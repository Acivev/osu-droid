package org.andengine.engine.handler.runnable;

import java.util.ArrayList;

import org.andengine.engine.handler.IUpdateHandler;

/**
 * (c) 2010 Nicolas Gramlich 
 * (c) 2011 Zynga Inc.
 * 
 * @author Nicolas Gramlich
 * @since 10:24:39 - 18.06.2010
 */
public class RunnableHandler implements IUpdateHandler {
	// ===========================================================
	// Constants
	// ===========================================================

	// ===========================================================
	// Fields
	// ===========================================================

	private ArrayList<Runnable> mRunnables = new ArrayList<Runnable>();
	// BEGIN osu!droid modified: add a spare buffer to swap mRunnables with when onUpdate() is running.
	// See the method for explanation.
	private ArrayList<Runnable> mRunnablesSwapBuffer = new ArrayList<Runnable>();
	// END osu!droid modified

	// ===========================================================
	// Constructors
	// ===========================================================

	// ===========================================================
	// Getter & Setter
	// ===========================================================

	// ===========================================================
	// Methods for/from SuperClass/Interfaces
	// ===========================================================

	@Override
	public void onUpdate(final float pSecondsElapsed) {
		final ArrayList<Runnable> runnables;

		// BEGIN osu!droid modified: swap in the empty buffer so Runnables posted by run() below (directly, or
		// indirectly via postRunnable()) land in the new mRunnables instead of the list we're about to iterate
		// and clear (which will cause the posted Runnables, which has not been run yet, to be cleared).
		// The swap is the only part under the monitor: the batch runs outside it so postRunnable() never waits on a
		// running Runnable (which may itself wait on a lock held by the thread that is posting, causing a deadlock).
		// Only the update thread calls onUpdate(), so mRunnablesSwapBuffer needs no further protection.
		synchronized(this) {
			runnables = this.mRunnables;
			this.mRunnables = this.mRunnablesSwapBuffer;
			this.mRunnablesSwapBuffer = runnables;
		}
		// END osu!droid modified

		final int runnableCount = runnables.size();
		for(int i = 0; i < runnableCount; i++) {
			runnables.get(i).run();
		}
		runnables.subList(0, runnableCount).clear();
	}

	@Override
	public synchronized void reset() {
		this.mRunnables.clear();
	}

	// ===========================================================
	// Methods
	// ===========================================================

	public synchronized void postRunnable(final Runnable pRunnable) {
		this.mRunnables.add(pRunnable);
	}

	// ===========================================================
	// Inner and Anonymous Classes
	// ===========================================================
}
