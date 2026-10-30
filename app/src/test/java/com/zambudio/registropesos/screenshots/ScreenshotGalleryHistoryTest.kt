package com.zambudio.registropesos.screenshots

import com.zambudio.registropesos.feature.history.detail.HistoryExerciseUi
import com.zambudio.registropesos.feature.history.detail.HistorySetUi
import com.zambudio.registropesos.feature.history.detail.SessionDetailScreen
import com.zambudio.registropesos.feature.history.detail.SessionDetailUiState
import com.zambudio.registropesos.feature.history.list.HistoryItemUi
import com.zambudio.registropesos.feature.history.list.HistoryScreen
import com.zambudio.registropesos.feature.history.list.HistoryUiState
import org.junit.Test

/** Galería del historial: lista agrupada por meses y detalle de una sesión. */
class ScreenshotGalleryHistoryTest : ScreenshotGallery() {

    private val day = 86_400_000L
    private val oct29 = 1_793_295_000_000L // 29/10/2026, 18:30 aprox.

    @Test
    fun historyList() = snap("history-list") {
        HistoryScreen(
            state = HistoryUiState.Content(
                listOf(
                    HistoryItemUi("s1", "Día 1 · Empuje", oct29, 3_900_000L, 6, 8_450.0),
                    HistoryItemUi("s2", "Día 3 · Pierna", oct29 - 2 * day, 4_500_000L, 5, 12_380.5),
                    HistoryItemUi("s3", "Día 2 · Tirón", oct29 - 4 * day, 3_300_000L, 6, 7_120.0),
                    HistoryItemUi("s4", "Día 1 · Empuje", oct29 - 32 * day, null, 4, 5_960.0),
                    HistoryItemUi("s5", "Hombro y core", oct29 - 35 * day, 2_700_000L, 5, 3_210.0),
                ),
            ),
            onBack = {},
            onOpenSession = {},
            onRetry = {},
        )
    }

    @Test
    fun sessionDetail() = snap("session-detail") {
        SessionDetailScreen(
            state = SessionDetailUiState.Content(
                routineName = "Día 1 · Empuje",
                startedAt = oct29,
                durationMs = 3_900_000L,
                volumeKg = 8_450.0,
                exercises = listOf(
                    HistoryExerciseUi(
                        "e1",
                        "Press banca con barra",
                        "Pecho",
                        listOf(
                            HistorySetUi(1, 80.0, 10, 106.7),
                            HistorySetUi(2, 85.0, 8, 107.7),
                            HistorySetUi(3, 87.5, 6, 105.0),
                        ),
                        2_005.0,
                    ),
                    HistoryExerciseUi(
                        "e2",
                        "Press inclinado con mancuernas",
                        "Pecho",
                        listOf(HistorySetUi(1, 30.0, 12, 42.0), HistorySetUi(2, 32.5, 10, 43.3)),
                        685.0,
                    ),
                    HistoryExerciseUi(
                        "e3",
                        "Extensión de tríceps en polea",
                        "Tríceps",
                        listOf(HistorySetUi(1, 25.0, 15, 37.5), HistorySetUi(2, null, 12, 0.0)),
                        375.0,
                    ),
                ),
            ),
            onBack = {},
            onDelete = {},
            onRetry = {},
        )
    }

    @Test
    fun historyEmpty() = snap("history-empty") {
        HistoryScreen(state = HistoryUiState.Content(emptyList()), onBack = {}, onOpenSession = {}, onRetry = {})
    }
}
