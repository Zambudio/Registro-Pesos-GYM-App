package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MuscleGroupDao {
    @Query("SELECT * FROM muscle_groups ORDER BY sort_order, name")
    fun observeAll(): Flow<List<MuscleGroupEntity>>

    @Query("SELECT * FROM muscle_groups ORDER BY sort_order, name")
    suspend fun getAll(): List<MuscleGroupEntity>

    @Query("SELECT * FROM muscle_groups WHERE id = :id")
    suspend fun getById(id: String): MuscleGroupEntity?

    @Query("SELECT * FROM muscle_groups WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<MuscleGroupEntity>

    /** Nº de ejercicios no archivados por grupo; los grupos sin ejercicios aparecen con 0. */
    @Query(
        """
        SELECT g.id AS groupId, COUNT(e.id) AS count
        FROM muscle_groups g
        LEFT JOIN exercises e ON e.muscle_group_id = g.id AND e.is_archived = 0
        GROUP BY g.id
        """,
    )
    fun observeExerciseCounts(): Flow<List<GroupExerciseCountRow>>

    @Query("SELECT COUNT(*) FROM exercises WHERE muscle_group_id = :groupId")
    suspend fun countExercises(groupId: String): Int

    @Query("SELECT COUNT(*) FROM routine_blocks WHERE muscle_group_id = :groupId")
    suspend fun countBlocks(groupId: String): Int

    @Insert
    suspend fun insert(entity: MuscleGroupEntity)

    @Update
    suspend fun update(entity: MuscleGroupEntity): Int

    /**
     * Update y, si no existía, insert. No se usa @Upsert/REPLACE: @Upsert ignora en silencio
     * un choque con el índice único de nombre y REPLACE dispararía los CASCADE.
     */
    @Transaction
    suspend fun save(entity: MuscleGroupEntity) {
        if (update(entity) == 0) insert(entity)
    }

    @Query("UPDATE muscle_groups SET sort_order = :sortOrder, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("DELETE FROM muscle_groups WHERE id = :id")
    suspend fun deleteById(id: String): Int
}
