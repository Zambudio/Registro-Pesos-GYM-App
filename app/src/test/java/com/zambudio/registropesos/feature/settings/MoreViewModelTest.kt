package com.zambudio.registropesos.feature.settings

import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.ThemeMode
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.feature.progress.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoreViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository()

    private fun TestScope.viewModel(): MoreViewModel {
        val viewModel = MoreViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
        return viewModel
    }

    @Test
    fun `exposes stored settings`() = runTest {
        repository.current.value = AppSettings(weightStepKg = 1.25, defaultSets = 3)

        val state = viewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(AppSettings(1.25, 3), state.settings)
    }

    @Test
    fun `weight step accepts only the offered values`() = runTest {
        val viewModel = viewModel()

        viewModel.setWeightStep(5.0)
        assertEquals(5.0, viewModel.state.value.settings.weightStepKg, 0.0)

        viewModel.setWeightStep(3.0)
        assertEquals(5.0, viewModel.state.value.settings.weightStepKg, 0.0)
        assertEquals(1, repository.updates)
    }

    @Test
    fun `default sets stay between 1 and 10`() = runTest {
        val viewModel = viewModel()

        viewModel.setDefaultSets(10)
        viewModel.setDefaultSets(11)
        viewModel.setDefaultSets(0)
        assertEquals(10, viewModel.state.value.settings.defaultSets)

        viewModel.setDefaultSets(1)
        assertEquals(1, viewModel.state.value.settings.defaultSets)
        assertEquals(2, repository.updates)
    }

    @Test
    fun `theme and rest settings are stored`() = runTest {
        val viewModel = viewModel()

        viewModel.setThemeMode(ThemeMode.DARK)
        viewModel.setRestSeconds(120)
        viewModel.setRestSeconds(100)
        viewModel.setRestAutoStart(false)

        val settings = viewModel.state.value.settings
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals("solo duraciones ofrecidas", 120, settings.restSeconds)
        assertFalse(settings.restAutoStart)
        assertEquals(3, repository.updates)
    }
}

private class FakeSettingsRepository : SettingsRepository {
    val current = MutableStateFlow(AppSettings())
    var updates = 0
    override val settings: Flow<AppSettings> = current

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        updates++
        current.update(transform)
    }
}
