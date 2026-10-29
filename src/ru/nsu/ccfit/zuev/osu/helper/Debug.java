package ru.nsu.ccfit.zuev.osu.helper;

import android.util.Log;

/**
 * App-side twin of AndEngine's {@code Debug} logger, logging under the {@code "AndEngine"} tag.
 */
public final class Debug {
    private static final String TAG = "AndEngine";

    private Debug() {}

    public static void d(final String message) {
        d(TAG, message, null);
    }

    public static void d(final String message, final Throwable throwable) {
        d(TAG, message, throwable);
    }

    public static void d(final String tag, final String message) {
        d(tag, message, null);
    }

    public static void d(final String tag, final String message, final Throwable throwable) {
        if (throwable == null) {
            Log.d(tag, message);
        } else {
            Log.d(tag, message, throwable);
        }
    }

    public static void i(final String message) {
        i(TAG, message, null);
    }

    public static void i(final String message, final Throwable throwable) {
        i(TAG, message, throwable);
    }

    public static void i(final String tag, final String message) {
        i(tag, message, null);
    }

    public static void i(final String tag, final String message, final Throwable throwable) {
        if (throwable == null) {
            Log.i(tag, message);
        } else {
            Log.i(tag, message, throwable);
        }
    }

    public static void w(final String message) {
        w(TAG, message, null);
    }

    public static void w(final String message, final Throwable throwable) {
        w(TAG, message, throwable);
    }

    public static void w(final String tag, final String message) {
        w(tag, message, null);
    }

    public static void w(final String tag, final String message, final Throwable throwable) {
        if (throwable == null) {
            Log.w(tag, message);
        } else {
            Log.w(tag, message, throwable);
        }
    }

    public static void e(final String message) {
        e(TAG, message, null);
    }

    public static void e(final String message, final Throwable throwable) {
        e(TAG, message, throwable);
    }

    public static void e(final String tag, final String message) {
        e(tag, message, null);
    }

    public static void e(final String tag, final String message, final Throwable throwable) {
        if (throwable == null) {
            Log.e(tag, message);
        } else {
            Log.e(tag, message, throwable);
        }
    }

    public static void w(final Throwable throwable) {
        w("", throwable);
    }

    public static void e(final Throwable throwable) {
        e(TAG, throwable);
    }
}
