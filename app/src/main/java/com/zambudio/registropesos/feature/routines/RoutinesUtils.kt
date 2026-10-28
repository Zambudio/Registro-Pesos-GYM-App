package com.zambudio.registropesos.feature.routines

import kotlinx.coroutines.CancellationException

/** runCatching que no se traga la cancelación de la corrutina. */
internal inline fun <T> runCatchingNonCancellation(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }

/**
 * Mueve el elemento de [index] una posición arriba (offset -1) o abajo (+1).
 * Fuera de rango devuelve la misma instancia sin cambios.
 */
internal fun <T> List<T>.moved(index: Int, offset: Int): List<T> {
    val target = index + offset
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
