package com.zambudio.registropesos.data.rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zambudio.registropesos.core.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Recibe la alarma de fin de descanso y la acción "Saltar" de la notificación de cuenta atrás. */
@AndroidEntryPoint
class RestTimerReceiver : BroadcastReceiver() {

    @Inject
    lateinit var restTimer: AndroidRestTimer

    @Inject
    @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_FINISHED -> restTimer.onAlarm()
                    ACTION_SKIP -> restTimer.stop()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FINISHED = "com.zambudio.registropesos.action.REST_FINISHED"
        const val ACTION_SKIP = "com.zambudio.registropesos.action.REST_SKIP"
    }
}
