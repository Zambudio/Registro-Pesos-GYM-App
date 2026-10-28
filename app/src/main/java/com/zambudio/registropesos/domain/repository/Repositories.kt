package com.zambudio.registropesos.domain.repository

import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.model.AppSettings
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.ExerciseSessionPoint
import com.zambudio.registropesos.domain.model.FlatSet
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineSummary
import com.zambudio.registropesos.domain.model.SessionDetail
import com.zambudio.registropesos.domain.model.SessionSummary
import com.zambudio.registropesos.domain.model.SessionVolumePoint
import com.zambudio.registropesos.domain.model.WorkoutSession
import com.zambudio.registropesos.domain.model.WorkoutSet
import kotlinx.coroutines.flow.Flow
import java.io.File

// Contratos de datos. Implementación en data/; las features solo dependen de esto.
// Cualquier cambio de contrato pasa por el orquestador.

interface MuscleGroupRepository {
    fun observeAll(): Flow<List<MuscleGroup>>

    /** Nº de ejercicios no archivados por id de grupo. */
    fun observeExerciseCounts(): Flow<Map<String, Int>>
    suspend fun getById(id: String): MuscleGroup?
    suspend fun upsert(group: MuscleGroup)
    suspend fun reorder(orderedIds: List<String>)

    /** Falla con [MuscleGroupInUseException] si tiene ejercicios (incluidos archivados) o bloques de rutina. */
    suspend fun delete(id: String): Result<Unit>
}

interface ExerciseRepository {
    fun observeAll(includeArchived: Boolean = false): Flow<List<Exercise>>
    fun observeByGroup(groupId: String, includeArchived: Boolean = false): Flow<List<Exercise>>
    fun observeById(id: String): Flow<Exercise?>
    suspend fun getById(id: String): Exercise?
    suspend fun upsert(exercise: Exercise)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun hasHistory(id: String): Boolean
}

/** Almacén de imágenes en filesDir/images. Las rutas que maneja el dominio son relativas. */
interface ImageStore {
    /** Lee la imagen de [sourceUri] (content:// o file://), la redimensiona a 1280 px lado mayor, JPEG 80. */
    suspend fun saveResized(sourceUri: String): String
    fun resolve(relativePath: String): File
    suspend fun delete(relativePath: String)
    fun listAll(): List<File>

    /** Copia bytes ya procesados (import de backup) con el nombre relativo dado. */
    suspend fun importRaw(relativePath: String, bytes: ByteArray)
}

interface RoutineRepository {
    fun observeSummaries(): Flow<List<RoutineSummary>>
    fun observeDetail(id: String): Flow<RoutineDetail?>
    suspend fun getDetail(id: String): RoutineDetail?

    /** Transaccional: inserta/actualiza la rutina y reemplaza bloques y ejercicios. */
    suspend fun save(detail: RoutineDetail)
    suspend fun reorder(orderedIds: List<String>)

    /** Borra la rutina (cascade a bloques/ejercicios) y deja tombstone. El historial queda con routineId = null. */
    suspend fun delete(id: String)
}

interface WorkoutRepository {
    fun observeInProgress(): Flow<WorkoutSession?>
    suspend fun getInProgress(): WorkoutSession?
    fun observeSessionDetail(id: String): Flow<SessionDetail?>
    suspend fun getSession(id: String): WorkoutSession?

    /**
     * Crea la sesión IN_PROGRESS con [defaultSets] filas vacías por ejercicio de la rutina.
     * Lanza [SessionAlreadyInProgressException] si ya hay una en curso. Devuelve el id.
     */
    suspend fun start(routine: RoutineDetail, startedAt: Long): String
    suspend fun upsertSet(set: WorkoutSet)
    suspend fun upsertSets(sets: List<WorkoutSet>)
    suspend fun deleteSet(setId: String)

    /** Marca FINISHED con [finishedAt] y borra las series cuyos ids no estén en [keepSetIds]. */
    suspend fun finish(sessionId: String, finishedAt: Long, keepSetIds: List<String>)

    /** Borra físicamente la sesión en curso descartada (no tiene valor histórico). */
    suspend fun discard(sessionId: String)

    /** Series de la última sesión FINISHED que contiene el ejercicio, excluyendo [excludeSessionId]. */
    suspend fun lastFinishedSetsForExercise(exerciseId: String, excludeSessionId: String?): List<WorkoutSet>

    fun observeFinishedSessions(): Flow<List<SessionSummary>>

    /** Borra la sesión (cascade a series) y deja tombstone. */
    suspend fun deleteSession(id: String)
}

interface ProgressRepository {
    fun observeExercisePoints(exerciseId: String, fromMillis: Long): Flow<List<ExerciseSessionPoint>>
    fun observeGroupSessionVolumes(groupId: String, fromMillis: Long): Flow<List<SessionVolumePoint>>

    /** Series de sesiones FINISHED con reps > 0 y peso no nulo; [exerciseId] null = todas. */
    fun observeFinishedSetsFlat(exerciseId: String?): Flow<List<FlatSet>>
    suspend fun finishedSetsFlat(exerciseId: String?): List<FlatSet>
}

interface BackupRepository {
    /** Instantánea coherente de toda la BD (excluye la sesión IN_PROGRESS). */
    suspend fun exportSnapshot(appVersion: String, exportedAt: Long): BackupSnapshot

    /** Merge por UUID: gana el updatedAt más reciente; respeta tombstones. No copia imágenes. */
    suspend fun importSnapshot(snapshot: BackupSnapshot): ImportReport
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

class MuscleGroupInUseException(message: String) : IllegalStateException(message)

class SessionAlreadyInProgressException(val sessionId: String) :
    IllegalStateException("Ya hay una sesión en curso: $sessionId")
