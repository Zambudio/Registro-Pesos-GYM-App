package com.zambudio.registropesos.core.di

import javax.inject.Qualifier

/** Dispatcher para E/S bloqueante (ficheros, bitmaps). Room ya gestiona sus propios hilos. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Scope de proceso (SupervisorJob + IO) para trabajo que debe terminar aunque se cierre la pantalla
 * que lo lanzó (p. ej. escrituras ya encoladas). Nunca se cancela.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
