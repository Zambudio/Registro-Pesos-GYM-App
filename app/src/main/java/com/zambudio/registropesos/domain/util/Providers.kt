package com.zambudio.registropesos.domain.util

/** Hora del sistema inyectable (los tests usan un reloj fijo). */
fun interface TimeProvider {
    fun now(): Long
}

/** Generador de UUID inyectable. */
fun interface IdGenerator {
    fun newId(): String
}
