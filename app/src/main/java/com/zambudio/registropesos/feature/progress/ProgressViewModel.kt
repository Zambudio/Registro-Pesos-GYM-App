package com.zambudio.registropesos.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.usecase.PersonalRecords
import com.zambudio.registropesos.domain.usecase.ProgressRange
import com.zambudio.registropesos.domain.usecase.WeeklyVolumeByMuscleGroup
import com.zambudio.registropesos.domain.usecase.WeeklyVolumePoint
import com.zambudio.registropesos.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId
import javax.inject.Inject

/**
 * Progreso por ejercicio (máximo, volumen y e1RM por sesión + récords) y por grupo muscular
 * (volumen y sesiones por semana). Todo reacciona a la BD: terminar un entrenamiento actualiza
 * las gráficas sin recargar.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val muscleGroupRepository: MuscleGroupRepository,
    exerciseRepository: ExerciseRepository,
    private val progressRepository: ProgressRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val zone: ZoneId = ZoneId.systemDefault()

    private data class Selection(
        val tab: ProgressTab = ProgressTab.EXERCISE,
        val range: ProgressRange = ProgressUiState.DEFAULT_RANGE,
        val exerciseGroupId: String? = null,
        val exerciseId: String? = null,
        val chartGroupId: String? = null,
    )

    private data class Catalog(
        val groups: List<MuscleGroup>,
        val exercises: Map<String, Exercise>,
        val records: Map<String, PersonalRecord>,
    ) {
        val firstGroupId: String? get() = groups.firstOrNull()?.id
    }

    private val selection = MutableStateFlow(Selection())
    private val retryTrigger = MutableStateFlow(0)

    private val catalog: Flow<Catalog> = combine(
        muscleGroupRepository.observeAll(),
        exerciseRepository.observeAll(includeArchived = true),
        progressRepository.observeFinishedSetsFlat(null),
    ) { groups, exercises, sets ->
        Catalog(
            groups = groups.sortedBy { it.sortOrder },
            exercises = exercises.associateBy { it.id },
            records = PersonalRecords(sets),
        )
    }

    private val exercisePoints: Flow<List<ExerciseSessionPoint>> = selection
        .map { it.exerciseId to it.range }
        .distinctUntilChanged()
        .flatMapLatest { (exerciseId, range) ->
            if (exerciseId == null) {
                flowOf(emptyList())
            } else {
                progressRepository.observeExercisePoints(exerciseId, range.fromMillis(time.now(), zone))
            }
        }

    /** Solo consulta la BD con la pestaña "Por grupo" visible. Sin grupo elegido, el primero. */
    private val weekly: Flow<List<WeeklyVolumePoint>> =
        combine(selection, muscleGroupRepository.observeAll()) { s, groups ->
            val firstGroupId = groups.minByOrNull { it.sortOrder }?.id
            val groupId = if (s.tab == ProgressTab.GROUP) s.chartGroupId ?: firstGroupId else null
            groupId to s.range
        }
            .distinctUntilChanged()
            .flatMapLatest { (groupId, range) ->
                if (groupId == null) {
                    flowOf(emptyList())
                } else {
                    val now = time.now()
                    progressRepository.observeGroupSessionVolumes(groupId, range.fromMillis(now, zone))
                        .map { WeeklyVolumeByMuscleGroup(it, zone, untilMillis = now) }
                }
            }

    val state: StateFlow<ProgressUiState> = retryTrigger
        .flatMapLatest {
            combine(selection, catalog, exercisePoints, weekly, ::buildState)
                .catch { emit(ProgressUiState(isLoading = false, isError = true)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun selectTab(tab: ProgressTab) = selection.update { it.copy(tab = tab) }

    fun selectRange(range: ProgressRange) = selection.update { it.copy(range = range) }

    /** Cambiar de grupo descarta el ejercicio elegido si no pertenece al nuevo grupo. */
    fun selectExerciseGroup(groupId: String) = selection.update { current ->
        if (current.exerciseGroupId == groupId) current else current.copy(exerciseGroupId = groupId, exerciseId = null)
    }

    fun selectExercise(exerciseId: String) = selection.update { it.copy(exerciseId = exerciseId) }

    /** Desde una tarjeta de récord: elige el ejercicio y su grupo. */
    fun openExercise(exerciseId: String, muscleGroupId: String) = selection.update {
        it.copy(tab = ProgressTab.EXERCISE, exerciseGroupId = muscleGroupId, exerciseId = exerciseId)
    }

    fun clearExercise() = selection.update { it.copy(exerciseId = null) }

    fun selectChartGroup(groupId: String) = selection.update { it.copy(chartGroupId = groupId) }

    fun retry() = retryTrigger.update { it + 1 }

    private fun buildState(
        s: Selection,
        c: Catalog,
        points: List<ExerciseSessionPoint>,
        weekly: List<WeeklyVolumePoint>,
    ): ProgressUiState {
        val selectedExercise = s.exerciseId?.let(c.exercises::get)
        val exerciseGroupId = s.exerciseGroupId ?: selectedExercise?.muscleGroupId ?: c.firstGroupId
        val options = c.exercises.values
            .filter { it.muscleGroupId == exerciseGroupId && (!it.isArchived || it.id in c.records) }
            .sortedWith(compareBy({ it.isArchived }, { it.name.lowercase() }))
            .map { it.toOption() }
        return ProgressUiState(
            isLoading = false,
            tab = s.tab,
            range = s.range,
            groups = c.groups,
            exerciseGroupId = exerciseGroupId,
            exercises = options,
            selectedExercise = selectedExercise?.toOption(),
            exercisePoints = if (selectedExercise != null) points else emptyList(),
            selectedRecord = selectedExercise?.let { c.records[it.id] },
            records = recordCards(c),
            chartGroupId = s.chartGroupId ?: c.firstGroupId,
            weekly = weekly,
        )
    }

    private fun recordCards(c: Catalog): List<RecordCard> {
        val groupOrder = c.groups.withIndex().associate { (index, group) -> group.id to index }
        val groupNames = c.groups.associate { it.id to it.name }
        return c.records.values
            .mapNotNull { record ->
                val exercise = c.exercises[record.exerciseId] ?: return@mapNotNull null
                exercise to RecordCard(
                    exerciseId = exercise.id,
                    muscleGroupId = exercise.muscleGroupId,
                    exerciseName = exercise.name,
                    groupName = groupNames[exercise.muscleGroupId].orEmpty(),
                    record = record,
                )
            }
            .sortedWith(
                compareBy(
                    { (exercise, _) -> groupOrder[exercise.muscleGroupId] ?: Int.MAX_VALUE },
                    { (exercise, _) -> exercise.name.lowercase() },
                ),
            )
            .map { it.second }
    }

    private fun Exercise.toOption() = ExerciseOption(id, name, muscleGroupId, isArchived)
}
