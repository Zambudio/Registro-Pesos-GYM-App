package com.zambudio.registropesos.domain.usecase

import java.time.Instant
import java.time.ZoneId

/** Filtros de rango de las gráficas. El inicio es el comienzo del día de hace N meses. */
enum class ProgressRange(private val months: Long?) {
    M1(months = 1),
    M3(months = 3),
    M6(months = 6),
    Y1(months = 12),
    ALL(months = null),
    ;

    fun fromMillis(now: Long, zone: ZoneId): Long {
        val back = months ?: return 0L
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .minusMonths(back)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
    }
}
