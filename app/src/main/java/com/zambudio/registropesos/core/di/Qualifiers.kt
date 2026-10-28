package com.zambudio.registropesos.core.di

import javax.inject.Qualifier

/** Dispatcher para E/S bloqueante (ficheros, bitmaps). Room ya gestiona sus propios hilos. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
