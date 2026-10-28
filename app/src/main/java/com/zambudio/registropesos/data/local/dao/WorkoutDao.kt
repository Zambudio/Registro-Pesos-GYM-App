package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zambudio.registropesos.data.local.entity.WorkoutSessionEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

// Las consultas comparan status con el nombre del enum (TypeConverter en core/db/Converters).
// SQLite 3.19 (API 26): sin window functions ni RETURNING.

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    fun observeInProgress(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY started_at DESC LIMIT 1")
    suspend fun getInProgress(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE status != 'IN_PROGRESS' ORDER BY started_at")
    suspend fun getSessionsExcludingInProgress(): List<WorkoutSessionEntity>

    @Query(
        """
        SELECT ws.* FROM workout_sets ws
        JOIN workout_sessions s ON s.id = ws.session_id
        WHERE s.status != 'IN_PROGRESS'
        ORDER BY ws.session_id, ws.exercise_order, ws.set_index
        """,
    )
    suspend fun getSetsExcludingInProgress(): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE session_id = :sessionId ORDER BY exercise_order, set_index")
    suspend fun getSets(sessionId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE id = :id")
    suspend fun getSet(id: String): WorkoutSetEntity?

    @Insert
    suspend fun insertSession(entity: WorkoutSessionEntity)

    @Update
    suspend fun updateSession(entity: WorkoutSessionEntity): Int

    @Insert
    suspend fun insertSet(entity: WorkoutSetEntity)

    @Insert
    suspend fun insertSets(entities: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(entity: WorkoutSetEntity): Int

    /** Update y, si no existía, insert: un choque con el índice único lanza en vez de perderse. */
    @Transaction
    suspend fun saveSet(entity: WorkoutSetEntity) {
        if (updateSet(entity) == 0) insertSet(entity)
    }

    @Transaction
    suspend fun saveSession(entity: WorkoutSessionEntity) {
        if (updateSession(entity) == 0) insertSession(entity)
    }

    @Query("UPDATE workout_sessions SET updated_at = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: String, updatedAt: Long)

    @Query(
        """
        UPDATE workout_sessions
        SET status = 'FINISHED', finished_at = :finishedAt, updated_at = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markFinished(id: String, finishedAt: Long, updatedAt: Long): Int

    @Query("DELETE FROM workout_sets WHERE session_id = :sessionId AND id NOT IN (:keepIds)")
    suspend fun deleteSetsNotIn(sessionId: String, keepIds: List<String>)

    @Query("DELETE FROM workout_sets WHERE session_id = :sessionId")
    suspend fun deleteSetsOf(sessionId: String)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String): Int

    /** Series de la última sesión FINISHED (por started_at) con el ejercicio, excluyendo una. */
    @Query(
        """
        SELECT * FROM workout_sets
        WHERE exercise_id = :exerciseId AND session_id = (
            SELECT s.id FROM workout_sessions s
            WHERE s.status = 'FINISHED'
                AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
                AND EXISTS (
                    SELECT 1 FROM workout_sets x WHERE x.session_id = s.id AND x.exercise_id = :exerciseId
                )
            ORDER BY s.started_at DESC
            LIMIT 1
        )
        ORDER BY set_index
        """,
    )
    suspend fun lastFinishedSetsForExercise(exerciseId: String, excludeSessionId: String?): List<WorkoutSetEntity>

    @Query(
        """
        SELECT s.id AS id, s.routine_name_snapshot AS routineName, s.started_at AS startedAt,
            s.finished_at AS finishedAt,
            COUNT(DISTINCT ws.exercise_id) AS exerciseCount,
            COALESCE(SUM(ws.weight_kg * ws.reps), 0.0) AS volumeKg
        FROM workout_sessions s
        LEFT JOIN workout_sets ws ON ws.session_id = s.id
        WHERE s.status = 'FINISHED'
        GROUP BY s.id
        ORDER BY s.started_at DESC
        """,
    )
    fun observeFinishedSummaries(): Flow<List<SessionSummaryRow>>
}
