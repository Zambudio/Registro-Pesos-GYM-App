package com.zambudio.registropesos.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreUiState(
    val isLoading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val isError: Boolean = false,
)

@HiltViewModel
class MoreViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<MoreUiState> = settingsRepository.settings
        .map { MoreUiState(isLoading = false, settings = it) }
        .catch { emit(MoreUiState(isLoading = false, isError = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreUiState())

    fun setWeightStep(stepKg: Double) {
        if (stepKg !in WEIGHT_STEPS_KG) return
        viewModelScope.launch { settingsRepository.update { it.copy(weightStepKg = stepKg) } }
    }

    fun setDefaultSets(sets: Int) {
        if (sets !in DEFAULT_SETS_RANGE) return
        viewModelScope.launch { settingsRepository.update { it.copy(defaultSets = sets) } }
    }

    companion object {
        val WEIGHT_STEPS_KG = listOf(1.0, 1.25, 2.5, 5.0)
        val DEFAULT_SETS_RANGE = 1..10
    }
}
