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

    override val state: Flow<RestTimerState?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val endsAt = prefs[ENDS_AT] ?: return@map null
            RestTimerState(endsAt = endsAt, durationMillis = prefs[DURATION] ?: 0L)
        }
        .distinctUntilChanged()

    override suspend fun start(durationMillis: Long) {
        if (durationMillis <= 0L) return stop()
        save(RestTimerState(endsAt = timeProvider.now() + durationMillis, durationMillis = durationMillis))
    }

    override suspend fun adjust(deltaMillis: Long) {
        val current = state.first() ?: return
        val now = timeProvider.now()
        val endsAt = current.endsAt + deltaMillis
        if (endsAt <= now) return stop()
        // La duración total acompaña al ajuste para que la barra de progreso siga siendo coherente.
        val duration = (current.durationMillis + deltaMillis).coerceAtLeast(endsAt - now)
        save(RestTimerState(endsAt = endsAt, durationMillis = duration))
    }

    override suspend fun stop() {
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
    suspend fun onAlarm() {
        val current = state.first() ?: return
        if (current.endsAt - timeProvider.now() > ALARM_TOLERANCE_MILLIS) return
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
    }
}
