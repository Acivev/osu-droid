package com.osudroid.verktex.bridge

import android.content.Context
import com.reco1l.verktex.Platform
import com.reco1l.verktex.android.AndroidProvider
import com.reco1l.verktex.audio.AudioDevice
import com.reco1l.verktex.audio.AudioDriver
import com.reco1l.verktex.worker.LoopWorker

/**
 * [Platform.Implementation] for Verktex hosted by the activity's render loop. Workers own no
 * threads, the host pumps them by name through [worker].
 */
class HostedPlatform(context: Context) : Platform.Implementation {

    override val provider = AndroidProvider(context)
    override val currentDisplay: Platform.Display
    override val usesVirtualKeyboard = true

    private val _workers = LinkedHashMap<String, ManualLoopWorker>()

    val workers: Map<String, ManualLoopWorker>
        get() = _workers


    init {
        val metrics = context.resources.displayMetrics

        currentDisplay = Platform.Display(
            density = metrics.density,
            width = metrics.widthPixels,
            height = metrics.heightPixels
        )
    }


    override fun createLoopWorker(name: String, onLoop: () -> Unit): LoopWorker {
        val worker = ManualLoopWorker(name, onLoop)
        _workers[name] = worker
        return worker
    }

    fun worker(name: String): ManualLoopWorker {
        return _workers[name] ?: throw IllegalStateException("No loop worker named $name has been created.")
    }
}

/**
 * An [AudioDriver] with no devices, audio is handled by the host while Verktex is hosted.
 */
class NoopAudioDriver : AudioDriver() {

    override fun onEnumerateDevices(): List<AudioDevice> = emptyList()
}
