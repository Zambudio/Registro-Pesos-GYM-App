package com.zambudio.registropesos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType

@Dao
interface TombstoneDao {
    // REPLACE es seguro aquí: deleted_entities no tiene hijos.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeletedEntityEntity)

    @Query("SELECT * FROM deleted_entities WHERE entity_type = :type AND id = :id")
    suspend fun get(type: TombstoneType, id: String): DeletedEntityEntity?

    @Query("SELECT * FROM deleted_entities ORDER BY deleted_at")
    suspend fun getAll(): List<DeletedEntityEntity>

    @Query("DELETE FROM deleted_entities WHERE entity_type = :type AND id = :id")
    suspend fun delete(type: TombstoneType, id: String)
}
