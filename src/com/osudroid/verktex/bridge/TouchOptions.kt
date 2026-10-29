package com.osudroid.verktex.bridge

/**
 * The two touch flags [TouchController.applyTouchOptions] copies.
 */
class TouchOptions {

    /**
     * Whether the historical (batched) positions inside a MOVE are each fired as their own event.
     */
    var processHistoricalEvents = false

    /**
     * Whether the raw pointer table is written on the UI thread for low-latency reads.
     */
    var useRawPointer = false
}
