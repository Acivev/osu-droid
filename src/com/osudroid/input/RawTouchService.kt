package com.osudroid.input

import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.Keep
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.system.exitProcess

/**
 * Reads the raw input events of the touchscreen and forwards finger contacts that start while a stylus is in range.
 *
 * Android discards such contacts before they reach any application, so they can only be obtained from the input
 * devices directly, which requires shell privileges. This class therefore runs in a separate process that is started
 * from an ADB shell (see [RawTouchAdb]), and talks to the application through a loopback socket (see [RawTouchInput]).
 * The process keeps running after the ADB connection is gone, until the device restarts or debugging is turned off
 * altogether. It only reads the input devices while the application is connected.
 *
 * A contact that starts while the stylus hovers is reported at the position of the stylus and follows it, so that
 * the stylus aims and fingers press. Where the finger is gets reported separately. A contact that starts while the stylus touches the screen is reported where the
 * finger is. Contacts that start without a stylus in range are not forwarded, as they reach the application the
 * usual way.
 *
 * The state of the stylus itself is forwarded as well, in records with the slot [STYLUS_SLOT].
 */
@Keep
class RawTouchService private constructor(private val token: String) {

    @Volatile
    private var generation = 0

    // Guards everything below, which both readers access, as well as writing to the client.
    private val lock = Any()

    private var isStylusInRange = false
    private var isStylusTouching = false
    private var stylusX = 0f
    private var stylusY = 0f

    // Per slot, whether the contact is forwarded and whether it is reported at the position of the stylus.
    private val forwarded = BooleanArray(SLOT_COUNT)
    private val following = BooleanArray(SLOT_COUNT)


    private fun serve(port: Int) {
        val server = ServerSocket(port, 2, InetAddress.getLoopbackAddress())

        while (true) {
            val socket = server.accept()

            try {
                handle(socket)
            } catch (e: Exception) {
                Log.w(TAG, "Dropped a client", e)

                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = HANDSHAKE_TIMEOUT

        val input = DataInputStream(socket.getInputStream())
        val protocol = input.readInt()
        val clientToken = ByteArray(TOKEN_LENGTH).also { input.readFully(it) }.toString(Charsets.US_ASCII)

        // Other applications on the device can reach the socket as well, but do not know the token.
        if (clientToken != token) {
            socket.close()
            return
        }

        if (protocol != PROTOCOL_VERSION) {
            socket.close()
            exitProcess(0)
        }

        val error = start(socket.getOutputStream())?.toByteArray()
        val output = DataOutputStream(socket.getOutputStream())

        output.writeInt(error?.size ?: 0)

        if (error != null) {
            output.write(error)
            output.flush()
            socket.close()
            return
        }

        output.flush()
        socket.soTimeout = 0

        val currentGeneration = generation

        // The client never sends anything after the handshake, so this returns once it is gone.
        Thread({
            try {
                socket.getInputStream().read()
            } catch (_: Exception) {
            }

            if (generation == currentGeneration) {
                generation++
            }

            try {
                socket.close()
            } catch (_: Exception) {
            }
        }, "raw-client").start()
    }

    /**
     * @return A description of what went wrong, or `null` if the readers were started.
     */
    private fun start(output: OutputStream): String? {
        val devices = try {
            findDevices()
        } catch (e: Exception) {
            return "Unable to list input devices: $e"
        }

        val touchscreen = devices.firstOrNull { it.isTouchscreen && "touchscreen" in it.name }
            ?: devices.firstOrNull { it.isTouchscreen }
            ?: return "No touchscreen found"

        val stylus = devices.firstOrNull { it.isStylus } ?: return "No stylus digitizer found"

        // Readers of a previous start stop once they notice the new generation.
        val currentGeneration = ++generation

        synchronized(lock) {
            isStylusInRange = false
            isStylusTouching = false
            forwarded.fill(false)
            following.fill(false)
        }

        Thread({ readStylus(stylus, output, currentGeneration) }, "raw-stylus").start()
        Thread({ readTouchscreen(touchscreen, output, currentGeneration) }, "raw-touch").start()

        return null
    }

    private fun readStylus(device: InputDevice, output: OutputStream, currentGeneration: Int) {
        val maxX = device.maxX.coerceAtLeast(1).toFloat()
        val maxY = device.maxY.coerceAtLeast(1).toFloat()

        val outBytes = ByteArray(RECORD_SIZE * (SLOT_COUNT + 1))
        val out = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        var isInRange = false
        var isTouching = false
        var x = 0
        var y = 0

        try {
            FileInputStream(device.path).use { input ->
                val bytes = ByteArray(EVENT_SIZE * 64)
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                while (generation == currentGeneration) {
                    val read = input.read(bytes)

                    if (read <= 0) {
                        break
                    }

                    var offset = 0

                    while (offset + EVENT_SIZE <= read) {
                        val type = buffer.getShort(offset + 16).toInt() and 0xFFFF
                        val code = buffer.getShort(offset + 18).toInt() and 0xFFFF
                        val value = buffer.getInt(offset + 20)

                        if (type == EV_KEY) {
                            when (code) {
                                BTN_TOOL_PEN, BTN_TOOL_RUBBER -> isInRange = value != 0
                                BTN_TOUCH -> isTouching = value != 0
                            }
                        } else if (type == EV_ABS) {
                            when (code) {
                                ABS_X -> x = value
                                ABS_Y -> y = value
                            }
                        } else if (type == EV_SYN && code == SYN_REPORT) {
                            val eventTime = toUptimeMillis(buffer.getLong(offset), buffer.getLong(offset + 8))
                            val normalizedX = x / maxX
                            val normalizedY = y / maxY

                            synchronized(lock) {
                                isStylusInRange = isInRange
                                isStylusTouching = isInRange && isTouching
                                stylusX = normalizedX
                                stylusY = normalizedY

                                out.clear()

                                putRecord(
                                    out,
                                    STYLUS_SLOT,
                                    when {
                                        !isInRange -> STYLUS_OUT_OF_RANGE
                                        isTouching -> STYLUS_TOUCHING
                                        else -> STYLUS_HOVERING
                                    },
                                    normalizedX,
                                    normalizedY,
                                    eventTime
                                )

                                if (isInRange) {
                                    for (i in 0 until SLOT_COUNT) {
                                        if (forwarded[i] && following[i]) {
                                            putRecord(out, i, ACTION_MOVE, normalizedX, normalizedY, eventTime)
                                        }
                                    }
                                }

                                output.write(outBytes, 0, out.position())
                            }
                        }

                        offset += EVENT_SIZE
                    }
                }
            }
        } catch (_: Exception) {
            // Without the stylus state no contact is forwarded, which is the safe outcome.
        }

        if (generation == currentGeneration) {
            synchronized(lock) {
                isStylusInRange = false
                isStylusTouching = false
            }
        }
    }

    private fun readTouchscreen(device: InputDevice, output: OutputStream, currentGeneration: Int) {
        val trackingId = IntArray(SLOT_COUNT) { -1 }
        val x = IntArray(SLOT_COUNT)
        val y = IntArray(SLOT_COUNT)
        val trackingChanged = BooleanArray(SLOT_COUNT)
        val positionChanged = BooleanArray(SLOT_COUNT)
        var slot = 0

        val maxX = device.maxX.coerceAtLeast(1).toFloat()
        val maxY = device.maxY.coerceAtLeast(1).toFloat()

        val outBytes = ByteArray(RECORD_SIZE * SLOT_COUNT * 4)
        val out = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        try {
            output.use {
                FileInputStream(device.path).use { input ->
                    val bytes = ByteArray(EVENT_SIZE * 64)
                    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

                    while (generation == currentGeneration) {
                        val read = input.read(bytes)

                        if (read <= 0) {
                            break
                        }

                        var offset = 0

                        while (offset + EVENT_SIZE <= read) {
                            val type = buffer.getShort(offset + 16).toInt() and 0xFFFF
                            val code = buffer.getShort(offset + 18).toInt() and 0xFFFF
                            val value = buffer.getInt(offset + 20)

                            if (type == EV_ABS) {
                                when (code) {
                                    ABS_MT_SLOT -> slot = value.coerceIn(0, SLOT_COUNT - 1)

                                    ABS_MT_POSITION_X -> {
                                        x[slot] = value
                                        positionChanged[slot] = true
                                    }

                                    ABS_MT_POSITION_Y -> {
                                        y[slot] = value
                                        positionChanged[slot] = true
                                    }

                                    ABS_MT_TRACKING_ID -> {
                                        trackingId[slot] = value
                                        trackingChanged[slot] = true
                                    }
                                }
                            } else if (type == EV_SYN && code == SYN_REPORT) {
                                val eventTime = toUptimeMillis(buffer.getLong(offset), buffer.getLong(offset + 8))

                                synchronized(lock) {
                                    out.clear()

                                    for (i in 0 until SLOT_COUNT) {
                                        var action = -1

                                        if (trackingChanged[i]) {
                                            if (trackingId[i] >= 0) {
                                                // A contact replacing another one in the same report ends the old one.
                                                if (forwarded[i]) {
                                                    putRecord(out, i, ACTION_UP, 0f, 0f, eventTime)

                                                    if (following[i]) {
                                                        putRecord(out, i + FINGER_SLOT_OFFSET, ACTION_UP, 0f, 0f, eventTime)
                                                    }
                                                }

                                                forwarded[i] = isStylusInRange
                                                following[i] = isStylusInRange && !isStylusTouching
                                                action = ACTION_DOWN
                                            } else {
                                                action = ACTION_UP
                                            }
                                        } else if (positionChanged[i] && trackingId[i] >= 0) {
                                            action = ACTION_MOVE
                                        }

                                        trackingChanged[i] = false
                                        positionChanged[i] = false

                                        if (action == -1 || !forwarded[i]) {
                                            continue
                                        }

                                        if (following[i]) {
                                            // The stylus reader moves the contact along with the stylus.
                                            if (action != ACTION_MOVE) {
                                                putRecord(out, i, action, stylusX, stylusY, eventTime)
                                            }

                                            putRecord(
                                                out,
                                                i + FINGER_SLOT_OFFSET,
                                                action,
                                                x[i] / maxX,
                                                y[i] / maxY,
                                                eventTime
                                            )
                                        } else {
                                            putRecord(out, i, action, x[i] / maxX, y[i] / maxY, eventTime)
                                        }

                                        if (action == ACTION_UP) {
                                            forwarded[i] = false
                                            following[i] = false
                                        }
                                    }

                                    if (out.position() > 0) {
                                        output.write(outBytes, 0, out.position())
                                    }
                                }
                            }

                            offset += EVENT_SIZE
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // The application closed its end of the socket or the device went away.
        }
    }

    private fun putRecord(out: ByteBuffer, slot: Int, action: Int, x: Float, y: Float, eventTime: Long) {
        if (out.remaining() < RECORD_SIZE) {
            return
        }

        out.putInt(slot)
        out.putInt(action)
        out.putFloat(x)
        out.putFloat(y)
        out.putLong(eventTime)
    }

    /**
     * Converts the time of a raw input event, which is in wall clock time, to the [SystemClock.uptimeMillis] time base.
     */
    private fun toUptimeMillis(seconds: Long, microseconds: Long): Long {
        val uptime = SystemClock.uptimeMillis()
        val age = System.currentTimeMillis() - (seconds * 1000 + microseconds / 1000)

        // Guards against input devices that report in another clock.
        return if (age in 0..1000) uptime - age else uptime
    }


    private class InputDevice(val path: String) {
        var name = ""
        val keys = HashSet<Int>()
        val absoluteAxes = HashSet<Int>()

        // Of the multi-touch axes for a touchscreen, and of the single-touch axes for a stylus.
        var maxX = 0
        var maxY = 0

        val isStylus
            get() = BTN_TOOL_PEN in keys

        val isTouchscreen
            get() = !isStylus && ABS_MT_SLOT in absoluteAxes && ABS_MT_POSITION_X in absoluteAxes
                && ABS_MT_POSITION_Y in absoluteAxes && maxX > 0 && maxY > 0
    }

    private fun findDevices(): List<InputDevice> {
        val devices = ArrayList<InputDevice>()
        var device: InputDevice? = null
        var section = ""

        // The multi-touch axes of a device take precedence over its single-touch ones.
        var hasMultiTouchX = false
        var hasMultiTouchY = false

        val process = Runtime.getRuntime().exec(arrayOf("getevent", "-p"))

        process.inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.startsWith("add device")) {
                    device = InputDevice(line.substringAfter(": ").trim()).also { devices.add(it) }
                    section = ""
                    hasMultiTouchX = false
                    hasMultiTouchY = false
                    continue
                }

                val current = device ?: continue
                val trimmed = line.trim()

                if (trimmed.isEmpty()) {
                    continue
                }

                if (trimmed.startsWith("name:")) {
                    current.name = trimmed.substringAfter("name:").trim().trim('"')
                    continue
                }

                var content = trimmed
                val header = SECTION_HEADER.find(trimmed)

                if (header != null) {
                    section = header.groupValues[1]
                    content = trimmed.substring(header.range.last + 1)
                } else if (!trimmed.first().isHexDigit()) {
                    section = ""
                    continue
                }

                when (section) {
                    "KEY" -> HEX_CODE.findAll(content).forEach { current.keys.add(it.value.toInt(16)) }

                    "ABS" -> {
                        val axis = ABSOLUTE_AXIS.find(content) ?: continue
                        val code = axis.groupValues[1].toInt(16)
                        val max = axis.groupValues[2].toInt()

                        current.absoluteAxes.add(code)

                        when (code) {
                            ABS_X -> if (!hasMultiTouchX) current.maxX = max
                            ABS_Y -> if (!hasMultiTouchY) current.maxY = max

                            ABS_MT_POSITION_X -> {
                                current.maxX = max
                                hasMultiTouchX = true
                            }

                            ABS_MT_POSITION_Y -> {
                                current.maxY = max
                                hasMultiTouchY = true
                            }
                        }
                    }
                }
            }
        }

        return devices
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f'


    companion object {
        private const val TAG = "RawTouchService"

        /**
         * Changes whenever the handshake or the records change. A service of another version exits when it is
         * contacted, so that the application can start the current one.
         */
        const val PROTOCOL_VERSION = 4

        const val TOKEN_LENGTH = 32

        private const val HANDSHAKE_TIMEOUT = 2000

        /**
         * Entry point when started with `app_process`.
         *
         * @param args The process name, the loopback port to listen on, and the token that clients have to present.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                val processName = args[0]
                val port = args[1].toInt()
                val token = args[2]

                require(token.length == TOKEN_LENGTH)

                stopOtherInstances(processName)

                RawTouchService(token).serve(port)
            } catch (e: Throwable) {
                Log.e(TAG, "The raw touch service stopped", e)
                exitProcess(1)
            }
        }

        private fun stopOtherInstances(processName: String) {
            val self = Process.myPid()

            for (directory in File("/proc").listFiles() ?: return) {
                val pid = directory.name.toIntOrNull() ?: continue

                if (pid == self) {
                    continue
                }

                val name = try {
                    File(directory, "cmdline").readText().substringBefore(NUL)
                } catch (_: Exception) {
                    continue
                }

                if (name == processName) {
                    Process.killProcess(pid)
                }
            }

            // Gives the port of a stopped instance time to become free.
            Thread.sleep(200)
        }

        /**
         * The size of a forwarded record: slot (int), action (int), X and Y in the 0-1 range of the touchscreen in its
         * natural orientation (floats), and the event time in the [SystemClock.uptimeMillis] time base (long).
         */
        const val RECORD_SIZE = 24

        const val ACTION_DOWN = 0
        const val ACTION_MOVE = 1
        const val ACTION_UP = 2

        /**
         * The slot of records that describe the stylus instead of a finger contact. Their action is one of
         * [STYLUS_OUT_OF_RANGE], [STYLUS_HOVERING] and [STYLUS_TOUCHING].
         */
        const val STYLUS_SLOT = -1

        /**
         * Added to the slot of records that describe where the finger of a contact is, when the contact itself is
         * reported at the position of the stylus.
         */
        const val FINGER_SLOT_OFFSET = 0x100

        const val STYLUS_OUT_OF_RANGE = 0
        const val STYLUS_HOVERING = 1
        const val STYLUS_TOUCHING = 2

        private const val SLOT_COUNT = 16

        // Separates the arguments in the command line of a process.
        private val NUL = 0.toChar()

        // struct input_event on 64-bit: timeval (two longs), type and code (shorts), value (int).
        private const val EVENT_SIZE = 24

        private const val EV_SYN = 0
        private const val EV_KEY = 1
        private const val EV_ABS = 3
        private const val SYN_REPORT = 0
        private const val BTN_TOOL_PEN = 0x140
        private const val BTN_TOOL_RUBBER = 0x141
        private const val BTN_TOUCH = 0x14a
        private const val ABS_X = 0x00
        private const val ABS_Y = 0x01
        private const val ABS_MT_SLOT = 0x2f
        private const val ABS_MT_POSITION_X = 0x35
        private const val ABS_MT_POSITION_Y = 0x36
        private const val ABS_MT_TRACKING_ID = 0x39

        private val SECTION_HEADER = Regex("""^([A-Z]+) \([0-9a-f]{4}\):""")
        private val HEX_CODE = Regex("""\b[0-9a-f]{4}\b""")
        private val ABSOLUTE_AXIS = Regex("""^\s*([0-9a-f]{4})\s*: value -?\d+, min -?\d+, max (-?\d+)""")
    }
}
