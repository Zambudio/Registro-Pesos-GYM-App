package com.zambudio.registropesos.domain.usecase

import com.zambudio.registropesos.domain.model.SessionVolumePoint
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Volumen de una semana ISO (empieza en lunes) en la zona dada. */
data class WeeklyVolumePoint(
    val weekStart: LocalDate,
    val weekStartMillis: Long,
    val volumeKg: Double,
    val sessionCount: Int,
)

/**
 * Agrupa volúmenes por sesión en semanas ISO. Las semanas sin datos entre la primera y la
 * última (o hasta [untilMillis] si se da) salen con 0 para que la gráfica no salte.
 */
object WeeklyVolumeByMuscleGroup {
    operator fun invoke(
        points: List<SessionVolumePoint>,
        zone: ZoneId,
        untilMillis: Long? = null,
    ): List<WeeklyVolumePoint> {
        if (points.isEmpty()) return emptyList()
        val byWeek = points.groupBy { weekStartOf(it.startedAt, zone) }
        val first = byWeek.keys.min()
        val lastWithData = byWeek.keys.max()
        val last = untilMillis?.let { maxOf(lastWithData, weekStartOf(it, zone)) } ?: lastWithData
        return generateSequence(first) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(last) }
            .map { week ->
                val weekPoints = byWeek[week].orEmpty()
                WeeklyVolumePoint(
                    weekStart = week,
                    weekStartMillis = week.atStartOfDay(zone).toInstant().toEpochMilli(),
                    volumeKg = weekPoints.sumOf { it.volumeKg },
                    sessionCount = weekPoints.distinctBy { it.sessionId }.size,
                )
            }
            .toList()
    }

    fun weekStartOf(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
}
