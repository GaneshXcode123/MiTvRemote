package com.mitvremote.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mitvremote.app.tv.BadPairingCode
import com.mitvremote.app.tv.Discovery
import com.mitvremote.app.tv.PairingClient
import com.mitvremote.app.tv.RemoteClient
import com.mitvremote.app.tv.TvDevice
import com.mitvremote.app.tv.VoiceStreamer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { CONNECT, PAIR, REMOTE }
enum class Link { IDLE, CONNECTING, CONNECTED, LOST }
enum class PairStage { STARTING, ENTER_CODE, VERIFYING, FAILED }

data class UiState(
    val screen: Screen = Screen.CONNECT,
    val saved: List<TvDevice> = emptyList(),
    val devices: List<TvDevice> = emptyList(),
    val scanning: Boolean = false,
    val message: String? = null,
    val pairTv: TvDevice? = null,
    val pairStage: PairStage = PairStage.STARTING,
    val link: Link = Link.IDLE,
    val tvName: String = "",
    val tvHost: String = "",
    val tvOn: Boolean = true,
    val volume: String? = null,
    val voiceActive: Boolean = false,
    val volumeKeys: Boolean = true,
)

class RemoteViewModel(private val app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _ui = MutableStateFlow(
        UiState(saved = store.paired(), volumeKeys = store.volumeKeys),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var client: RemoteClient? = null
    private var pairing: PairingClient? = null
    private var pairJob: Job? = null
    private var discovery: Discovery? = null
    private var voice: VoiceStreamer? = null
    private var currentTv: TvDevice? = null
    private var everConnected = false

    init {
        val last = store.lastHost()
        store.paired().firstOrNull { it.host == last }?.let { connect(it) }
    }

    // ---- discovery ---------------------------------------------------------------------

    fun startDiscovery() {
        if (discovery != null) return
        _ui.update { it.copy(devices = emptyList(), scanning = true, saved = store.paired()) }
        val d = Discovery(
            app,
            onFound = { tv ->
                _ui.update { s ->
                    val existing = s.devices.firstOrNull { it.host == tv.host }
                    val list = when {
                        existing == null -> s.devices + tv
                        existing.name.startsWith("Android TV (") && !tv.name.startsWith("Android TV (") ->
                            s.devices.map { if (it.host == tv.host) tv else it }
                        else -> s.devices
                    }
                    s.copy(devices = list)
                }
            },
            onScanDone = { _ui.update { it.copy(scanning = false) } },
        )
        discovery = d
        d.start(viewModelScope)
    }

    fun stopDiscovery() {
        discovery?.stop()
        discovery = null
        _ui.update { it.copy(scanning = false) }
    }

    fun rescan() {
        stopDiscovery()
        startDiscovery()
    }

    // ---- choosing / pairing ------------------------------------------------------------

    fun pick(tv: TvDevice) {
        if (store.isPaired(tv.host)) connect(tv) else startPairing(tv)
    }

    fun connectManual(ip: String) {
        val host = ip.trim()
        if (host.isEmpty()) return
        val known = store.paired().firstOrNull { it.host == host }
        pick(known ?: TvDevice(host, "Mi TV ($host)"))
    }

    fun startPairing(tv: TvDevice) {
        stopDiscovery()
        client?.close()
        client = null
        pairing?.close()
        pairJob?.cancel()
        _ui.update {
            it.copy(screen = Screen.PAIR, pairTv = tv, pairStage = PairStage.STARTING, message = null, link = Link.IDLE)
        }
        val p = PairingClient(app, tv.host)
        pairing = p
        pairJob = viewModelScope.launch {
            try {
                p.start()
                _ui.update { it.copy(pairStage = PairStage.ENTER_CODE) }
            } catch (e: Exception) {
                p.close()
                _ui.update {
                    it.copy(
                        pairStage = PairStage.FAILED,
                        message = "Couldn't start pairing: ${e.message ?: e.javaClass.simpleName}. " +
                            "Make sure the TV is on and on the same Wi-Fi.",
                    )
                }
            }
        }
    }

    fun submitCode(code: String) {
        val p = pairing ?: return
        val tv = _ui.value.pairTv ?: return
        _ui.update { it.copy(pairStage = PairStage.VERIFYING, message = null) }
        pairJob = viewModelScope.launch {
            try {
                p.finish(code)
                p.close()
                pairing = null
                store.addPaired(tv)
                connect(tv)
            } catch (e: BadPairingCode) {
                _ui.update { it.copy(pairStage = PairStage.ENTER_CODE, message = e.message) }
            } catch (e: Exception) {
                p.close()
                _ui.update {
                    it.copy(
                        pairStage = PairStage.FAILED,
                        message = "Pairing failed: ${e.message ?: e.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun cancelPairing() {
        pairJob?.cancel()
        pairing?.close()
        pairing = null
        _ui.update { it.copy(screen = Screen.CONNECT, message = null, saved = store.paired()) }
    }

    fun forgetAndRepair(tv: TvDevice) {
        store.forget(tv.host)
        _ui.update { it.copy(saved = store.paired()) }
        startPairing(tv)
    }

    // ---- connection --------------------------------------------------------------------

    fun connect(tv: TvDevice) {
        stopDiscovery()
        stopVoice()
        client?.close()
        currentTv = tv
        everConnected = false
        store.setLast(tv.host)
        _ui.update {
            it.copy(
                screen = Screen.REMOTE, link = Link.CONNECTING, tvName = tv.name, tvHost = tv.host, message = null,
                saved = store.paired(), volume = null,
            )
        }
        val c = RemoteClient(app, tv.host, object : RemoteClient.Listener {
            override fun onReady(c: RemoteClient) {
                if (c !== client) return
                everConnected = true
                _ui.update { it.copy(link = Link.CONNECTED, message = null) }
            }

            override fun onClosed(c: RemoteClient, reason: String) {
                if (c !== client) return
                if (everConnected) {
                    _ui.update { it.copy(link = Link.LOST, message = reason, voiceActive = false) }
                } else {
                    _ui.update {
                        it.copy(
                            screen = Screen.CONNECT, link = Link.IDLE, saved = store.paired(),
                            message = "Couldn't connect to ${tv.name}: $reason. " +
                                "If the TV is on and nearby, tap Re-pair next to it.",
                        )
                    }
                }
            }

            override fun onPower(c: RemoteClient, on: Boolean) {
                if (c !== client) return
                _ui.update { it.copy(tvOn = on) }
            }

            override fun onVolume(c: RemoteClient, level: Int, max: Int, muted: Boolean) {
                if (c !== client) return
                _ui.update { it.copy(volume = if (muted) "Muted" else "Vol $level/$max") }
            }
        })
        client = c
        c.connect()
    }

    fun reconnect() {
        currentTv?.let { connect(it) }
    }

    fun switchTv() {
        stopVoice()
        client?.close()
        client = null
        _ui.update { it.copy(screen = Screen.CONNECT, link = Link.IDLE, message = null, saved = store.paired()) }
    }

    fun onForeground() {
        val s = _ui.value
        if (s.screen == Screen.REMOTE && s.link != Link.CONNECTED && s.link != Link.CONNECTING) reconnect()
    }

    fun onBackground() {
        stopVoice()
        if (_ui.value.screen == Screen.REMOTE) {
            // Free the connection while the app is hidden; it reconnects when you come back.
            client?.close()
            client = null
            _ui.update { it.copy(link = Link.LOST, message = null) }
        }
    }

    // ---- sending -----------------------------------------------------------------------

    fun key(code: Int) {
        client?.key(code)
    }

    fun typeText(text: String) {
        client?.text(text)
    }

    fun launch(appIdOrLink: String) {
        val id = appIdOrLink.trim()
        if (id.isNotEmpty()) client?.launch(id)
    }

    fun startVoice() {
        val c = client ?: return
        if (_ui.value.link != Link.CONNECTED || voice != null) return
        val v = VoiceStreamer(c)
        voice = v
        _ui.update { it.copy(voiceActive = true) }
        v.start(viewModelScope) { err ->
            voice = null
            _ui.update { it.copy(voiceActive = false, message = err ?: it.message) }
        }
    }

    fun stopVoice() {
        voice?.stop()
    }

    // ---- settings ----------------------------------------------------------------------

    fun setVolumeKeys(on: Boolean) {
        store.volumeKeys = on
        _ui.update { it.copy(volumeKeys = on) }
    }

    fun captureVolumeKeys(): Boolean {
        val s = _ui.value
        return s.screen == Screen.REMOTE && s.volumeKeys && s.link == Link.CONNECTED
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    override fun onCleared() {
        stopDiscovery()
        voice?.stop()
        client?.close()
        pairing?.close()
    }
}
