package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zambudio.registropesos.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE (:includeArchived OR is_archived = 0) ORDER BY name COLLATE NOCASE")
    fun observeAll(includeArchived: Boolean): Flow<List<ExerciseEntity>>

    @Query(
        """
        SELECT * FROM exercises
        WHERE muscle_group_id = :groupId AND (:includeArchived OR is_archived = 0)
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun observeByGroup(groupId: String, includeArchived: Boolean): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ExerciseEntity>

    @Query("SELECT * FROM exercises ORDER BY created_at")
    suspend fun getAll(): List<ExerciseEntity>

    @Insert
    suspend fun insert(entity: ExerciseEntity)

    @Update
    suspend fun update(entity: ExerciseEntity): Int

    /** Update y, si no existía, insert (ver [MuscleGroupDao.save]). */
    @Transaction
    suspend fun save(entity: ExerciseEntity) {
        if (update(entity) == 0) insert(entity)
    }

    @Query("UPDATE exercises SET is_archived = :archived, updated_at = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM workout_sets WHERE exercise_id = :id)")
    suspend fun hasHistory(id: String): Boolean
}
