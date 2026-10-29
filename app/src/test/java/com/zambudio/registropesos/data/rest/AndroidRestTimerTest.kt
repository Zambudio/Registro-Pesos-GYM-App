package com.zambudio.registropesos.data.rest

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.zambudio.registropesos.domain.model.RestTimerState
import com.zambudio.registropesos.feature.workout.TestClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidRestTimerTest {

    private val clock = TestClock(1_000_000L)
    private val alerts = FakeRestAlerts()

    /** Hace de fichero de ajustes: sobrevive a crear otro [AndroidRestTimer] (otro "proceso"). */
    private val store = InMemoryPreferences()

    private fun newTimer(): AndroidRestTimer = AndroidRestTimer(store, alerts, clock)

    @Test
    fun startPersistsTheRestAndSchedulesTheAlarm() = runTest {
        val timer = newTimer()

        timer.start(90_000L)

        val expected = RestTimerState(endsAt = 1_090_000L, durationMillis = 90_000L)
        assertEquals(expected, timer.state.first())
        assertEquals(listOf(expected), alerts.scheduled)
    }

    @Test
    fun adjustMovesTheEndAndStopsWhenNothingIsLeft() = runTest {
        val timer = newTimer()
        timer.start(60_000L)

        timer.adjust(15_000L)
        assertEquals(RestTimerState(endsAt = 1_075_000L, durationMillis = 75_000L), timer.state.first())

        clock.now += 50_000L
        timer.adjust(-15_000L)
        // Quedan 10 s de 60 s totales: la duración nunca baja de lo que falta.
        assertEquals(RestTimerState(endsAt = 1_060_000L, durationMillis = 60_000L), timer.state.first())

        timer.adjust(-15_000L)
        assertNull(timer.state.first())
        assertEquals(1, alerts.cancels)
    }

    @Test
    fun adjustWithoutRestDoesNothing() = runTest {
        val timer = newTimer()

        timer.adjust(15_000L)

        assertNull(timer.state.first())
        assertTrue(alerts.scheduled.isEmpty())
    }

    @Test
    fun alarmClosesTheRestAndNotifiesOnlyWhenItIsDue() = runTest {
        val timer = newTimer()
        timer.start(60_000L)

        clock.now += 30_000L
        timer.onAlarm()
        assertEquals("alarma adelantada: se ignora", 0, alerts.finished)

        clock.now += 30_000L
        timer.onAlarm()
        assertNull(timer.state.first())
        assertEquals(1, alerts.finished)

        timer.onAlarm()
        assertEquals("sin descanso no avisa otra vez", 1, alerts.finished)
    }

    @Test
    fun restThatEndedLongAgoWithoutAlarmIsNotShown() = runTest {
        // Alarma perdida (reinicio del móvil) o ajustes restaurados de una copia antigua.
        newTimer().start(60_000L)
        clock.now += 60_000L + 30_000L
        assertEquals("recién terminado: aún se ve", 1_060_000L, newTimer().state.first()?.endsAt)

        clock.now += 60_000L

        assertNull(newTimer().state.first())
    }

    @Test
    fun restSurvivesTheDeathOfTheProcess() = runTest {
        newTimer().start(120_000L)

        val afterRestart = newTimer()

        assertEquals(RestTimerState(endsAt = 1_120_000L, durationMillis = 120_000L), afterRestart.state.first())
    }
}

/**
 * DataStore en memoria. El real no se usa aquí porque en Windows falla al renombrar su fichero
 * temporal con escrituras muy seguidas; su persistencia es cosa de la librería.
 */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

private class FakeRestAlerts : RestAlerts {
    val scheduled = mutableListOf<RestTimerState>()
    var cancels = 0
    var finished = 0

    override fun schedule(state: RestTimerState) {
        scheduled += state
    }

    override fun cancel() {
        cancels++
    }

    override fun showFinished() {
        finished++
    }
}
