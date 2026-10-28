package com.zambudio.registropesos.core.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Versión con argumento de `androidx.lifecycle.compose.dropUnlessResumed` (p. ej. abrir un elemento
 * por id): ignora la llamada si el destino ya no está en RESUMED, así un doble toque no navega dos
 * veces. Solo para navegaciones disparadas por el usuario; los eventos del ViewModel llegan en
 * STARTED y se perderían.
 */
@Composable
fun <T> dropUnlessResumedWith(block: (T) -> Unit): (T) -> Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    return { value ->
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) block(value)
    }
}
