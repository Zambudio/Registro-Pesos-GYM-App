package com.zambudio.registropesos.data.repository

import androidx.room.withTransaction
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.backup.BackupMerger
import com.zambudio.registropesos.data.mapper.toDto
import com.zambudio.registropesos.domain.backup.BACKUP_SCHEMA_VERSION
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.repository.BackupRepository
import javax.inject.Inject

class BackupRepositoryImpl @Inject constructor(private val db: AppDatabase) : BackupRepository {

    override suspend fun exportSnapshot(appVersion: String, exportedAt: Long): BackupSnapshot =
        db.withTransaction {
            BackupSnapshot(
                schemaVersion = BACKUP_SCHEMA_VERSION,
                dbVersion = AppDatabase.VERSION,
                appVersion = appVersion,
                exportedAt = exportedAt,
                muscleGroups = db.muscleGroupDao().getAll().map { it.toDto() },
                exercises = db.exerciseDao().getAll().map { it.toDto() },
                routines = db.routineDao().getAll().map { it.toDto() },
                routineBlocks = db.routineDao().getAllBlocks().map { it.toDto() },
                routineExercises = db.routineDao().getAllRoutineExercises().map { it.toDto() },
                sessions = db.workoutDao().getSessionsExcludingInProgress().map { it.toDto() },
                sets = db.workoutDao().getSetsExcludingInProgress().map { it.toDto() },
                tombstones = db.tombstoneDao().getAll().map { it.toDto() },
            )
        }

    /**
     * Lanza [IllegalArgumentException] si el backup viene de una versión de la app más nueva.
     * Todo el merge es una única transacción: o se aplica entero o nada.
     */
    override suspend fun importSnapshot(snapshot: BackupSnapshot): ImportReport {
        require(snapshot.schemaVersion <= BACKUP_SCHEMA_VERSION) {
            "El backup usa el formato v${snapshot.schemaVersion} y esta versión de la app solo " +
                "entiende hasta la v$BACKUP_SCHEMA_VERSION. Actualiza la app para importarlo."
        }
        return db.withTransaction { BackupMerger(db).merge(snapshot) }
    }
}
