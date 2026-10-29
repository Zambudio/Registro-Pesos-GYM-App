package com.zambudio.registropesos.screenshots

import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.RestTimerState
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.usecase.GhostSet
import com.zambudio.registropesos.domain.usecase.NewRecord
import com.zambudio.registropesos.domain.usecase.RecordType
import com.zambudio.registropesos.feature.settings.MoreScreen
import com.zambudio.registropesos.feature.settings.MoreUiState
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailScreen
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailUiState
import com.zambudio.registropesos.feature.workout.session.ExercisePickerContent
import com.zambudio.registropesos.feature.workout.session.ExercisePickerUi
import com.zambudio.registropesos.feature.workout.session.PickerExerciseUi
import com.zambudio.registropesos.feature.workout.session.WorkoutActions
import com.zambudio.registropesos.feature.workout.session.WorkoutBlockUi
import com.zambudio.registropesos.feature.workout.session.WorkoutExerciseUi
import com.zambudio.registropesos.feature.workout.session.WorkoutScreen
import com.zambudio.registropesos.feature.workout.session.WorkoutSetUi
import com.zambudio.registropesos.feature.workout.session.WorkoutUiState
import com.zambudio.registropesos.feature.workout.summary.WorkoutSummaryScreen
import com.zambudio.registropesos.feature.workout.summary.WorkoutSummaryUiState
import com.zambudio.registropesos.feature.workout.train.InProgressSessionUi
import com.zambudio.registropesos.feature.workout.train.TrainScreen
import com.zambudio.registropesos.feature.workout.train.TrainUiState
import org.junit.Test

/** Capturas de Entrenar, rutina, sesión (con y sin descanso), selector, resumen y Más. */
class ScreenshotGalleryWorkoutTest : ScreenshotGallery() {

    private val now = System.currentTimeMillis()

    @Test
    fun train() = snap("workout-train") {
        TrainScreen(
            state = TrainUiState.Content(
                inProgress = InProgressSessionUi("s1", "Día 1 · Pecho y tríceps", now - 25 * 60_000L),
                routines = listOf(
                    RoutineSummary("r1", "Día 1", "Pecho: 4 ejercicios · Tríceps: 2", listOf("Pecho", "Tríceps")),
                    RoutineSummary("r2", "Día 2", "Tirón: prioriza el remo", listOf("Espalda", "Bíceps")),
                    RoutineSummary("r3", "Día 3", null, listOf("Pierna")),
                    RoutineSummary("r4", "Día 4", null, listOf("Hombros", "Deltoides posterior", "Abdomen")),
                ),
            ),
            onOpenRoutine = {},
            onResumeSession = {},
            onDiscardSession = {},
            onOpenHistory = {},
            onCreateRoutine = {},
            onRetry = {},
        )
    }

    @Test
    fun routineDetail() = snap("workout-routine-detail") {
        val chest = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
        val triceps = MuscleGroup("g2", "Tríceps", 1, true, 0L, 0L)
        val abs = MuscleGroup("g3", "Abdomen", 2, true, 0L, 0L)
        val detail = RoutineDetail(
            routine = Routine("r1", "Día 1", "Pecho: 4 ejercicios · Tríceps: 2 · Abdomen al final", 0, 0L, 0L),
            blocks = listOf(chest, triceps, abs).mapIndexed { i, group ->
                RoutineBlockDetail(RoutineBlock("b$i", "r1", group.id, i, 0L, 0L), group, emptyList())
            },
        )
        RoutineDetailScreen(
            state = RoutineDetailUiState.Content(detail),
            onBack = {},
            onEditRoutine = {},
            onStart = {},
            onGoToInProgress = {},
            onDismissConflict = {},
            onRetry = {},
        )
    }

    private val sets = listOf(
        WorkoutSetUi("s1", 1, 80.0, 8, 77.5, 8),
        WorkoutSetUi("s2", 2, 82.5, 6, 77.5, 8),
        WorkoutSetUi("s3", 3, null, null, 75.0, 6),
        WorkoutSetUi("s4", 4, null, null, null, null),
    )

    private fun session(rest: RestTimerState?, picker: ExercisePickerUi? = null) = WorkoutUiState.Content(
        sessionId = "s",
        routineName = "Día 1",
        startedAt = now - 32 * 60_000L,
        reminder = "Pecho: 4 ejercicios · Tríceps: 2 ejercicios",
        blocks = listOf(
            WorkoutBlockUi(
                "g1",
                "Pecho",
                "pecho",
                listOf(
                    WorkoutExerciseUi(
                        "e1",
                        "Press de banca plano con barra",
                        sets,
                        listOf(GhostSet(0, 77.5, 8), GhostSet(1, 77.5, 8), GhostSet(2, 75.0, 6)),
                    ),
                    WorkoutExerciseUi("e2", "Press inclinado con mancuernas", sets.take(2), emptyList()),
                ),
            ),
            WorkoutBlockUi("g2", "Tríceps", "triceps", emptyList()),
        ),
        expandedExerciseId = "e1",
        weightStepKg = 2.5,
        hasAnyData = true,
        isClosing = false,
        picker = picker,
        rest = rest,
        restDefaultSeconds = 90,
    )

    @Test
    fun session() = snap("workout-session") {
        WorkoutScreen(session(rest = null), WorkoutActions(), onBack = {}, onFinish = {}, onDiscard = {}, onRetry = {})
    }

    @Test
    fun sessionResting() = snap("workout-session-rest") {
        WorkoutScreen(
            session(rest = RestTimerState(endsAt = now + 72_000L, durationMillis = 90_000L)),
            WorkoutActions(),
            onBack = {},
            onFinish = {},
            onDiscard = {},
            onRetry = {},
        )
    }

    @Test
    fun picker() = snap("workout-picker") {
        val names = listOf(
            "Press de banca plano con barra",
            "Press inclinado con mancuernas",
            "Press de pecho en máquina",
            "Cruces de poleas",
            "Aperturas en máquina contractora",
            "Fondos orientados al pecho",
        )
        ExercisePickerContent(
            picker = ExercisePickerUi(
                muscleGroupId = "pecho",
                muscleGroupName = "Pecho",
                isLoading = false,
                exercises = names.mapIndexed { i, name -> PickerExerciseUi("p$i", name, null, alreadyAdded = i == 0) },
            ),
            onPick = {},
            onCreateExercise = {},
            onDismiss = {},
        )
    }

    @Test
    fun summary() = snap("workout-summary") {
        WorkoutSummaryScreen(
            state = WorkoutSummaryUiState.Content(
                routineName = "Día 1",
                durationMs = 62 * 60_000L,
                exerciseCount = 6,
                setCount = 22,
                volumeKg = 12_480.0,
                newRecords = listOf(
                    NewRecord("e1", "Press de banca plano con barra", RecordType.WEIGHT, 80.0, 85.0),
                    NewRecord("e2", "Press inclinado con mancuernas", RecordType.E1RM, 36.0, 38.5),
                ),
            ),
            onDone = {},
            onRetry = {},
        )
    }

    @Test
    fun more() = snap("settings-more") {
        MoreScreen(
            state = MoreUiState(isLoading = false, settings = AppSettings()),
            onOpenBackup = {},
            onOpenMuscleGroups = {},
            onOpenHistory = {},
            onOpenAbout = {},
            onWeightStepSelected = {},
            onDefaultSetsChanged = {},
            onThemeModeSelected = {},
            onRestSecondsSelected = {},
            onRestAutoStartChanged = {},
        )
    }
}
