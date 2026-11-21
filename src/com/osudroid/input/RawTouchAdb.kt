package com.osudroid.input

import android.content.Context
import android.os.Build
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Runs shell commands through the ADB daemon of this device, which is reachable over the loopback interface while
 * wireless debugging is turned on.
 *
 * The daemon only accepts clients that were paired with it once, see [pairWith].
 */
internal class RawTouchAdb(private val context: Context) : AbsAdbConnectionManager() {

    enum class Result {
        /**
         * The command was run.
         */
        DONE,

        /**
         * The daemon does not know this client.
         */
        NOT_PAIRED,

        /**
         * The daemon was not found, most likely because wireless debugging is turned off.
         */
        UNREACHABLE,

        /**
         * The command could not be run.
         */
        FAILED
    }

    private val key: PrivateKey
    private val keyCertificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(TIMEOUT, TimeUnit.MILLISECONDS)

        val directory = File(context.filesDir, "rawtouch").apply { mkdirs() }
        val keyFile = File(directory, "adb_key")
        val certificateFile = File(directory, "adb_certificate")

        var loadedKey: PrivateKey? = null
        var loadedCertificate: Certificate? = null

        try {
            if (keyFile.exists() && certificateFile.exists()) {
                loadedKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
                loadedCertificate = certificateFile.inputStream().use {
                    CertificateFactory.getInstance("X.509").generateCertificate(it)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to load the ADB key, creating a new one", e)
            loadedKey = null
        }

        if (loadedKey == null || loadedCertificate == null) {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048, SecureRandom())

            val pair = generator.generateKeyPair()
            val algorithm = "SHA512withRSA"
            val name = X500Name("CN=osu!droid")
            val notBefore = Date()
            val notAfter = Date(notBefore.time + TimeUnit.DAYS.toMillis(365L * 30))

            val extensions = CertificateExtensions()
            extensions.set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))

            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(pair.public))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", extensions)

            val certificate = X509CertImpl(info)
            certificate.sign(pair.private, algorithm)

            keyFile.writeBytes(pair.private.encoded)
            certificateFile.writeBytes(certificate.encoded)

            loadedKey = pair.private
            loadedCertificate = certificate
        }

        key = loadedKey
        keyCertificate = loadedCertificate
    }

    override fun getPrivateKey() = key

    override fun getCertificate() = keyCertificate

    override fun getDeviceName() = "osu!droid"


    /**
     * Pairs with the daemon while the pairing dialog of the wireless debugging settings is open.
     *
     * @param port The port of the pairing service, which changes whenever the dialog is opened.
     * @param code The code shown in the dialog.
     */
    fun pairWith(port: Int, code: String) = try {
        pair(LOOPBACK, port, code)
    } catch (e: Exception) {
        Log.w(TAG, "Pairing failed", e)
        false
    }

    /**
     * Runs a shell command and waits for it to finish, though not longer than a few seconds.
     */
    fun run(command: String): Result {
        try {
            disconnect()
        } catch (_: Exception) {
        }

        try {
            val port = RawTouchAdbDiscovery.find(context, RawTouchAdbDiscovery.CONNECT, DISCOVERY_TIMEOUT)

            if (port <= 0 || !connect(LOOPBACK, port)) {
                return Result.UNREACHABLE
            }
        } catch (_: AdbPairingRequiredException) {
            return Result.NOT_PAIRED
        } catch (e: Exception) {
            Log.i(TAG, "The ADB daemon is not reachable: $e")
            return Result.UNREACHABLE
        }

        return try {
            val stream = openStream("shell:$command")

            // Reading blocks for as long as the command keeps its output open.
            val reader = Thread({
                try {
                    val bytes = ByteArray(1024)

                    while (stream.read(bytes, 0, bytes.size) >= 0) {
                        // The output is of no interest.
                    }
                } catch (_: Exception) {
                }
            }, "raw-touch-adb")

            reader.isDaemon = true
            reader.start()
            reader.join(TIMEOUT)

            try {
                stream.close()
            } catch (_: Exception) {
            }

            Result.DONE
        } catch (e: Exception) {
            Log.e(TAG, "Unable to run a shell command", e)
            Result.FAILED
        } finally {
            try {
                disconnect()
            } catch (_: Exception) {
            }
        }
    }


    companion object {
        private const val TAG = "RawTouchAdb"
        private const val LOOPBACK = "127.0.0.1"
        private const val TIMEOUT = 5000L
        private const val DISCOVERY_TIMEOUT = 3000L
    }
}
