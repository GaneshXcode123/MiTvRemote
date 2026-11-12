package com.mitvremote.app.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class TvDevice(val host: String, val name: String)

/** Finds TVs two ways: mDNS (instant, TV name) and a quick port-6466 scan of the local /24 subnet. */
class Discovery(
    private val ctx: Context,
    private val onFound: (TvDevice) -> Unit,
    private val onScanDone: () -> Unit,
) {
    private val nsd = ctx.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var lock: WifiManager.MulticastLock? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var scanJob: Job? = null

    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start(scope: CoroutineScope) {
        try {
            lock = wifi.createMulticastLock("mitvremote").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
        }
        startMdns()
        scanJob = scope.launch(Dispatchers.IO) {
            try {
                scanSubnet()
            } catch (_: Exception) {
            } finally {
                onScanDone()
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        listener?.let {
            try {
                nsd.stopServiceDiscovery(it)
            } catch (_: Exception) {
            }
        }
        listener = null
        try {
            lock?.release()
        } catch (_: Exception) {
        }
        lock = null
        synchronized(queue) { queue.clear() }
    }

    // ---- mDNS --------------------------------------------------------------------------

    private fun startMdns() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(queue) { queue.addLast(serviceInfo) }
                resolveNext()
            }
        }
        listener = l
        try {
            nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {
        }
    }

    private fun resolveNext() {
        var pending: NsdServiceInfo? = null
        synchronized(queue) {
            if (!resolving) {
                pending = queue.removeFirstOrNull()
                if (pending != null) resolving = true
            }
        }
        val next = pending ?: return
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    done()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val addr = serviceInfo.host
                    if (addr is Inet4Address) {
                        onFound(TvDevice(addr.hostAddress ?: "", serviceInfo.serviceName))
                    }
                    done()
                }

                private fun done() {
                    synchronized(queue) { resolving = false }
                    resolveNext()
                }
            })
        } catch (_: Exception) {
            synchronized(queue) { resolving = false }
        }
    }

    // ---- subnet scan -------------------------------------------------------------------

    private fun localPrefix(): String? {
        val candidates = ArrayList<Pair<String, String>>()
        val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
        for (nif in ifaces) {
            if (!nif.isUp || nif.isLoopback) continue
            for (a in nif.inetAddresses) {
                if (a is Inet4Address && a.isSiteLocalAddress) {
                    candidates.add(nif.name to (a.hostAddress ?: continue))
                }
            }
        }
        val pick = candidates.firstOrNull { it.first.startsWith("wlan") } ?: candidates.firstOrNull()
        return pick?.second?.substringBeforeLast('.')
    }

    private suspend fun scanSubnet() = coroutineScope {
        val prefix = localPrefix() ?: return@coroutineScope
        val gate = Semaphore(48)
        (1..254).map { i ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val ip = "$prefix.$i"
                    if (portOpen(ip, 6466)) onFound(TvDevice(ip, "Android TV ($ip)"))
                }
            }
        }.awaitAll()
    }

    private fun portOpen(ip: String, port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), 400) }
        true
    } catch (_: Exception) {
        false
    }
}
