package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Solo cuentan series de sesiones FINISHED con reps > 0 y peso no nulo.
// e1RM por Epley: peso × (1 + reps/30); el redondeo lo aplica el dominio.

private const val FLAT_SETS_QUERY = """
    SELECT ws.exercise_id AS exerciseId, ws.session_id AS sessionId, ws.weight_kg AS weightKg,
        ws.reps AS reps, s.started_at AS startedAt
    FROM workout_sets ws
    JOIN workout_sessions s ON s.id = ws.session_id
    WHERE s.status = 'FINISHED'
        AND ws.reps > 0
        AND ws.weight_kg IS NOT NULL
        AND (:exerciseId IS NULL OR ws.exercise_id = :exerciseId)
    ORDER BY s.started_at, ws.exercise_order, ws.set_index
"""

@Dao
interface ProgressDao {
    @Query(
        """
        SELECT s.id AS sessionId, s.started_at AS startedAt,
            MAX(ws.weight_kg) AS maxWeightKg,
            SUM(ws.weight_kg * ws.reps) AS volumeKg,
            MAX(ws.weight_kg * (1 + ws.reps / 30.0)) AS bestE1rmKg
        FROM workout_sets ws
        JOIN workout_sessions s ON s.id = ws.session_id
        WHERE ws.exercise_id = :exerciseId
            AND s.status = 'FINISHED'
            AND s.started_at >= :fromMillis
            AND ws.reps > 0
            AND ws.weight_kg IS NOT NULL
        GROUP BY s.id
        ORDER BY s.started_at
        """,
    )
    fun observeExercisePoints(exerciseId: String, fromMillis: Long): Flow<List<ExerciseSessionPointRow>>

    @Query(
        """
        SELECT s.id AS sessionId, s.started_at AS startedAt, SUM(ws.weight_kg * ws.reps) AS volumeKg
        FROM workout_sets ws
        JOIN workout_sessions s ON s.id = ws.session_id
        JOIN exercises e ON e.id = ws.exercise_id
        WHERE e.muscle_group_id = :groupId
            AND s.status = 'FINISHED'
            AND s.started_at >= :fromMillis
            AND ws.reps > 0
            AND ws.weight_kg IS NOT NULL
        GROUP BY s.id
        ORDER BY s.started_at
        """,
    )
    fun observeGroupSessionVolumes(groupId: String, fromMillis: Long): Flow<List<SessionVolumeRow>>

    @Query(FLAT_SETS_QUERY)
    fun observeFinishedSetsFlat(exerciseId: String?): Flow<List<FlatSetRow>>

    @Query(FLAT_SETS_QUERY)
    suspend fun finishedSetsFlat(exerciseId: String?): List<FlatSetRow>
}
