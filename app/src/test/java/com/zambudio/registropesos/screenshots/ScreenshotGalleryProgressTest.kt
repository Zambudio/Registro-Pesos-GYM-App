package com.zambudio.registropesos.screenshots

import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.PersonalRecord
import com.zambudio.registropesos.domain.usecase.WeeklyVolumePoint
import com.zambudio.registropesos.feature.progress.ExerciseOption
import com.zambudio.registropesos.feature.progress.ProgressCallbacks
import com.zambudio.registropesos.feature.progress.ProgressScreen
import com.zambudio.registropesos.feature.progress.ProgressTab
import com.zambudio.registropesos.feature.progress.ProgressUiState
import com.zambudio.registropesos.feature.progress.RecordCard
import org.junit.Test
import java.time.LocalDate

/** Galería de progreso: récords, gráficas por ejercicio y por grupo. */
class ScreenshotGalleryProgressTest : ScreenshotGallery() {

    private val day = 86_400_000L
    private val now = 1_793_295_000_000L
    private val chest = MuscleGroup("g1", "Pecho", 0, true, 0L, 0L)
    private val back = MuscleGroup("g2", "Espalda", 1, true, 0L, 0L)
    private val bench = ExerciseOption("e1", "Press banca con barra", "g1", isArchived = false)

    @Test
    fun progressRecords() = snap("progress-records") {
        ProgressScreen(
            state = ProgressUiState(
                isLoading = false,
                groups = listOf(chest, back),
                exerciseGroupId = "g1",
                exercises = listOf(bench),
                records = listOf(
                    RecordCard("e1", "g1", "Press banca con barra", "Pecho", record(87.5, 107.7)),
                    RecordCard("e2", "g1", "Press inclinado con mancuernas", "Pecho", record(32.5, 43.3)),
                    RecordCard("e5", "g2", "Jalón al pecho agarre abierto", "Espalda", record(70.0, 88.7)),
                ),
            ),
            callbacks = ProgressCallbacks(),
        )
    }

    @Test
    fun progressExercise() = snap("progress-exercise") {
        val weights = listOf(75.0, 77.5, 77.5, 80.0, 82.5, 82.5, 85.0, 87.5)
        ProgressScreen(
            state = ProgressUiState(
                isLoading = false,
                groups = listOf(chest, back),
                exerciseGroupId = "g1",
                exercises = listOf(bench),
                selectedExercise = bench,
                selectedRecord = record(87.5, 107.7),
                exercisePoints = weights.mapIndexed { i, w ->
                    ExerciseSessionPoint("s$i", now - (weights.size - i) * 4 * day, w, w * 24, w * 1.25)
                },
            ),
            callbacks = ProgressCallbacks(),
        )
    }

    @Test
    fun progressGroup() = snap("progress-group") {
        val volumes = listOf(6_200.0, 7_450.0, 5_900.0, 8_100.0, 8_900.0, 7_700.0)
        ProgressScreen(
            state = ProgressUiState(
                isLoading = false,
                tab = ProgressTab.GROUP,
                groups = listOf(chest, back),
                chartGroupId = "g1",
                weekly = volumes.mapIndexed { i, v ->
                    val start = now - (volumes.size - i) * 7 * day
                    WeeklyVolumePoint(LocalDate.of(2026, 9, 1).plusWeeks(i.toLong()), start, v, 1 + i % 3)
                },
            ),
            callbacks = ProgressCallbacks(),
        )
    }

    @Test
    fun progressEmpty() = snap("progress-empty") {
        ProgressScreen(
            state = ProgressUiState(isLoading = false, groups = listOf(chest), exerciseGroupId = "g1"),
            callbacks = ProgressCallbacks(),
        )
    }

    private fun record(weight: Double, e1rm: Double) = PersonalRecord("e", weight, now - 3 * day, e1rm, now - 10 * day)
}
