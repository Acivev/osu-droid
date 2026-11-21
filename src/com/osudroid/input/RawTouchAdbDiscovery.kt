package com.osudroid.input

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import java.net.NetworkInterface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Finds the ports of the ADB daemon of this device, which announces them on the network and changes them whenever
 * wireless debugging or its pairing dialog is opened.
 *
 * @param serviceType The announced service to look for, [PAIRING] or [CONNECT].
 * @param onPortChanged Called on an arbitrary thread with the port of the service, or `-1` once it is gone.
 */
internal class RawTouchAdbDiscovery(
    context: Context,
    private val serviceType: String,
    private val onPortChanged: (Int) -> Unit
) {

    private val manager = context.getSystemService(NsdManager::class.java)

    private var isStarted = false

    @Volatile
    private var serviceName: String? = null

    private val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) = Unit

        override fun onDiscoveryStopped(serviceType: String) = Unit

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Unable to look for $serviceType: error $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

        override fun onServiceFound(service: NsdServiceInfo) {
            @Suppress("DEPRECATION")
            manager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
                    Log.w(TAG, "Unable to resolve ${service.serviceName}: error $errorCode")
                }

                override fun onServiceResolved(service: NsdServiceInfo) {
                    @Suppress("DEPRECATION")
                    val host = service.host

                    // Other devices on the network announce their daemons as well.
                    if (host != null && !host.isLoopbackAddress && NetworkInterface.getByInetAddress(host) == null) {
                        return
                    }

                    Log.i(TAG, "Found ${service.serviceName} on port ${service.port}")

                    serviceName = service.serviceName
                    onPortChanged(service.port)
                }
            })
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            if (service.serviceName == serviceName) {
                serviceName = null
                onPortChanged(-1)
            }
        }
    }


    fun start() {
        if (isStarted) {
            return
        }

        isStarted = true
        manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stop() {
        if (!isStarted) {
            return
        }

        isStarted = false

        try {
            manager.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
    }


    companion object {
        private const val TAG = "RawTouchAdbDiscovery"

        const val PAIRING = "_adb-tls-pairing._tcp"
        const val CONNECT = "_adb-tls-connect._tcp"

        /**
         * Waits for a service to be found.
         *
         * @return The port of the service, or `-1` if it was not found in time.
         */
        fun find(context: Context, serviceType: String, timeoutMillis: Long): Int {
            val latch = CountDownLatch(1)
            var result = -1

            val discovery = RawTouchAdbDiscovery(context, serviceType) { port ->
                if (port > 0) {
                    result = port
                    latch.countDown()
                }
            }

            discovery.start()

            try {
                latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            } finally {
                discovery.stop()
            }

            return result
        }
    }
}
