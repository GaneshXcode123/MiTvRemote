package com.mitvremote.app.tv

import android.annotation.SuppressLint
import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.RSAPublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * The TV identifies a paired remote by its TLS client certificate, so we create one
 * self-signed certificate on first launch and keep using it.
 */
object Tls {
    private const val PASS = "mitvremote"
    private const val FILE = "client.p12"
    private const val ALIAS = "client"

    @Synchronized
    private fun keyStore(ctx: Context): KeyStore {
        val file = File(ctx.filesDir, FILE)
        if (file.exists()) {
            try {
                val existing = KeyStore.getInstance("PKCS12")
                file.inputStream().use { existing.load(it, PASS.toCharArray()) }
                if (existing.isKeyEntry(ALIAS)) return existing
            } catch (_: Exception) {
                // fall through and create a fresh identity
            }
        }
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val name = X500Name("CN=atvremote")
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(now),
            Date(now - 24L * 3600L * 1000L),
            Date(now + 20L * 365L * 24L * 3600L * 1000L),
            name,
            kp.public,
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, kp.private, PASS.toCharArray(), arrayOf(cert))
        file.outputStream().use { ks.store(it, PASS.toCharArray()) }
        return ks
    }

    fun clientPublicKey(ctx: Context): RSAPublicKey {
        val cert = keyStore(ctx).getCertificate(ALIAS) as X509Certificate
        return cert.publicKey as RSAPublicKey
    }

    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    fun connect(ctx: Context, host: String, port: Int, timeoutMs: Int = 6000): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(ctx), PASS.toCharArray())

        // TVs use their own self-signed certificate, so it can't be validated against a CA.
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val sc = SSLContext.getInstance("TLS")
        sc.init(kmf.keyManagers, arrayOf<TrustManager>(trustAll), null)

        val socket = sc.socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            socket.startHandshake()
        } catch (e: Exception) {
            try {
                socket.close()
            } catch (_: Exception) {
            }
            throw e
        }
        return socket
    }
}
