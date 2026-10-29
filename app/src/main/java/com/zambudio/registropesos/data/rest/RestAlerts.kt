package com.zambudio.registropesos.data.rest

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.zambudio.registropesos.MainActivity
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.model.RestTimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Efectos del descanso fuera de la app: alarma del sistema y notificaciones. */
interface RestAlerts {
    /** Programa el aviso final y muestra la cuenta atrás en la barra de notificaciones. */
    fun schedule(state: RestTimerState)

    /** Anula el aviso programado y quita la cuenta atrás. */
    fun cancel()

    /** "Descanso terminado": notificación con sonido y vibración. */
    fun showFinished()
}

class AndroidRestAlerts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : RestAlerts {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notifications = NotificationManagerCompat.from(context)

    override fun schedule(state: RestTimerState) {
        val alarm = alarmIntent()
        // USE_EXACT_ALARM (API 33+) se concede al instalar; en API 31-32 SCHEDULE_EXACT_ALARM también.
        // Si el usuario lo retirara, el aviso llega igualmente, solo que con algo de retraso.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.endsAt, alarm)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.endsAt, alarm)
        }
        notify(COUNTDOWN_ID, countdownNotification(state))
    }

    override fun cancel() {
        alarmManager.cancel(alarmIntent())
        notifications.cancel(COUNTDOWN_ID)
    }

    override fun showFinished() {
        notifications.cancel(COUNTDOWN_ID)
        val notification = NotificationCompat.Builder(context, FINISHED_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(context.getString(R.string.workout_rest_finished_title))
            .setContentText(context.getString(R.string.workout_rest_finished_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(VIBRATION_PATTERN)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setAutoCancel(true)
            .setTimeoutAfter(FINISHED_TIMEOUT_MILLIS)
            .setContentIntent(openApp())
            .build()
        notify(FINISHED_ID, notification)
    }

    private fun countdownNotification(state: RestTimerState) =
        NotificationCompat.Builder(context, COUNTDOWN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle(context.getString(R.string.workout_rest_countdown_title))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(state.endsAt)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setTimeoutAfter((state.endsAt - System.currentTimeMillis()).coerceAtLeast(0L))
            .setContentIntent(openApp())
            .addAction(0, context.getString(R.string.workout_rest_skip), skipIntent())
            .build()

    private fun notify(id: Int, notification: Notification) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        ensureChannels()
        notifications.notify(id, notification)
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val finished = NotificationChannel(
            FINISHED_CHANNEL,
            context.getString(R.string.workout_rest_channel_finished),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
        }
        val countdown = NotificationChannel(
            COUNTDOWN_CHANNEL,
            context.getString(R.string.workout_rest_channel_countdown),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannels(listOf(finished, countdown))
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_FINISHED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun skipIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        SKIP_REQUEST,
        Intent(context, RestTimerReceiver::class.java).setAction(RestTimerReceiver.ACTION_SKIP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_REQUEST,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val FINISHED_CHANNEL = "rest_finished"
        const val COUNTDOWN_CHANNEL = "rest_countdown"
        const val FINISHED_ID = 2001
        const val COUNTDOWN_ID = 2002
        const val ALARM_REQUEST = 1
        const val SKIP_REQUEST = 2
        const val OPEN_REQUEST = 3
        const val FINISHED_TIMEOUT_MILLIS = 60_000L
        val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400, 200, 600)
    }
}
