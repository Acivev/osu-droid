package com.osudroid.verktex.bridge

/**
 * Thrown when the device's EGL driver offers no config or cannot create a GL context satisfying the minimum OpenGL ES
 * version (see [GameSurfaceView]). Carries enough device diagnostics for a user facing dialog and bug reports, since
 * the generic stack trace is meaningless to players hitting a hardware limitation.
 */
class GLESVersionUnsupportedException @JvmOverloads constructor(
    required: String,
    detected: String,
    val device: String,
    hardware: String,
    android: String,
    cause: Throwable? = null,
) : RuntimeException("Device does not support $required (detected: $detected, device: $device)", cause) {

    val requiredVersion = required

    val detectedVersion = detected

    val chipset = hardware

    val androidVersion = android

    private companion object {
        private const val serialVersionUID = 1L
    }
}
