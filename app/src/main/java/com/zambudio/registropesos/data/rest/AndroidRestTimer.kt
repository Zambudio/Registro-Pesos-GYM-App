package com.zambudio.registropesos.data.rest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.zambudio.registropesos.domain.model.RestTimerState
import com.zambudio.registropesos.domain.repository.RestTimer
import com.zambudio.registropesos.domain.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descanso entre series (ADR-020). El estado (fin y duración) vive en el DataStore de ajustes para
 * sobrevivir a la muerte del proceso; el aviso final lo da una alarma exacta del sistema
 * ([RestAlerts]), así suena aunque la app esté cerrada o el móvil bloqueado.
 */
@Singleton
class AndroidRestTimer @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val alerts: RestAlerts,
    private val timeProvider: TimeProvider,
) : RestTimer {

    /**
     * Ordena estado y alarma: sin él, dos "+15 s" seguidos perderían uno y un "parar" cruzado con un
     * "empezar" podría dejar un descanso visible sin alarma.
     */
    private val mutex = Mutex()

    override val state: Flow<RestTimerState?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val endsAt = prefs[ENDS_AT] ?: return@map null
            // Un descanso que acabó hace rato y no se cerró (alarma perdida por un reinicio, ajustes
            // restaurados de una copia...) no se muestra.
            if (endsAt < timeProvider.now() - STALE_AFTER_MILLIS) return@map null
            RestTimerState(endsAt = endsAt, durationMillis = prefs[DURATION] ?: 0L)
        }
        .distinctUntilChanged()

    override suspend fun start(durationMillis: Long) = mutex.withLock {
        if (durationMillis <= 0L) {
            clear()
        } else {
            save(RestTimerState(endsAt = timeProvider.now() + durationMillis, durationMillis = durationMillis))
        }
    }

    override suspend fun adjust(deltaMillis: Long) = mutex.withLock {
        val current = state.first() ?: return@withLock
        val now = timeProvider.now()
        val endsAt = current.endsAt + deltaMillis
        if (endsAt <= now) return@withLock clear()
        // La duración total acompaña al ajuste para que la barra de progreso siga siendo coherente.
        val duration = (current.durationMillis + deltaMillis).coerceAtLeast(endsAt - now)
        save(RestTimerState(endsAt = endsAt, durationMillis = duration))
    }

    override suspend fun stop() = mutex.withLock { clear() }

    private suspend fun clear() {
        dataStore.edit {
            it.remove(ENDS_AT)
            it.remove(DURATION)
        }
        alerts.cancel()
    }

    /**
     * Llamado por la alarma. Solo cierra el descanso si de verdad ha terminado: una alarma de un
     * descanso ya ajustado o parado no debe avisar (no debería llegar, pero no cuesta comprobarlo).
     */
    suspend fun onAlarm() = mutex.withLock {
        val current = state.first() ?: return@withLock
        if (current.endsAt - timeProvider.now() > ALARM_TOLERANCE_MILLIS) return@withLock
        dataStore.edit {
            it.remove(ENDS_AT)
            it.remove(DURATION)
        }
        alerts.showFinished()
    }

    private suspend fun save(state: RestTimerState) {
        dataStore.edit {
            it[ENDS_AT] = state.endsAt
            it[DURATION] = state.durationMillis
        }
        alerts.schedule(state)
    }

    private companion object {
        val ENDS_AT = longPreferencesKey("rest_ends_at")
        val DURATION = longPreferencesKey("rest_duration_ms")

        /** Las alarmas exactas pueden adelantarse unos milisegundos. */
        const val ALARM_TOLERANCE_MILLIS = 1_000L

        /** Pasado este tiempo desde el final sin que la alarma lo cierre, el descanso se da por caducado. */
        const val STALE_AFTER_MILLIS = 60_000L
    }
}
