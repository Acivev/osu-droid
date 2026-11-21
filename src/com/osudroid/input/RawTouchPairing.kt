package com.osudroid.input

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import ru.nsu.ccfit.zuev.osuplus.R

/**
 * Guides the user through pairing this application with the ADB daemon of the device.
 *
 * The system only shows the pairing code while its pairing dialog is open, and closes that dialog when another
 * application comes to the foreground. The code is therefore asked for in a notification, which the user can answer
 * without leaving the dialog.
 */
object RawTouchPairing {

    private const val CHANNEL_ID = "stylus_setup"
    private const val NOTIFICATION_ID = 0x5254
    internal const val KEY_CODE = "code"

    private var discovery: RawTouchAdbDiscovery? = null

    @Volatile
    private var pairingPort = -1


    /**
     * Starts looking for the pairing dialog of the system, and asks for the pairing code once it is open.
     */
    @JvmStatic
    fun begin(context: Context) {
        val applicationContext = context.applicationContext

        cancel(applicationContext)
        notify(applicationContext, R.string.stylusMultiTouch_pairing_searching, isOngoing = true)

        discovery = RawTouchAdbDiscovery(applicationContext, RawTouchAdbDiscovery.PAIRING) { port ->
            if (port == pairingPort) {
                return@RawTouchAdbDiscovery
            }

            pairingPort = port

            if (port > 0) {
                notify(applicationContext, R.string.stylusMultiTouch_pairing_enter_code, isOngoing = true, hasReply = true)
            } else {
                notify(applicationContext, R.string.stylusMultiTouch_pairing_searching, isOngoing = true)
            }
        }.apply { start() }
    }

    /**
     * Stops looking for the pairing dialog and removes the notification.
     */
    @JvmStatic
    fun cancel(context: Context) {
        stopDiscovery()
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    internal fun onCodeEntered(context: Context, code: String) {
        val port = pairingPort

        if (port <= 0) {
            // The pairing dialog was closed in the meantime, or the application was restarted.
            begin(context)
            return
        }

        notify(context, R.string.stylusMultiTouch_pairing_working, isOngoing = true)

        RawTouchInput.pair(context, port, code.trim()) { isPaired ->
            if (!isPaired) {
                // The dialog is still open, so the user can try again.
                notify(context, R.string.stylusMultiTouch_pairing_failed, isOngoing = true, hasReply = true)
                return@pair
            }

            stopDiscovery()

            RawTouchInput.connect { status ->
                notify(
                    context,
                    if (status == RawTouchInput.Status.READY) R.string.stylusMultiTouch_ready
                    else R.string.stylusMultiTouch_failed
                )
            }
        }
    }

    private fun stopDiscovery() {
        discovery?.stop()
        discovery = null
        pairingPort = -1
    }

    private fun notify(context: Context, text: Int, isOngoing: Boolean = false, hasReply: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.stylusMultiTouch_notification_channel),
                    // Shown on top of the settings, where the user is while pairing.
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notify_inso)
            .setContentTitle(context.getString(R.string.opt_stylusMultiTouch_title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)

        if (hasReply) {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT

            // The system adds the entered code to the intent.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or PendingIntent.FLAG_MUTABLE
            }

            val intent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, RawTouchPairingReceiver::class.java),
                flags
            )

            val input = RemoteInput.Builder(KEY_CODE)
                .setLabel(context.getString(R.string.stylusMultiTouch_pairing_code_hint))
                .build()

            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.notify_inso,
                    context.getString(R.string.stylusMultiTouch_pairing_enter_code_action),
                    intent
                ).addRemoteInput(input).build()
            )
        }

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notifications are not allowed.
        }
    }
}

/**
 * Receives the pairing code that the user entered in the notification of [RawTouchPairing].
 */
class RawTouchPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(RawTouchPairing.KEY_CODE) ?: return

        RawTouchPairing.onCodeEntered(context.applicationContext, code.toString())
    }
}
