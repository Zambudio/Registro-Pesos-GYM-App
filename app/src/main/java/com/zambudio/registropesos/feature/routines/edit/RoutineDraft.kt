package com.zambudio.registropesos.feature.routines.edit

import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.model.Routine
import com.zambudio.registropesos.domain.model.RoutineBlock
import com.zambudio.registropesos.domain.model.RoutineBlockDetail
import com.zambudio.registropesos.domain.model.RoutineDetail
import com.zambudio.registropesos.domain.model.RoutineExercise
import com.zambudio.registropesos.domain.model.RoutineExerciseDetail

// Copia editable de una rutina. Vive en el ViewModel y solo se persiste al pulsar Guardar.
// Los ids de bloques y ejercicios nuevos se generan al añadirlos (sirven de clave estable
// en la lista); createdAt = 0 marca "nuevo" y se fija a now al guardar.

data class RoutineDraft(
    val name: String = "",
    val subtitle: String = "",
    val blocks: List<DraftBlock> = emptyList(),
)

data class DraftBlock(
    val id: String,
    val muscleGroup: MuscleGroup,
    val exercises: List<DraftExercise> = emptyList(),
    val createdAt: Long = 0L,
)

data class DraftExercise(
    /** Id del RoutineExercise (no del ejercicio de la biblioteca). */
    val id: String,
    val exercise: Exercise,
    val defaultSets: Int,
    val createdAt: Long = 0L,
)

fun RoutineDetail.toDraft(): RoutineDraft = RoutineDraft(
    name = routine.name,
    subtitle = routine.subtitle.orEmpty(),
    blocks = blocks.map { blockDetail ->
        DraftBlock(
            id = blockDetail.block.id,
            muscleGroup = blockDetail.muscleGroup,
            createdAt = blockDetail.block.createdAt,
            exercises = blockDetail.exercises.map { item ->
                DraftExercise(
                    id = item.routineExercise.id,
                    exercise = item.exercise,
                    defaultSets = item.routineExercise.defaultSets,
                    createdAt = item.routineExercise.createdAt,
                )
            },
        )
    },
)

/**
 * Convierte el borrador en el agregado que se guarda. [base] aporta id, sortOrder y createdAt
 * de la rutina; el orden de las listas fija los sortOrder de bloques y ejercicios.
 */
fun RoutineDraft.toDetail(base: Routine, now: Long): RoutineDetail {
    val routine = base.copy(
        name = name.trim(),
        subtitle = subtitle.trim().ifEmpty { null },
        updatedAt = now,
    )
    return RoutineDetail(
        routine = routine,
        blocks = blocks.mapIndexed { blockIndex, block ->
            RoutineBlockDetail(
                block = RoutineBlock(
                    id = block.id,
                    routineId = routine.id,
                    muscleGroupId = block.muscleGroup.id,
                    sortOrder = blockIndex,
                    createdAt = block.createdAt.ifNew(now),
                    updatedAt = now,
                ),
                muscleGroup = block.muscleGroup,
                exercises = block.exercises.mapIndexed { index, item ->
                    RoutineExerciseDetail(
                        routineExercise = RoutineExercise(
                            id = item.id,
                            blockId = block.id,
                            exerciseId = item.exercise.id,
                            sortOrder = index,
                            defaultSets = item.defaultSets,
                            createdAt = item.createdAt.ifNew(now),
                            updatedAt = now,
                        ),
                        exercise = item.exercise,
                    )
                },
            )
        },
    )
}

private fun Long.ifNew(now: Long): Long = if (this > 0L) this else now
