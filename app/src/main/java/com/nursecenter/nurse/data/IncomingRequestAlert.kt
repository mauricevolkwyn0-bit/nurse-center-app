package com.nursecenter.nurse.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nursecenter.nurse.MainActivity
import com.nursecenter.nurse.R

/** Vibrates, plays the NurseCenter chime and shows a heads-up notification when a client request arrives. */
object IncomingRequestAlert {
    private const val TAG = "IncomingRequestAlert"
    private const val CHANNEL_ID = "incoming_requests"
    private val VIBRATION = longArrayOf(0, 700, 300, 700, 300, 700, 300, 700)

    // Alarm usage so the alert still vibrates and sounds when the app is in the background;
    // Android drops unattributed vibrations from background apps.
    private val ALARM_AUDIO = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Held so the player isn't garbage collected mid-chime. */
    private var player: MediaPlayer? = null
    /** The booking whose chime and vibration are currently playing. */
    @Volatile private var ringingFor: String? = null

    /** @param expiresAtMillis when the nurse's window to respond closes. */
    fun fire(context: Context, bookingId: String, expiresAtMillis: Long) {
        ringingFor = bookingId
        vibrate(context)
        playSound(context)
        notify(context, bookingId, expiresAtMillis)
    }

    /** Silences and removes the alert for a request that was cancelled or taken elsewhere. */
    fun dismiss(context: Context, bookingId: String) {
        NotificationManagerCompat.from(context).cancel(bookingId.hashCode())
        if (ringingFor != bookingId) return
        ringingFor = null
        vibrator(context)?.cancel()
        runCatching { player?.stop() }
        player?.release()
        player = null
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    private fun vibrate(context: Context) {
        val vibrator = vibrator(context)
        if (vibrator == null || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(VIBRATION, -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, ALARM_AUDIO)
        }
    }

    private fun playSound(context: Context) {
        val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.incoming_request}")
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(ALARM_AUDIO)
                setDataSource(context, uri)
                setOnCompletionListener { done ->
                    done.release()
                    if (player === done) player = null
                }
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "Couldn't play request chime: ${it.message}") }
    }

    private fun notify(context: Context, bookingId: String, expiresAtMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java)
        // Sound and vibration are played by [fire] directly, so the channel itself stays silent to avoid doubling up.
        val channel = NotificationChannel(CHANNEL_ID, "Incoming requests", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when a client requests a visit"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remaining = (expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("New care request")
            .setContentText("A client has requested a visit. Respond before the timer runs out.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            // Live countdown to the end of the response window; the notification removes itself when it expires.
            .setWhen(expiresAtMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(remaining)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(bookingId.hashCode(), notification) }
    }
}
