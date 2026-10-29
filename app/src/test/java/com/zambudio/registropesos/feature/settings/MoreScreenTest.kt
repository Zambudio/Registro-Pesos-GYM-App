package com.zambudio.registropesos.feature.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MoreScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun entriesNavigateAndSettingsNotify() {
        val opened = mutableListOf<String>()
        var step: Double? = null
        var sets: Int? = null
        var theme: ThemeMode? = null
        var rest: Int? = null
        var auto: Boolean? = null
        compose.setContent {
            GymTheme {
                MoreScreen(
                    state = MoreUiState(isLoading = false, settings = AppSettings(weightStepKg = 2.5, defaultSets = 4)),
                    onOpenBackup = { opened += "backup" },
                    onOpenMuscleGroups = { opened += "groups" },
                    onOpenHistory = { opened += "history" },
                    onOpenAbout = { opened += "about" },
                    onWeightStepSelected = { step = it },
                    onDefaultSetsChanged = { sets = it },
                    onThemeModeSelected = { theme = it },
                    onRestSecondsSelected = { rest = it },
                    onRestAutoStartChanged = { auto = it },
                )
            }
        }

        compose.onNodeWithText("Exportar / importar").performScrollTo().performClick()
        compose.onNodeWithText("Grupos musculares").performScrollTo().performClick()
        compose.onNodeWithText("Historial").performScrollTo().performClick()
        compose.onNodeWithText("Acerca de").performScrollTo().performClick()
        compose.onNodeWithText("1,25 kg").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Más series").performScrollTo().performClick()
        compose.onNodeWithText("Oscuro").performScrollTo().performClick()
        compose.onNodeWithText("2:00").performScrollTo().performClick()
        compose.onNodeWithText("Empezar al anotar una serie").performScrollTo().performClick()

        assertEquals(listOf("backup", "groups", "history", "about"), opened)
        assertEquals(1.25, step)
        assertEquals(5, sets)
        assertEquals(ThemeMode.DARK, theme)
        assertEquals(120, rest)
        assertEquals("el descanso automático viene activado: pulsar lo desactiva", false, auto)
    }

    @Test
    fun aboutShowsVersionAndPrivacyNote() {
        compose.setContent { GymTheme { AboutScreen(versionName = "9.9.9", onBack = {}) } }

        compose.onNodeWithText("Versión 9.9.9").assertExists()
        compose.onNodeWithText("Privacidad").assertExists()
    }
}
