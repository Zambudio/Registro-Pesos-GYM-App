package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.SessionVolumePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private val madrid: ZoneId = ZoneId.of("Europe/Madrid")

private fun millis(y: Int, m: Int, d: Int, h: Int = 10, min: Int = 0): Long =
    ZonedDateTime.of(y, m, d, h, min, 0, 0, madrid).toInstant().toEpochMilli()

class WeeklyVolumeByMuscleGroupTest {
    @Test
    fun emptyInput_isEmpty() {
        assertTrue(WeeklyVolumeByMuscleGroup(emptyList(), madrid).isEmpty())
    }

    @Test
    fun groupsByIsoWeek_andFillsGapsWithZero() {
        val points = listOf(
            SessionVolumePoint("s1", millis(2026, 1, 5), 1000.0), // lunes
            SessionVolumePoint("s2", millis(2026, 1, 11, h = 20), 500.0), // domingo, misma semana
            SessionVolumePoint("s3", millis(2026, 1, 21), 800.0), // miércoles, dos semanas después
        )

        val weeks = WeeklyVolumeByMuscleGroup(points, madrid)

        assertEquals(
            listOf(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 1, 19)),
            weeks.map { it.weekStart },
        )
        assertEquals(listOf(1500.0, 0.0, 800.0), weeks.map { it.volumeKg })
        assertEquals(listOf(2, 0, 1), weeks.map { it.sessionCount })
        assertEquals(millis(2026, 1, 12, h = 0), weeks[1].weekStartMillis)
    }

    @Test
    fun weekBoundary_usesTheGivenZone() {
        // Lunes 00:30 en Madrid es aún domingo en UTC.
        val mondayEarly = millis(2026, 1, 12, h = 0, min = 30)
        val sundayLate = millis(2026, 1, 11, h = 23, min = 30)
        val points = listOf(SessionVolumePoint("a", sundayLate, 1.0), SessionVolumePoint("b", mondayEarly, 2.0))

        val madridWeeks = WeeklyVolumeByMuscleGroup(points, madrid)
        val utcWeeks = WeeklyVolumeByMuscleGroup(points, ZoneId.of("UTC"))

        assertEquals(listOf(1.0, 2.0), madridWeeks.map { it.volumeKg })
        assertEquals(listOf(3.0), utcWeeks.map { it.volumeKg })
    }

    @Test
    fun sameSessionTwice_countsOnce() {
        val points = listOf(
            SessionVolumePoint("s1", millis(2026, 1, 6), 100.0),
            SessionVolumePoint("s1", millis(2026, 1, 6), 50.0),
        )
        val week = WeeklyVolumeByMuscleGroup(points, madrid).single()
        assertEquals(1, week.sessionCount)
        assertEquals(150.0, week.volumeKg, 0.0)
    }

    @Test
    fun untilMillis_extendsWithEmptyWeeks() {
        val points = listOf(SessionVolumePoint("s1", millis(2026, 1, 6), 100.0))
        val weeks = WeeklyVolumeByMuscleGroup(points, madrid, untilMillis = millis(2026, 1, 22))
        assertEquals(listOf(100.0, 0.0, 0.0), weeks.map { it.volumeKg })
    }
}

class ProgressRangeTest {
    private val now = millis(2026, 3, 31, h = 18, min = 45)

    @Test
    fun all_startsAtEpoch() {
        assertEquals(0L, ProgressRange.ALL.fromMillis(now, madrid))
    }

    @Test
    fun months_goBackToStartOfDay_clampingShortMonths() {
        assertEquals(millis(2026, 2, 28, h = 0), ProgressRange.M1.fromMillis(now, madrid))
        assertEquals(millis(2025, 12, 31, h = 0), ProgressRange.M3.fromMillis(now, madrid))
        assertEquals(millis(2025, 9, 30, h = 0), ProgressRange.M6.fromMillis(now, madrid))
        assertEquals(millis(2025, 3, 31, h = 0), ProgressRange.Y1.fromMillis(now, madrid))
    }
}
