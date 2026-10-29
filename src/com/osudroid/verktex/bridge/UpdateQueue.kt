package com.osudroid.verktex.bridge

/**
 * A queue of [Runnable]s that any thread may post to and the update thread drains once per frame.
 *
 * Posting never waits on a running [Runnable]: [drain] holds the monitor only to swap the pending list with an empty
 * spare, then runs the batch outside it. A [Runnable] may wait on a lock held by a thread that is posting, so running
 * the batch under the monitor would deadlock.
 *
 * A [Runnable] posted while a batch runs lands in the new pending list and runs on the next [drain]; iterating and
 * clearing the live list would otherwise drop it unrun. Only the update thread calls [drain], so the spare list needs
 * no further protection.
 */
class UpdateQueue {

    private var pending = ArrayList<Runnable>()
    private var spare = ArrayList<Runnable>()

    @Synchronized
    fun post(runnable: Runnable) {
        pending.add(runnable)
    }

    fun drain() {
        val batch: ArrayList<Runnable>

        synchronized(this) {
            batch = pending
            pending = spare
            spare = batch
        }

        val count = batch.size
        for (i in 0 until count) {
            batch[i].run()
        }
        batch.subList(0, count).clear()
    }

    @Synchronized
    fun reset() {
        pending.clear()
    }
}
