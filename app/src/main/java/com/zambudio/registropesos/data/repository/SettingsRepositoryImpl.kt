package com.zambudio.registropesos.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.ThemeMode
import com.zambudio.registropesos.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/** Ajustes en DataStore Preferences (fichero filesDir/datastore/settings.preferences_pb). */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs.write(updated)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            weightStepKg = this[WEIGHT_STEP_KG] ?: defaults.weightStepKg,
            defaultSets = this[DEFAULT_SETS] ?: defaults.defaultSets,
            // Un valor desconocido (p. ej. de una versión futura) vuelve al de por defecto.
            themeMode = ThemeMode.entries.firstOrNull { it.name == this[THEME_MODE] } ?: defaults.themeMode,
        )
    }

    private fun MutablePreferences.write(settings: AppSettings) {
        this[WEIGHT_STEP_KG] = settings.weightStepKg
        this[DEFAULT_SETS] = settings.defaultSets
        this[THEME_MODE] = settings.themeMode.name
    }

    companion object {
        const val FILE_NAME = "settings"
        private val WEIGHT_STEP_KG = doublePreferencesKey("weight_step_kg")
        private val DEFAULT_SETS = intPreferencesKey("default_sets")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
