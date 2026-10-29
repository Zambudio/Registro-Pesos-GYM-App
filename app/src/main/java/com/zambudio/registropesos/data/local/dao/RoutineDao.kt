package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zambudio.registropesos.data.local.entity.RoutineBlockEntity
import com.zambudio.registropesos.data.local.entity.RoutineEntity
import com.zambudio.registropesos.data.local.entity.RoutineExerciseEntity

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineEntity?

    @Query("SELECT * FROM routines ORDER BY sort_order, name")
    suspend fun getAll(): List<RoutineEntity>

    @Query("SELECT * FROM routine_blocks WHERE routine_id = :routineId ORDER BY sort_order")
    suspend fun getBlocks(routineId: String): List<RoutineBlockEntity>

    @Query(
        """
        SELECT re.* FROM routine_exercises re
        JOIN routine_blocks b ON b.id = re.block_id
        WHERE b.routine_id = :routineId
        ORDER BY b.sort_order, re.sort_order
        """,
    )
    suspend fun getRoutineExercises(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_blocks ORDER BY routine_id, sort_order")
    suspend fun getAllBlocks(): List<RoutineBlockEntity>

    @Query("SELECT * FROM routine_exercises ORDER BY block_id, sort_order")
    suspend fun getAllRoutineExercises(): List<RoutineExerciseEntity>

    @Insert
    suspend fun insert(entity: RoutineEntity)

    @Update
    suspend fun update(entity: RoutineEntity): Int

    @Insert
    suspend fun insertBlock(entity: RoutineBlockEntity)

    @Update
    suspend fun updateBlock(entity: RoutineBlockEntity): Int

    @Insert
    suspend fun insertRoutineExercise(entity: RoutineExerciseEntity)

    @Update
    suspend fun updateRoutineExercise(entity: RoutineExerciseEntity): Int

    // Update-then-insert en vez de REPLACE: REPLACE borraría la fila y arrastraría los CASCADE.
    @Transaction
    suspend fun save(entity: RoutineEntity) {
        if (update(entity) == 0) insert(entity)
    }

    @Transaction
    suspend fun saveBlock(entity: RoutineBlockEntity) {
        if (updateBlock(entity) == 0) insertBlock(entity)
    }

    @Transaction
    suspend fun saveRoutineExercise(entity: RoutineExerciseEntity) {
        if (updateRoutineExercise(entity) == 0) insertRoutineExercise(entity)
    }

    @Query(
        """
        DELETE FROM routine_exercises
        WHERE block_id IN (SELECT id FROM routine_blocks WHERE routine_id = :routineId)
            AND id NOT IN (:keepIds)
        """,
    )
    suspend fun deleteRoutineExercisesNotIn(routineId: String, keepIds: List<String>)

    @Query("DELETE FROM routine_blocks WHERE routine_id = :routineId AND id NOT IN (:keepIds)")
    suspend fun deleteBlocksNotIn(routineId: String, keepIds: List<String>)

    @Query("UPDATE routines SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
