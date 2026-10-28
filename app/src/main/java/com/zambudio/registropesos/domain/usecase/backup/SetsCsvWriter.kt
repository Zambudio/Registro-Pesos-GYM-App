package com.zambudio.registropesos.domain.usecase.backup

import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.SessionDto
import com.zambudio.registropesos.domain.backup.SetDto
import com.zambudio.registropesos.domain.model.SessionStatus
import com.zambudio.registropesos.domain.usecase.CalculateE1rm
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * sets.csv (RFC 4180, CRLF, separador coma, decimal con punto): una fila por serie de las
 * sesiones FINISHED, en orden cronológico. Devuelve texto sin BOM; quien escriba el fichero
 * en UTF-8 antepone [UTF8_BOM] para que Excel detecte la codificación.
 */
object SetsCsvWriter {
    const val UTF8_BOM = "﻿"
    const val HEADER = "fecha,hora,rutina,grupo,ejercicio,serie,kg,reps,e1rm"
    private const val LINE_END = "\r\n"

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val charsNeedingQuotes = charArrayOf(',', '"', '\n', '\r')

    fun write(snapshot: BackupSnapshot, zone: ZoneId): String {
        val setsBySession = snapshot.sets.groupBy { it.sessionId }
        val sessions = snapshot.sessions
            .filter { it.status == SessionStatus.FINISHED.name }
            .sortedWith(compareBy({ it.startedAt }, { it.id }))
        return buildString {
            append(HEADER).append(LINE_END)
            for (session in sessions) {
                setsBySession[session.id].orEmpty()
                    .sortedWith(compareBy({ it.exerciseOrder }, { it.setIndex }))
                    .forEach { set -> append(row(session, set, zone)).append(LINE_END) }
            }
        }
    }

    private fun row(session: SessionDto, set: SetDto, zone: ZoneId): String {
        val startedAt = Instant.ofEpochMilli(session.startedAt).atZone(zone)
        val reps = set.reps
        val weight = set.weightKg
        val e1rm = if (weight != null && reps != null && reps > 0) CalculateE1rm(weight, reps) else null
        return listOf(
            dateFormat.format(startedAt),
            timeFormat.format(startedAt),
            session.routineNameSnapshot,
            set.muscleGroupNameSnapshot,
            set.exerciseNameSnapshot,
            (set.setIndex + 1).toString(),
            weight?.let(::formatDecimal).orEmpty(),
            reps?.toString().orEmpty(),
            e1rm?.let(::formatDecimal).orEmpty(),
        ).joinToString(",", transform = ::escape)
    }

    /** Sin depender del Locale: 82.5 → "82.5", 80.0 → "80". */
    fun formatDecimal(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    fun escape(field: String): String =
        if (field.indexOfAny(charsNeedingQuotes) >= 0) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
