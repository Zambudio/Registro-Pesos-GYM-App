package com.zambudio.registropesos

import android.app.Application
import android.util.Log
import com.zambudio.registropesos.core.di.ApplicationScope
import com.zambudio.registropesos.data.catalog.CatalogSeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GymApplication : Application() {
    @Inject lateinit var catalogSeeder: CatalogSeeder

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Catálogo de ejercicios incluido (idempotente, ADR-019). Si falla, la app sigue funcionando
        // y se reintenta en el siguiente arranque.
        applicationScope.launch {
            try {
                catalogSeeder.seed()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                Log.w(TAG, "No se pudo cargar el catálogo de ejercicios", error)
            }
        }
    }

    private companion object {
        const val TAG = "GymApplication"
    }
}
