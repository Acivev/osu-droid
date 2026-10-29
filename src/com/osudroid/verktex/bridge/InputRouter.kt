package com.osudroid.verktex.bridge

/**
 * The order a key is offered in.
 */
object InputRouter {

    /**
     * The order a key is offered in: a shown router overlay first, then the rest of the dispatch.
     */
    fun routeKey(overlayShown: Boolean, overlay: () -> Boolean, rest: () -> Boolean): Boolean {
        return (overlayShown && overlay()) || rest()
    }

    /**
     * One key event's offer to Verktex. Every step of the key dispatch that asks Verktex asks through
     * it, so Verktex sees the event at most once. A repeated ask returns false: had the first one
     * been consumed, the dispatch would have ended there.
     */
    class KeyOffer(private val offer: () -> Boolean) {
        private var tried = false

        fun dispatch(): Boolean {
            if (tried) {
                return false
            }
            tried = true
            return offer()
        }
    }
}
