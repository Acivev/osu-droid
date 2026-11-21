package com.osudroid.input

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import ru.nsu.ccfit.zuev.osu.Config
import ru.nsu.ccfit.zuev.osu.GlobalManager

/**
 * Provides finger contacts that Android withholds from applications while a stylus is in range, by reading the
 * touchscreen directly through a [RawTouchService].
 *
 * The service outlives the application and is reused for as long as it runs. When it is not running, it is started
 * through the ADB daemon of this device (see [RawTouchAdb]), which requires wireless debugging to be turned on and
 * this application to be paired with the daemon (see [RawTouchPairing]).
 *
 * During gameplay ([isActive]), contacts are queued by a reader thread and handed out with [drain]. Otherwise they
 * are passed to the user interface as touch events.
 */
object RawTouchInput {

    enum class Status {
        /**
         * The service is running and connected.
         */
        READY,

        /**
         * This application has to be paired with the ADB daemon first.
         */
        PAIRING_REQUIRED,

        /**
         * The ADB daemon is not reachable, most likely because wireless debugging is turned off.
         */
        DEBUGGING_REQUIRED,

        /**
         * The service could not be started.
         */
        FAILED
    }

    /**
     * Receives a contact in the coordinate space of the render surface.
     */
    fun interface Sink {
        fun onRawTouch(slot: Int, action: Int, surfaceX: Float, surfaceY: Float, eventTime: Long)
    }

    const val ACTION_DOWN = RawTouchService.ACTION_DOWN
    const val ACTION_MOVE = RawTouchService.ACTION_MOVE
    const val ACTION_UP = RawTouchService.ACTION_UP

    /**
     * Contacts that start while the stylus hovers are placed at the stylus. The finger itself is handed out as
     * another contact, whose slot has this value added.
     */
    const val FINGER_SLOT_OFFSET = RawTouchService.FINGER_SLOT_OFFSET

    private const val TAG = "RawTouchInput"
    private const val QUEUE_CAPACITY = 256

    // Only used within this class, for a contact that ends without being released.
    private const val ACTION_CANCEL = 3

    private const val KEY_PAIRED = "paired"
    private const val KEY_PORT = "port"
    private const val KEY_TOKEN = "token"
    private const val START_SCRIPT_NAME = "stylus.sh"

    private const val CONNECT_TIMEOUT = 500
    private const val HANDSHAKE_TIMEOUT = 5000
    private const val START_ATTEMPTS = 25
    private const val START_ATTEMPT_INTERVAL = 200L

    private const val SOCKET_CONNECTED = 0
    private const val SOCKET_UNAVAILABLE = 1
    private const val SOCKET_REJECTED = 2

    // A contact that waited longer than this before being drained (for example across a pause) no longer presses.
    private const val MAX_PRESS_AGE = 250L


    /**
     * Whether contacts are queued for [drain] instead of being passed to the user interface.
     */
    @JvmStatic
    @Volatile
    var isActive = false
        set(value) {
            field = value

            if (value) {
                cancelInterfaceContact()
            } else {
                synchronized(queueLock) { queueSize = 0 }
            }
        }

    /**
     * Whether the service is running and connected.
     */
    @JvmStatic
    val isConnected
        get() = socket != null

    /**
     * A command that starts the service when run on a computer connected to this device, as an alternative to
     * wireless debugging. Available once connecting was attempted.
     */
    @JvmStatic
    @Volatile
    var computerCommand: String? = null
        private set

    /**
     * Whether connecting to the service, which includes starting it, is in progress.
     */
    @JvmStatic
    val isConnecting
        get() = pendingConnects.get() > 0

    private val pendingConnects = AtomicInteger()

    /**
     * Called on the main thread whenever [isConnected] or [isConnecting] changes.
     */
    @JvmStatic
    @Volatile
    var onConnectionChanged: (() -> Unit)? = null

    @Volatile
    private var socket: Socket? = null
        set(value) {
            val isChanged = (field == null) != (value == null)
            field = value

            if (isChanged) {
                mainHandler.post { onConnectionChanged?.invoke() }
            }
        }

    private var context: Context? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Everything that blocks runs here, one task at a time.
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "raw-touch-setup").apply { isDaemon = true } }

    private val adb by lazy { RawTouchAdb(context!!) }
    private val preferences by lazy { context!!.getSharedPreferences("rawtouch", Context.MODE_PRIVATE) }


    //region Queue

    private val queueLock = Any()
    private val queueSlot = IntArray(QUEUE_CAPACITY)
    private val queueAction = IntArray(QUEUE_CAPACITY)
    private val queueX = FloatArray(QUEUE_CAPACITY)
    private val queueY = FloatArray(QUEUE_CAPACITY)
    private val queueTime = LongArray(QUEUE_CAPACITY)
    private var queueSize = 0

    // Buffers of the draining thread, so that the sink is not called while holding the lock.
    private val drainSlot = IntArray(QUEUE_CAPACITY)
    private val drainAction = IntArray(QUEUE_CAPACITY)
    private val drainX = FloatArray(QUEUE_CAPACITY)
    private val drainY = FloatArray(QUEUE_CAPACITY)
    private val drainTime = LongArray(QUEUE_CAPACITY)

    //endregion

    //region Stylus

    @Volatile
    private var stylusState = RawTouchService.STYLUS_OUT_OF_RANGE

    // The bits of the X position in the upper half and those of the Y position in the lower half, in the 0-1 range
    // of the touchscreen. Packed so that both are always read from the same report.
    @Volatile
    private var stylusPosition = 0L

    private var wasStylusHovering = false

    //endregion

    //region User interface

    // Used by the draining thread and the main thread respectively.
    private val surfaceMapping = ScreenMapping()
    private val interfaceMapping = ScreenMapping()

    // The slot of the contact that is passed to the user interface, or -1 if there is none.
    @Volatile
    private var interfaceSlot = -1

    private var isInterfaceContactDown = false
    private var interfaceDownTime = 0L

    //endregion


    /**
     * Connects to the [RawTouchService] in the background if the user enabled the feature, so that it is ready before
     * gameplay starts.
     */
    @JvmStatic
    fun prepare(context: Context) {
        this.context = context.applicationContext

        if (Config.isStylusMultiTouch()) {
            connect()
        }
    }

    /**
     * Connects to the [RawTouchService] in the background, starting it if necessary.
     *
     * @param callback Called on the main thread with the outcome.
     */
    @JvmStatic
    @JvmOverloads
    fun connect(callback: ((Status) -> Unit)? = null) {
        pendingConnects.incrementAndGet()
        mainHandler.post { onConnectionChanged?.invoke() }

        executor.execute {
            val status = try {
                connectBlocking()
            } catch (e: Exception) {
                Log.e(TAG, "Unable to connect to the raw touch service", e)
                Status.FAILED
            } finally {
                pendingConnects.decrementAndGet()
                mainHandler.post { onConnectionChanged?.invoke() }
            }

            if (callback != null) {
                mainHandler.post { callback(status) }
            }
        }
    }

    /**
     * @return Whether the [RawTouchService] is connected. If it is not, connecting is attempted in the background.
     */
    @JvmStatic
    fun start(): Boolean {
        if (isConnected) {
            return true
        }

        connect()
        return false
    }

    /**
     * Disconnects from the [RawTouchService]. The service keeps running without reading any input, so that it can
     * be connected to again when debugging is no longer available to start it.
     */
    @JvmStatic
    fun stop() {
        isActive = false
        executor.execute { closeSocket() }
    }

    /**
     * Pairs this application with the ADB daemon in the background.
     *
     * @param port The port of the pairing service.
     * @param code The pairing code shown by the system.
     * @param callback Called on the main thread with whether pairing succeeded.
     */
    fun pair(context: Context, port: Int, code: String, callback: (Boolean) -> Unit) {
        this.context = context.applicationContext

        executor.execute {
            val isPaired = try {
                adb.pairWith(port, code)
            } catch (e: Exception) {
                Log.e(TAG, "Unable to pair with the ADB daemon", e)
                false
            }

            if (isPaired) {
                preferences.edit().putBoolean(KEY_PAIRED, true).commit()
            }

            mainHandler.post { callback(isPaired) }
        }
    }

    /**
     * Hands all queued contacts to a [Sink], oldest first. Must always be called from the same thread.
     */
    @JvmStatic
    fun drain(sink: Sink) {
        var count: Int

        synchronized(queueLock) {
            count = queueSize

            if (count == 0) {
                return
            }

            System.arraycopy(queueSlot, 0, drainSlot, 0, count)
            System.arraycopy(queueAction, 0, drainAction, 0, count)
            System.arraycopy(queueX, 0, drainX, 0, count)
            System.arraycopy(queueY, 0, drainY, 0, count)
            System.arraycopy(queueTime, 0, drainTime, 0, count)
            queueSize = 0
        }

        val now = SystemClock.uptimeMillis()

        for (i in 0 until count) {
            val action = drainAction[i]

            if (action == ACTION_DOWN) {
                if (now - drainTime[i] > MAX_PRESS_AGE) {
                    continue
                }

                // The display may have been rotated since the last contact.
                val activity = GlobalManager.getInstance().mainActivity ?: continue
                surfaceMapping.update(activity, activity.renderSurface)
            }

            surfaceMapping.map(drainX[i], drainY[i])
            sink.onRawTouch(drainSlot[i], action, surfaceMapping.x, surfaceMapping.y, drainTime[i])
        }
    }


    /**
     * Provides the position of the stylus while it hovers over the screen, which is where contacts that start in the
     * meantime are placed. Must be called from the thread that calls [drain].
     *
     * @param position Receives the position in the coordinate space of the render surface.
     * @return Whether the stylus hovers.
     */
    @JvmStatic
    fun getHoveringStylusPosition(position: FloatArray): Boolean {
        if (stylusState != RawTouchService.STYLUS_HOVERING) {
            wasStylusHovering = false
            return false
        }

        if (!wasStylusHovering) {
            // The display may have been rotated since the stylus was last in range.
            val activity = GlobalManager.getInstance().mainActivity ?: return false

            surfaceMapping.update(activity, activity.renderSurface)
            wasStylusHovering = true
        }

        val packed = stylusPosition

        surfaceMapping.map(Float.fromBits((packed ushr 32).toInt()), Float.fromBits(packed.toInt()))

        position[0] = surfaceMapping.x
        position[1] = surfaceMapping.y
        return true
    }

    /**
     * Forwards a contact to the user interface, which is where contacts go outside of gameplay. Only one contact is
     * forwarded at a time, which is all that menus need.
     */
    private fun forwardToInterface(slot: Int, action: Int, x: Float, y: Float, eventTime: Long) {
        if (slot >= FINGER_SLOT_OFFSET) {
            return
        }

        if (action == ACTION_DOWN) {
            if (interfaceSlot != -1) {
                return
            }

            interfaceSlot = slot
        } else if (slot != interfaceSlot) {
            return
        }

        if (action == ACTION_UP) {
            interfaceSlot = -1
        }

        mainHandler.post { dispatchToInterface(action, x, y, eventTime) }
    }

    private fun cancelInterfaceContact() {
        if (interfaceSlot != -1) {
            interfaceSlot = -1
            mainHandler.post { dispatchToInterface(ACTION_CANCEL, 0f, 0f, SystemClock.uptimeMillis()) }
        }
    }

    private fun dispatchToInterface(action: Int, x: Float, y: Float, eventTime: Long) {
        val activity = GlobalManager.getInstance().mainActivity ?: return

        if (action == ACTION_DOWN) {
            // Contacts keep arriving while another application is in front.
            if (!activity.hasWindowFocus()) {
                return
            }

            interfaceMapping.update(activity, activity.window.decorView)
            interfaceDownTime = eventTime
            isInterfaceContactDown = true
        } else if (!isInterfaceContactDown) {
            return
        }

        val motionAction = when (action) {
            ACTION_DOWN -> MotionEvent.ACTION_DOWN
            ACTION_MOVE -> MotionEvent.ACTION_MOVE
            ACTION_UP -> MotionEvent.ACTION_UP
            else -> MotionEvent.ACTION_CANCEL
        }

        if (action == ACTION_UP || action == ACTION_CANCEL) {
            isInterfaceContactDown = false
        }

        // A cancelled contact stays where it was.
        if (action != ACTION_CANCEL) {
            interfaceMapping.map(x, y)
        }

        val event = MotionEvent.obtain(
            interfaceDownTime,
            eventTime.coerceAtLeast(interfaceDownTime),
            motionAction,
            interfaceMapping.x,
            interfaceMapping.y,
            0
        )

        event.source = InputDevice.SOURCE_TOUCHSCREEN

        try {
            activity.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }


    private fun connectBlocking(): Status {
        val context = context ?: return Status.FAILED

        if (isConnected) {
            return Status.READY
        }

        var port = preferences.getInt(KEY_PORT, 0)
        var token = preferences.getString(KEY_TOKEN, null)

        // Both stay the same from then on, so that a service started from a computer is found as well.
        if (port == 0 || token == null || token.length != RawTouchService.TOKEN_LENGTH) {
            port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
            token = ByteArray(RawTouchService.TOKEN_LENGTH / 2).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }

            preferences.edit().putInt(KEY_PORT, port).putString(KEY_TOKEN, token).commit()
        }

        val processName = context.packageName + ":rawtouch"

        // Detached from the shell, so that the service keeps running when the ADB connection is closed. The shell
        // stays for a moment, as the service would be stopped along with it before it has detached itself.
        val command = "(trap '' HUP; CLASSPATH='${context.applicationInfo.sourceDir}' setsid nohup app_process" +
            " /system/bin --nice-name='$processName' ${RawTouchService::class.java.name} '$processName' $port $token" +
            " </dev/null >/dev/null 2>&1 &); sleep 1"

        writeStartScript(context, command)

        when (openSocket()) {
            SOCKET_CONNECTED -> return Status.READY
            SOCKET_REJECTED -> return Status.FAILED
        }

        if (!preferences.getBoolean(KEY_PAIRED, false)) {
            return Status.PAIRING_REQUIRED
        }

        when (adb.run(command)) {
            RawTouchAdb.Result.NOT_PAIRED -> {
                preferences.edit().putBoolean(KEY_PAIRED, false).commit()
                return Status.PAIRING_REQUIRED
            }

            RawTouchAdb.Result.UNREACHABLE -> return Status.DEBUGGING_REQUIRED
            RawTouchAdb.Result.FAILED -> return Status.FAILED
            RawTouchAdb.Result.DONE -> Unit
        }

        // The service takes about a second to come up.
        repeat(START_ATTEMPTS) {
            Thread.sleep(START_ATTEMPT_INTERVAL)

            when (openSocket()) {
                SOCKET_CONNECTED -> return Status.READY
                SOCKET_REJECTED -> return Status.FAILED
            }
        }

        Log.e(TAG, "The raw touch service did not come up")
        return Status.FAILED
    }

    /**
     * Writes the command that starts the service to a file that an ADB shell can run, see [computerCommand]. The
     * command changes whenever the application is updated.
     */
    private fun writeStartScript(context: Context, command: String) {
        try {
            val file = File(context.getExternalFilesDir(null) ?: return, START_SCRIPT_NAME)
            val content = "#!/system/bin/sh\n$command\necho 'The touch reader of osu!droid was started.'\n"

            if (!file.exists() || file.readText() != content) {
                file.writeText(content)
            }

            computerCommand = "adb shell sh ${file.absolutePath}"
        } catch (e: Exception) {
            Log.w(TAG, "Unable to write the start script", e)
        }
    }

    /**
     * @return [SOCKET_CONNECTED], [SOCKET_REJECTED] if the service is running but unable to read the touchscreen, or
     * [SOCKET_UNAVAILABLE] if no usable service is running.
     */
    private fun openSocket(): Int {
        val port = preferences.getInt(KEY_PORT, 0)
        val token = preferences.getString(KEY_TOKEN, null)

        if (port == 0 || token == null || token.length != RawTouchService.TOKEN_LENGTH) {
            return SOCKET_UNAVAILABLE
        }

        val socket = Socket()

        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT)
            socket.soTimeout = HANDSHAKE_TIMEOUT

            val output = DataOutputStream(socket.getOutputStream())
            output.writeInt(RawTouchService.PROTOCOL_VERSION)
            output.write(token.toByteArray(Charsets.US_ASCII))
            output.flush()

            val input = DataInputStream(socket.getInputStream())
            val errorLength = input.readInt()

            if (errorLength != 0) {
                val error = ByteArray(errorLength.coerceIn(0, 1024)).also { input.readFully(it) }

                Log.e(TAG, "The raw touch service did not start: ${String(error)}")
                socket.close()
                return SOCKET_REJECTED
            }

            socket.soTimeout = 0
        } catch (_: Exception) {
            // Nothing listens on the port, or what does is not a service of this version.
            try {
                socket.close()
            } catch (_: Exception) {
            }

            return SOCKET_UNAVAILABLE
        }

        this.socket = socket
        Thread({ read(socket) }, "raw-touch-input").apply { isDaemon = true }.start()

        Log.i(TAG, "The raw touch service is running")
        return SOCKET_CONNECTED
    }

    private fun read(socket: Socket) {
        val bytes = ByteArray(RawTouchService.RECORD_SIZE)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        try {
            val stream = socket.getInputStream()

            while (true) {
                var filled = 0

                while (filled < bytes.size) {
                    val read = stream.read(bytes, filled, bytes.size - filled)

                    if (read <= 0) {
                        return
                    }

                    filled += read
                }

                if (buffer.getInt(0) == RawTouchService.STYLUS_SLOT) {
                    stylusPosition = (buffer.getInt(8).toLong() shl 32) or (buffer.getInt(12).toLong() and 0xFFFFFFFFL)
                    stylusState = buffer.getInt(4)
                    continue
                }

                if (!isActive) {
                    forwardToInterface(
                        buffer.getInt(0),
                        buffer.getInt(4),
                        buffer.getFloat(8),
                        buffer.getFloat(12),
                        buffer.getLong(16)
                    )
                    continue
                }

                synchronized(queueLock) {
                    // When the queue is full, the newest contact is dropped. Releases always fit, because the
                    // queue is far larger than the number of contacts drained per frame.
                    if (queueSize < QUEUE_CAPACITY) {
                        val index = queueSize++

                        queueSlot[index] = buffer.getInt(0)
                        queueAction[index] = buffer.getInt(4)
                        queueX[index] = buffer.getFloat(8)
                        queueY[index] = buffer.getFloat(12)
                        queueTime[index] = buffer.getLong(16)
                    }
                }
            }
        } catch (_: Exception) {
            // The service stopped or the socket was closed.
        } finally {
            stylusState = RawTouchService.STYLUS_OUT_OF_RANGE
            cancelInterfaceContact()

            if (this.socket === socket) {
                this.socket = null
            }

            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }

        socket = null
    }


    /**
     * Converts positions on the touchscreen, which are in its natural orientation, to positions relative to a view.
     */
    private class ScreenMapping {

        var x = 0f
            private set

        var y = 0f
            private set

        private val displaySize = Point()
        private val origin = IntArray(2)
        private var rotation = Surface.ROTATION_0

        /**
         * Reads the current rotation of the display and the location of the view that positions are relative to.
         */
        fun update(activity: Activity, view: View?) {
            @Suppress("DEPRECATION")
            val display = activity.windowManager.defaultDisplay

            @Suppress("DEPRECATION")
            display.getRealSize(displaySize)
            rotation = display.rotation

            view?.getLocationOnScreen(origin)
        }

        /**
         * Converts a position in the 0-1 range of the touchscreen and stores the result in [x] and [y].
         */
        fun map(normalizedX: Float, normalizedY: Float) {
            val naturalWidth: Int
            val naturalHeight: Int

            if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
                naturalWidth = displaySize.x
                naturalHeight = displaySize.y
            } else {
                naturalWidth = displaySize.y
                naturalHeight = displaySize.x
            }

            val naturalX = normalizedX * naturalWidth
            val naturalY = normalizedY * naturalHeight
            val screenX: Float
            val screenY: Float

            when (rotation) {
                Surface.ROTATION_90 -> {
                    screenX = naturalY
                    screenY = naturalWidth - naturalX
                }

                Surface.ROTATION_180 -> {
                    screenX = naturalWidth - naturalX
                    screenY = naturalHeight - naturalY
                }

                Surface.ROTATION_270 -> {
                    screenX = naturalHeight - naturalY
                    screenY = naturalX
                }

                else -> {
                    screenX = naturalX
                    screenY = naturalY
                }
            }

            x = screenX - origin[0]
            y = screenY - origin[1]
        }
    }
}
