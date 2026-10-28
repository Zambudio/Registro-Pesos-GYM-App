package com.zambudio.registropesos.core.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Formato de presentación en español (coma decimal). Para CSV/JSON se usa punto: no usar esto allí.

private val SpanishLocale: Locale = Locale.forLanguageTag("es-ES")
private val KgFormat = DecimalFormat("#,##0.##", DecimalFormatSymbols(SpanishLocale))
private val DateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(SpanishLocale)
private val DateTimeFormatterShort = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", SpanishLocale)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm", SpanishLocale)

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

/** 82.5 -> "82,5"; 80.0 -> "80". */
fun formatKg(value: Double): String = KgFormat.format(value)

/** 82.5 -> "82,5 kg". */
fun formatKgWithUnit(value: Double): String = "${formatKg(value)} kg"

/** Duración en ms -> "1 h 05 min" o "45 min". */
fun formatDuration(millis: Long): String {
    val totalMinutes = (millis / MILLIS_PER_MINUTE).coerceAtLeast(0)
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) "$hours h ${minutes.toString().padStart(2, '0')} min" else "$minutes min"
}

fun formatDate(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatDateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    DateTimeFormatterShort.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun formatTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TimeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/** Acepta "82,5" o "82.5"; null si no es un número válido. */
fun parseDecimalInput(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
