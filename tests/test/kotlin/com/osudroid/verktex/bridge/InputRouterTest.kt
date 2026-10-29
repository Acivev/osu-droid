package com.osudroid.verktex.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputRouterTest {

    @Test
    fun routeKeyPrefersOverlayWhenShown() {
        var restCalled = false
        assertTrue(InputRouter.routeKey(true, { true }, { restCalled = true; false }))
        assertFalse(restCalled)
    }

    @Test
    fun routeKeySkipsOverlayWhenNotShown() {
        var overlayCalled = false
        assertTrue(InputRouter.routeKey(false, { overlayCalled = true; true }, { true }))
        assertFalse(overlayCalled)
    }

    @Test
    fun routeKeyDispatchesOnceWhenOverlayShown() {
        var dispatched = 0
        val offer = InputRouter.KeyOffer { dispatched++; false }
        assertFalse(InputRouter.routeKey(true, offer::dispatch, { false }))
        // A later step of the key dispatch asks again.
        assertFalse(offer.dispatch())
        assertEquals(1, dispatched)
    }

    @Test
    fun keyOfferDispatchesWhenNotTriedBefore() {
        var dispatched = 0
        val offer = InputRouter.KeyOffer { dispatched++; true }
        assertFalse(InputRouter.routeKey(false, offer::dispatch, { false }))
        assertTrue(offer.dispatch())
        assertEquals(1, dispatched)
    }
}
