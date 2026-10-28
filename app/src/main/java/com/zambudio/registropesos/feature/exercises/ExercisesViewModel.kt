package com.zambudio.registropesos.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

data class ExerciseListItem(
    val id: String,
    val name: String,
    val imageFile: File?,
    val isArchived: Boolean,
)

/** Ejercicios de un grupo muscular, en el orden del grupo. */
data class ExerciseSection(
    val groupId: String,
    val groupName: String,
    val exercises: List<ExerciseListItem>,
)

data class ExercisesUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val showArchived: Boolean = false,
    val sections: List<ExerciseSection> = emptyList(),
    /** Grupos para el selector de "Añadir ejercicio". */
    val groups: List<MuscleGroupOption> = emptyList(),
    /** No hay ningún ejercicio que mostrar antes de aplicar la búsqueda. */
    val isLibraryEmpty: Boolean = false,
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val muscleGroupRepository: MuscleGroupRepository,
    private val imageStore: ImageStore,
) : ViewModel() {

    // La búsqueda va aparte del estado combinado para que el TextField se actualice en el acto.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val showArchived = MutableStateFlow(false)
    private val retryTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ExercisesUiState> = retryTrigger
        .flatMapLatest {
            val exercises = showArchived.flatMapLatest { exerciseRepository.observeAll(includeArchived = it) }
            combine(muscleGroupRepository.observeAll(), exercises, showArchived, _query, ::buildState)
                .catch { emit(ExercisesUiState(isLoading = false, isError = true, showArchived = showArchived.value)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExercisesUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onShowArchivedChange(show: Boolean) {
        showArchived.value = show
    }

    fun onRetry() {
        retryTrigger.update { it + 1 }
    }

    private fun buildState(
        groups: List<MuscleGroup>,
        exercises: List<Exercise>,
        showArchived: Boolean,
        query: String,
    ) = ExercisesUiState(
        isLoading = false,
        showArchived = showArchived,
        sections = groupExercises(groups, exercises, query) { imageStore.fileOrNull(it) },
        groups = groups.sortedBy { it.sortOrder }.map { it.toOption() },
        isLibraryEmpty = exercises.isEmpty(),
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/**
 * Agrupa por grupo muscular (en su sortOrder) los ejercicios cuyo nombre contiene [query]
 * sin distinguir mayúsculas ni acentos. Omite los grupos sin coincidencias.
 */
internal fun groupExercises(
    groups: List<MuscleGroup>,
    exercises: List<Exercise>,
    query: String,
    resolveImage: (String?) -> File?,
): List<ExerciseSection> {
    val needle = normalizeForSearch(query)
    val byGroup = exercises
        .filter { needle.isEmpty() || normalizeForSearch(it.name).contains(needle) }
        .groupBy { it.muscleGroupId }
    return groups
        .sortedBy { it.sortOrder }
        .mapNotNull { group ->
            val matches = byGroup[group.id].orEmpty()
            if (matches.isEmpty()) {
                null
            } else {
                ExerciseSection(
                    groupId = group.id,
                    groupName = group.name,
                    exercises = matches
                        .sortedBy { normalizeForSearch(it.name) }
                        .map { ExerciseListItem(it.id, it.name, resolveImage(it.imagePath), it.isArchived) },
                )
            }
        }
}
