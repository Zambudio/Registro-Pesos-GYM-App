package com.zambudio.registropesos.data.backup

import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.RoutineBlockEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.data.mapper.toEntity
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ExerciseDto
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.backup.MuscleGroupDto
import com.zambudio.registropesos.domain.backup.RoutineBlockDto
import com.zambudio.registropesos.domain.backup.RoutineDto
import com.zambudio.registropesos.domain.backup.RoutineExerciseDto
import com.zambudio.registropesos.domain.backup.SessionDto
import com.zambudio.registropesos.domain.backup.SessionGroupDto
import com.zambudio.registropesos.domain.backup.SetDto
import com.zambudio.registropesos.domain.backup.TableReport
import com.zambudio.registropesos.domain.backup.TombstoneDto
import com.zambudio.registropesos.domain.model.SessionStatus
import java.util.Locale

/**
 * Merge de un backup sobre la BD local (un uso por import; debe ejecutarse dentro de una
 * transacción). Reglas (ADR-004/005):
 * 1. Grupos entrantes con otro id y el mismo nombre (sin mayúsculas) se remapean al id local.
 * 2. Tombstones entrantes borran la fila local si local.updatedAt < deletedAt.
 * 3. Por tabla (grupos → ejercicios → rutinas → sesiones): tombstone local con deletedAt >=
 *    updatedAt entrante → skip; no existe → insert; entrante más nuevo → update (los agregados
 *    reemplazan sus hijos); si no → skip. FKs rotas → skip + warning.
 * Las sesiones IN_PROGRESS nunca se importan ni se pisan. Los grupos de sesión (v2) se reemplazan
 * con la sesión; un backup v1 no los trae y entonces se dejan como estén.
 */
internal class BackupMerger(private val db: AppDatabase) {
    private val groupDao = db.muscleGroupDao()
    private val exerciseDao = db.exerciseDao()
    private val routineDao = db.routineDao()
    private val workoutDao = db.workoutDao()
    private val tombstoneDao = db.tombstoneDao()

    private val warnings = mutableListOf<String>()
    private val imagesToCopy = linkedMapOf<String, String>()
    private val groupIdMap = mutableMapOf<String, String>()
    private var deleted = 0

    suspend fun merge(snapshot: BackupSnapshot): ImportReport {
        buildGroupRemap(snapshot.muscleGroups)
        snapshot.tombstones.forEach { applyTombstone(it) }
        val groups = mergeGroups(snapshot.muscleGroups)
        val exercises = mergeExercises(snapshot.exercises)
        val routines = mergeRoutines(snapshot.routines, snapshot.routineBlocks, snapshot.routineExercises)
        val sessions = mergeSessions(snapshot, snapshot.sets)
        return ImportReport(
            muscleGroups = groups.toReport(),
            exercises = exercises.toReport(),
            routines = routines.toReport(),
            sessions = sessions.toReport(),
            deleted = deleted,
            warnings = warnings.toList(),
            imagesToCopy = imagesToCopy.toMap(),
        )
    }

    // --- Grupos ---

    private suspend fun buildGroupRemap(incoming: List<MuscleGroupDto>) {
        val local = groupDao.getAll()
        val localIds = local.map { it.id }.toSet()
        val localByName = local.associateBy { normalize(it.name) }
        incoming.filter { it.id !in localIds }.forEach { group ->
            localByName[normalize(group.name)]?.let { groupIdMap[group.id] = it.id }
        }
    }

    private fun mapGroupId(id: String): String = groupIdMap[id] ?: id

    private suspend fun mergeGroups(incoming: List<MuscleGroupDto>): Counter {
        val counter = Counter()
        val idByName = groupDao.getAll().associate { normalize(it.name) to it.id }.toMutableMap()
        for (group in incoming) {
            val name = normalize(group.name)
            val local = if (group.id in groupIdMap) null else groupDao.getById(group.id)
            val clash = idByName[name]?.takeIf { it != group.id }
            when {
                group.id in groupIdMap || isTombstoned(TombstoneType.MUSCLE_GROUP, group.id, group.updatedAt) ->
                    counter.skipped++
                clash != null -> {
                    warnings += "Grupo '${group.name}' omitido: ya existe otro grupo con ese nombre"
                    counter.skipped++
                }
                local == null -> {
                    groupDao.insert(group.toEntity())
                    idByName[name] = group.id
                    counter.inserted++
                }
                group.updatedAt > local.updatedAt -> {
                    groupDao.update(group.toEntity())
                    idByName.remove(normalize(local.name))
                    idByName[name] = group.id
                    counter.updated++
                }
                else -> counter.skipped++
            }
        }
        return counter
    }

    // --- Ejercicios ---

    private suspend fun mergeExercises(incoming: List<ExerciseDto>): Counter {
        val counter = Counter()
        for (exercise in incoming) {
            val outcome = mergeExercise(exercise)
            counter.add(outcome)
            if (outcome != Outcome.SKIPPED) exercise.imageFile?.let { imagesToCopy[exercise.id] = it }
        }
        return counter
    }

    private suspend fun mergeExercise(exercise: ExerciseDto): Outcome {
        val groupId = mapGroupId(exercise.muscleGroupId)
        if (groupDao.getById(groupId) == null) {
            warnings += "Ejercicio '${exercise.name}' omitido: su grupo muscular no existe"
            return Outcome.SKIPPED
        }
        val local = exerciseDao.getById(exercise.id)
        val entity = exercise.toEntity(groupId)
        return when {
            local == null -> {
                exerciseDao.insert(entity)
                Outcome.INSERTED
            }
            exercise.updatedAt > local.updatedAt -> {
                exerciseDao.update(entity)
                Outcome.UPDATED
            }
            else -> Outcome.SKIPPED
        }
    }

    // --- Rutinas (agregado) ---

    private suspend fun mergeRoutines(
        routines: List<RoutineDto>,
        blocks: List<RoutineBlockDto>,
        items: List<RoutineExerciseDto>,
    ): Counter {
        val counter = Counter()
        val blocksByRoutine = blocks.groupBy { it.routineId }
        val itemsByBlock = items.groupBy { it.blockId }
        for (routine in routines) {
            val local = routineDao.getById(routine.id)
            val skip = isTombstoned(TombstoneType.ROUTINE, routine.id, routine.updatedAt) ||
                (local != null && routine.updatedAt <= local.updatedAt)
            if (skip) {
                counter.skipped++
                continue
            }
            routineDao.save(routine.toEntity())
            replaceRoutineChildren(routine, blocksByRoutine[routine.id].orEmpty(), itemsByBlock)
            if (local == null) counter.inserted++ else counter.updated++
        }
        return counter
    }

    private suspend fun replaceRoutineChildren(
        routine: RoutineDto,
        blocks: List<RoutineBlockDto>,
        itemsByBlock: Map<String, List<RoutineExerciseDto>>,
    ) {
        routineDao.deleteBlocksNotIn(routine.id, emptyList()) // CASCADE a routine_exercises
        for (block in blocks) {
            val groupId = mapGroupId(block.muscleGroupId)
            if (groupDao.getById(groupId) == null) {
                warnings += "Rutina '${routine.name}': bloque omitido, su grupo muscular no existe"
                continue
            }
            routineDao.saveBlock(
                RoutineBlockEntity(block.id, routine.id, groupId, block.sortOrder, block.createdAt, block.updatedAt),
            )
            for (item in itemsByBlock[block.id].orEmpty()) {
                if (exerciseDao.getById(item.exerciseId) == null) {
                    warnings += "Rutina '${routine.name}': ejercicio omitido, no existe (${item.exerciseId})"
                    continue
                }
                routineDao.saveRoutineExercise(item.toEntity())
            }
        }
    }

    // --- Sesiones (agregado) ---

    private suspend fun mergeSessions(snapshot: BackupSnapshot, sets: List<SetDto>): Counter {
        val counter = Counter()
        val setsBySession = sets.groupBy { it.sessionId }
        val groupsBySession = snapshot.sessionGroups.groupBy { it.sessionId }
        val hasSessionGroups = snapshot.schemaVersion >= SESSION_GROUPS_SINCE
        for (session in snapshot.sessions) {
            val status = SessionStatus.entries.firstOrNull { it.name == session.status }
            val local = workoutDao.getSession(session.id)
            val skip = when {
                status == null -> {
                    warnings += "Sesión ${session.id} omitida: estado desconocido '${session.status}'"
                    true
                }
                status == SessionStatus.IN_PROGRESS -> true
                local?.status == SessionStatus.IN_PROGRESS -> {
                    warnings += "Sesión ${session.id} omitida: está en curso en este dispositivo"
                    true
                }
                isTombstoned(TombstoneType.SESSION, session.id, session.updatedAt) -> true
                else -> local != null && session.updatedAt <= local.updatedAt
            }
            if (skip || status == null) {
                counter.skipped++
                continue
            }
            // routineId es SET NULL: si la rutina no existe aquí, el snapshot conserva el nombre.
            val routineId = session.routineId?.takeIf { routineDao.getById(it) != null }
            // Un backup v1 no trae el comentario de la sesión: se conserva el local, como sus grupos.
            val entity = session.toEntity(status, routineId).let {
                if (hasSessionGroups) it else it.copy(routineCommentSnapshot = local?.routineCommentSnapshot)
            }
            workoutDao.saveSession(entity)
            replaceSessionSets(session, setsBySession[session.id].orEmpty())
            if (hasSessionGroups) replaceSessionGroups(session, groupsBySession[session.id].orEmpty())
            if (local == null) counter.inserted++ else counter.updated++
        }
        return counter
    }

    private suspend fun replaceSessionSets(session: SessionDto, sets: List<SetDto>) {
        workoutDao.deleteSetsOf(session.id)
        val seen = mutableSetOf<Pair<String, Int>>()
        for (set in sets) {
            when {
                exerciseDao.getById(set.exerciseId) == null ->
                    warnings += "Sesión ${session.id}: serie omitida, el ejercicio no existe (${set.exerciseId})"
                !seen.add(set.exerciseId to set.setIndex) ->
                    warnings += "Sesión ${session.id}: serie duplicada omitida (${set.id})"
                else -> workoutDao.insertSet(set.toEntity())
            }
        }
    }

    /** El grupo puede no existir aquí: el snapshot conserva el nombre (FK SET NULL). */
    private suspend fun replaceSessionGroups(session: SessionDto, groups: List<SessionGroupDto>) {
        workoutDao.deleteSessionGroupsOf(session.id)
        val entities = groups.map { group ->
            val groupId = group.muscleGroupId?.let(::mapGroupId)?.takeIf { groupDao.getById(it) != null }
            group.toEntity(groupId)
        }
        workoutDao.insertSessionGroups(entities)
    }

    // --- Tombstones ---

    private suspend fun applyTombstone(tombstone: TombstoneDto) {
        val type = TombstoneType.entries.firstOrNull { it.name == tombstone.entityType }
        if (type == null) {
            warnings += "Tombstone omitido: tipo desconocido '${tombstone.entityType}'"
            return
        }
        val localUpdatedAt = when (type) {
            TombstoneType.ROUTINE -> routineDao.getById(tombstone.id)?.updatedAt
            // Una sesión en curso nunca se borra por un tombstone importado.
            TombstoneType.SESSION -> workoutDao.getSession(tombstone.id)?.let {
                if (it.status == SessionStatus.IN_PROGRESS) Long.MAX_VALUE else it.updatedAt
            }
            TombstoneType.MUSCLE_GROUP -> groupDao.getById(tombstone.id)?.updatedAt
        }
        when {
            localUpdatedAt == null -> recordTombstone(type, tombstone)
            localUpdatedAt < tombstone.deletedAt -> if (deleteLocal(type, tombstone.id)) {
                deleted++
                recordTombstone(type, tombstone)
            }
            else -> Unit // La versión local es posterior al borrado: se conserva.
        }
    }

    private suspend fun deleteLocal(type: TombstoneType, id: String): Boolean = when (type) {
        TombstoneType.ROUTINE -> routineDao.deleteById(id) > 0
        TombstoneType.SESSION -> workoutDao.deleteSession(id) > 0
        TombstoneType.MUSCLE_GROUP ->
            if (groupDao.countExercises(id) > 0 || groupDao.countBlocks(id) > 0) {
                warnings += "Borrado del grupo $id omitido: tiene ejercicios o bloques"
                false
            } else {
                groupDao.deleteById(id) > 0
            }
    }

    private suspend fun recordTombstone(type: TombstoneType, tombstone: TombstoneDto) {
        val existing = tombstoneDao.get(type, tombstone.id)
        if (existing == null || existing.deletedAt < tombstone.deletedAt) {
            tombstoneDao.upsert(DeletedEntityEntity(type, tombstone.id, tombstone.deletedAt))
        }
    }

    private suspend fun isTombstoned(type: TombstoneType, id: String, updatedAt: Long): Boolean =
        tombstoneDao.get(type, id)?.let { it.deletedAt >= updatedAt } ?: false

    private fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)

    private enum class Outcome { INSERTED, UPDATED, SKIPPED }

    private companion object {
        /** Primera versión del formato que incluye sessionGroups. */
        const val SESSION_GROUPS_SINCE = 2
    }

    private class Counter {
        var inserted = 0
        var updated = 0
        var skipped = 0

        fun add(outcome: Outcome) {
            when (outcome) {
                Outcome.INSERTED -> inserted++
                Outcome.UPDATED -> updated++
                Outcome.SKIPPED -> skipped++
            }
        }

        fun toReport() = TableReport(inserted, updated, skipped)
    }
}
