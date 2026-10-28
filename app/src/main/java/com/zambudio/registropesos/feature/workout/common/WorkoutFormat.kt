package com.zambudio.registropesos.feature.workout.common

import com.zambudio.registropesos.core.util.parseDecimalInput
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToLong

// Utilidades de presentación y de llamadas seguras compartidas por feature/workout y feature/history.

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MILLIS_PER_SECOND = 1_000L
private const val CENTS = 100.0

// Sin separador de miles: el texto se vuelve a leer con parseDecimalInput ("1.000" sería 1,0).
private val InputKgFormat = DecimalFormat("0.##", DecimalFormatSymbols(Locale.forLanguageTag("es-ES")))

const val MAX_WEIGHT_KG = 1_000.0
const val MAX_REPS = 999

/** Peso tecleado ("82,5" o "82.5"); null si está vacío, no es un número o está fuera de 0..1000. */
fun parseWeightText(text: String): Double? =
    if (text.isBlank()) null else parseDecimalInput(text)?.takeIf { it in 0.0..MAX_WEIGHT_KG }

/** Repeticiones tecleadas; null si está vacío, no es un entero o está fuera de 0..999. */
fun parseRepsText(text: String): Int? =
    if (text.isBlank()) null else text.trim().toIntOrNull()?.takeIf { it in 0..MAX_REPS }

/** Peso para un campo editable: 82.5 -> "82,5", 80.0 -> "80", null -> "". */
fun formatWeightInput(weightKg: Double?): String = weightKg?.let { InputKgFormat.format(it) }.orEmpty()

/** Cronómetro: "05:09" o, a partir de una hora, "1:05:09". */
fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val mmss = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    return if (hours > 0) "$hours:$mmss" else mmss
}

/** Redondea a céntimos de kg para que sumar pasos (2,5; 1,25) no arrastre errores de coma flotante. */
fun roundKg(kg: Double): Double = (kg * CENTS).roundToLong() / CENTS

/**
 * Como runCatching pero sin tragarse la cancelación de la corrutina. Los repositorios pueden
 * lanzar cualquier excepción de SQLite/IO y la pantalla debe sobrevivir mostrando un aviso.
 */
@Suppress("TooGenericExceptionCaught")
suspend fun <T> suspendRunCatching(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

/** Aplana un Result<Result<T>> (casos de uso que ya devuelven Result envueltos en suspendRunCatching). */
fun <T> Result<Result<T>>.flatten(): Result<T> = getOrElse { Result.failure(it) }
