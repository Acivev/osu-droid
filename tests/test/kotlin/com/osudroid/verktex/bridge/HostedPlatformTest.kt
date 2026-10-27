package com.osudroid.verktex.bridge

import androidx.test.platform.app.InstrumentationRegistry
import com.osudroid.verktex.VerktexTestFixtures
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HostedPlatformTest {

    @Test
    fun createLoopWorkerRegistersByName() {
        val p = HostedPlatform(InstrumentationRegistry.getInstrumentation().targetContext)
        val w = p.createLoopWorker("draw_worker") {}
        assertTrue(w is ManualLoopWorker)
        assertSame(w, p.worker("draw_worker"))
    }

    @Test
    fun noopAudioDriverEnumeratesNothingWithoutThrowing() {
        VerktexTestFixtures.ensureInitialized() // Logger must be set
        val d = NoopAudioDriver()
        assertTrue(d.enumerateDevices().isEmpty())
        assertNull(d.activeDevice)
    }
}
