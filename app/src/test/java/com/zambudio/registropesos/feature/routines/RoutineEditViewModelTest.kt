package com.zambudio.registropesos.feature.routines

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.domain.usecase.RoutineError
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.routines.edit.RoutineEditDialog
import com.zambudio.registropesos.feature.routines.edit.RoutineEditEvent
import com.zambudio.registropesos.feature.routines.edit.RoutineEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Robolectric: SavedStateHandle.toRoute() decodifica los argumentos con android.os.Bundle.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class RoutineEditViewModelTest {

    private val now = 5_000L
    private val routines = FakeRoutineRepository(
        listOf(storedRoutine("r1", sortOrder = 0), storedRoutine("r2", sortOrder = 3)),
    )
    private val groups = FakeMuscleGroupRepository(listOf(pecho, triceps, cardio))
    private val exercises = FakeExerciseRepository(listOf(pressBanca, aperturas, fondos, pressArchivado, extensiones))
    private val settings = FakeSettingsRepository(defaultSets = 5)
    private val ids = SequentialIdGenerator()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(routineId: String? = null): RoutineEditViewModel {
        val handle = if (routineId == null) SavedStateHandle() else SavedStateHandle(mapOf("routineId" to routineId))
        val viewModel = RoutineEditViewModel(handle, routines, groups, exercises, settings, ids, TimeProvider { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val RoutineEditViewModel.state get() = uiState.value

    /** Nueva rutina con un bloque de Pecho y los ejercicios indicados ya añadidos. */
    private fun RoutineEditViewModel.addPechoBlock(vararg exerciseIds: String): String {
        onAddBlockClick()
        onGroupSelected(pecho)
        exerciseIds.forEach(::onPickerToggle)
        onPickerConfirm()
        return state.draft.blocks.last().id
    }

    // --- Carga ---

    @Test
    fun `nueva rutina arranca vacia y sin cambios`() = runTest {
        val vm = viewModel()

        assertTrue(vm.state.isNew)
        assertFalse(vm.state.isLoading)
        assertFalse(vm.state.isDirty)
        assertTrue(vm.state.draft.blocks.isEmpty())
        assertEquals(listOf(pecho, triceps, cardio), vm.state.muscleGroups)
    }

    @Test
    fun `editar carga la rutina con ejercicios archivados incluidos`() = runTest {
        val vm = viewModel("r1")

        assertFalse(vm.state.isNew)
        assertEquals("Día 1", vm.state.draft.name)
        assertEquals("Pecho y tríceps", vm.state.draft.subtitle)
        assertEquals(listOf("Pecho", "Tríceps"), vm.state.draft.blocks.map { it.muscleGroup.name })
        assertEquals(listOf(4, 3), vm.state.draft.blocks[0].exercises.map { it.defaultSets })
        assertFalse(vm.state.isDirty)
    }

    @Test
    fun `rutina inexistente muestra no encontrada`() = runTest {
        val vm = viewModel("nope")

        assertTrue(vm.state.notFound)
        vm.onSave()
        assertTrue(routines.saved.isEmpty())
    }

    // --- Bloques y selector ---

    @Test
    fun `anadir bloque abre el selector del grupo sin archivados`() = runTest {
        val vm = viewModel()

        vm.onAddBlockClick()
        assertTrue(vm.state.showGroupPicker)
        vm.onGroupSelected(pecho)

        assertFalse(vm.state.showGroupPicker)
        val block = vm.state.draft.blocks.single()
        val picker = vm.state.exercisePicker!!
        assertEquals(block.id, picker.blockId)
        assertFalse(picker.isLoading)
        assertTrue(picker.groupHasExercises)
        assertEquals(listOf("Aperturas", "Fondos", "Press banca"), picker.available.map { it.name })
    }

    @Test
    fun `seleccion multiple anade en orden del selector con las series de ajustes`() = runTest {
        val vm = viewModel()

        val blockId = vm.addPechoBlock(pressBanca.id, aperturas.id)

        assertNull(vm.state.exercisePicker)
        val block = vm.state.draft.blocks.single { it.id == blockId }
        assertEquals(listOf("Aperturas", "Press banca"), block.exercises.map { it.exercise.name })
        assertEquals(listOf(5, 5), block.exercises.map { it.defaultSets })
    }

    @Test
    fun `el selector no ofrece ni duplica ejercicios ya presentes`() = runTest {
        val vm = viewModel()
        val blockId = vm.addPechoBlock(pressBanca.id)

        vm.onAddExercisesClick(blockId)
        assertEquals(listOf("Aperturas", "Fondos"), vm.state.exercisePicker!!.available.map { it.name })

        // Aunque llegue un id ya presente (p. ej. selección obsoleta), no se duplica.
        vm.onPickerToggle(pressBanca.id)
        vm.onPickerToggle(fondos.id)
        vm.onPickerConfirm()

        val names = vm.state.draft.blocks.single().exercises.map { it.exercise.name }
        assertEquals(listOf("Press banca", "Fondos"), names)
    }

    @Test
    fun `desmarcar quita de la seleccion y cancelar no anade nada`() = runTest {
        val vm = viewModel()
        vm.onAddBlockClick()
        vm.onGroupSelected(pecho)

        vm.onPickerToggle(fondos.id)
        vm.onPickerToggle(aperturas.id)
        vm.onPickerToggle(fondos.id)
        assertEquals(setOf(aperturas.id), vm.state.exercisePicker!!.selectedIds)

        vm.onPickerDismiss()
        assertNull(vm.state.exercisePicker)
        assertTrue(vm.state.draft.blocks.single().exercises.isEmpty())
    }

    @Test
    fun `grupo sin ejercicios lo indica en el selector`() = runTest {
        val vm = viewModel()
        vm.onAddBlockClick()
        vm.onGroupSelected(cardio)

        val picker = vm.state.exercisePicker!!
        assertFalse(picker.isLoading)
        assertFalse(picker.groupHasExercises)
        assertTrue(picker.available.isEmpty())
    }

    @Test
    fun `el selector refleja ejercicios creados mientras esta abierto`() = runTest {
        val vm = viewModel()
        vm.onAddBlockClick()
        vm.onGroupSelected(cardio)

        exercises.exercises.value += exercise("e-cinta", cardio, "Cinta")

        assertEquals(listOf("Cinta"), vm.state.exercisePicker!!.available.map { it.name })
    }

    @Test
    fun `reordenar y quitar bloques`() = runTest {
        val vm = viewModel("r1")
        val (b1, b2) = vm.state.draft.blocks.map { it.id }

        vm.onMoveBlockDown(b1)
        assertEquals(listOf(b2, b1), vm.state.draft.blocks.map { it.id })
        vm.onMoveBlockDown(b1) // ya es el último: sin cambios
        assertEquals(listOf(b2, b1), vm.state.draft.blocks.map { it.id })
        vm.onMoveBlockUp(b1)
        assertEquals(listOf(b1, b2), vm.state.draft.blocks.map { it.id })

        vm.onRemoveBlock(b1)
        assertEquals(listOf(b2), vm.state.draft.blocks.map { it.id })
    }

    @Test
    fun `reordenar y quitar ejercicios dentro de un bloque`() = runTest {
        val vm = viewModel("r1")
        val block = vm.state.draft.blocks.first()
        val (i1, i2) = block.exercises.map { it.id }

        vm.onMoveExerciseUp(block.id, i2)
        assertEquals(listOf(i2, i1), vm.state.draft.blocks.first().exercises.map { it.id })
        vm.onMoveExerciseUp(block.id, i2) // ya es el primero
        assertEquals(listOf(i2, i1), vm.state.draft.blocks.first().exercises.map { it.id })

        vm.onRemoveExercise(block.id, i2)
        assertEquals(listOf(i1), vm.state.draft.blocks.first().exercises.map { it.id })
        // El otro bloque no se toca.
        assertEquals(1, vm.state.draft.blocks[1].exercises.size)
    }

    @Test
    fun `las series por defecto se limitan a 1 y 20`() = runTest {
        val vm = viewModel("r1")
        val block = vm.state.draft.blocks.first()
        val item = block.exercises.first() // 4 series

        repeat(10) { vm.onDecrementSets(block.id, item.id) }
        assertEquals(1, vm.state.draft.blocks.first().exercises.first().defaultSets)
        repeat(30) { vm.onIncrementSets(block.id, item.id) }
        assertEquals(20, vm.state.draft.blocks.first().exercises.first().defaultSets)
    }

    // --- Validación ---

    @Test
    fun `guardar sin nombre ni ejercicios muestra errores y no guarda`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.errors.isEmpty())

        vm.onSave()

        assertEquals(setOf(RoutineError.NAME_EMPTY, RoutineError.NO_EXERCISES), vm.state.errors)
        assertTrue(routines.saved.isEmpty())

        // Tras el primer intento, los errores se recalculan en vivo.
        vm.onNameChange("Día 3")
        assertEquals(setOf(RoutineError.NO_EXERCISES), vm.state.errors)
        vm.onAddBlockClick()
        vm.onGroupSelected(pecho)
        assertEquals(setOf(RoutineError.NO_EXERCISES), vm.state.errors) // bloque vacío no basta
        vm.onPickerToggle(fondos.id)
        vm.onPickerConfirm()
        assertTrue(vm.state.errors.isEmpty())
    }

    @Test
    fun `nombre solo con espacios no es valido`() = runTest {
        val vm = viewModel()
        vm.addPechoBlock(fondos.id)
        vm.onNameChange("   ")

        vm.onSave()

        assertEquals(setOf(RoutineError.NAME_EMPTY), vm.state.errors)
        assertTrue(routines.saved.isEmpty())
    }

    // --- Guardado ---

    @Test
    fun `guardar nueva genera ids, fechas y la coloca al final`() = runTest {
        val vm = viewModel()
        vm.onNameChange("  Día 3 ")
        vm.onSubtitleChange("   ")
        vm.addPechoBlock(pressBanca.id, aperturas.id)

        vm.onSave()

        val saved = routines.saved.single()
        assertEquals("id-1", saved.routine.id) // primer id generado: el de la rutina
        assertEquals("Día 3", saved.routine.name)
        assertNull(saved.routine.subtitle)
        assertEquals(4, saved.routine.sortOrder) // la última existente tiene sortOrder 3
        assertEquals(now, saved.routine.createdAt)
        assertEquals(now, saved.routine.updatedAt)
        val block = saved.blocks.single()
        assertEquals("id-1", block.block.routineId)
        assertEquals(0, block.block.sortOrder)
        assertEquals(now, block.block.createdAt)
        assertEquals(listOf(0, 1), block.exercises.map { it.routineExercise.sortOrder })
        assertTrue(block.exercises.all { it.routineExercise.blockId == block.block.id })
        assertTrue(block.exercises.all { it.routineExercise.createdAt == now })
        val allIds = listOf(saved.routine.id, block.block.id) + block.exercises.map { it.routineExercise.id }
        assertEquals(allIds.size, allIds.toSet().size) // ids nuevos y distintos
        assertEquals(RoutineEditEvent.Close, vm.eventFlow.first())
        assertFalse(vm.state.isDirty)
    }

    @Test
    fun `guardar edicion conserva ids y createdAt y actualiza updatedAt`() = runTest {
        val vm = viewModel("r1")
        val original = storedRoutine("r1")
        val pechoBlock = vm.state.draft.blocks.first()
        vm.onNameChange("Día 1 bis")
        vm.onMoveBlockDown(pechoBlock.id)
        vm.onAddExercisesClick(pechoBlock.id)
        vm.onPickerToggle(fondos.id)
        vm.onPickerConfirm()

        vm.onSave()

        val saved = routines.saved.single()
        assertEquals("r1", saved.routine.id)
        assertEquals("Día 1 bis", saved.routine.name)
        assertEquals(original.routine.sortOrder, saved.routine.sortOrder)
        assertEquals(original.routine.createdAt, saved.routine.createdAt)
        assertEquals(now, saved.routine.updatedAt)
        assertEquals(listOf("r1-b2", "r1-b1"), saved.blocks.map { it.block.id })
        assertEquals(listOf(0, 1), saved.blocks.map { it.block.sortOrder })
        assertTrue(saved.blocks.all { it.block.createdAt == 100L && it.block.updatedAt == now })
        val pechoItems = saved.blocks[1].exercises.map { it.routineExercise }
        assertEquals(listOf("r1-i1", "r1-i2"), pechoItems.take(2).map { it.id })
        assertEquals(listOf(100L, 100L, now), pechoItems.map { it.createdAt })
        assertEquals(fondos.id, pechoItems[2].exerciseId)
        assertEquals(5, pechoItems[2].defaultSets)
        assertEquals(listOf(4, 3, 5), pechoItems.map { it.defaultSets })
    }

    @Test
    fun `fallo al guardar emite evento y permite reintentar`() = runTest {
        routines.failOnSave = true
        val vm = viewModel("r1")
        vm.onNameChange("Otro nombre")

        vm.onSave()

        assertEquals(RoutineEditEvent.SaveFailed, vm.eventFlow.first())
        assertFalse(vm.state.isSaving)
        assertTrue(vm.state.isDirty)
    }

    // --- Cambios sin guardar ---

    @Test
    fun `detecta cambios y deja de haberlos al deshacerlos`() = runTest {
        val vm = viewModel("r1")
        val block = vm.state.draft.blocks.first()
        val item = block.exercises.first()

        vm.onNameChange("Día 1!")
        assertTrue(vm.state.isDirty)
        vm.onNameChange("Día 1")
        assertFalse(vm.state.isDirty)

        vm.onIncrementSets(block.id, item.id)
        assertTrue(vm.state.isDirty)
        vm.onDecrementSets(block.id, item.id)
        assertFalse(vm.state.isDirty)

        vm.onMoveBlockDown(block.id)
        assertTrue(vm.state.isDirty)
    }

    @Test
    fun `salir sin cambios cierra directamente`() = runTest {
        val vm = viewModel("r1")

        vm.onBack()

        assertNull(vm.state.dialog)
        assertEquals(RoutineEditEvent.Close, vm.eventFlow.first())
    }

    @Test
    fun `salir con cambios pide confirmacion y descartar cierra`() = runTest {
        val vm = viewModel()
        vm.onNameChange("Algo")

        vm.onBack()
        assertEquals(RoutineEditDialog.DISCARD_CHANGES, vm.state.dialog)

        vm.onDialogDismiss() // seguir editando
        assertNull(vm.state.dialog)
        assertEquals("Algo", vm.state.draft.name)

        vm.onBack()
        vm.onDiscardConfirm()
        assertNull(vm.state.dialog)
        assertEquals(RoutineEditEvent.Close, vm.eventFlow.first())
        assertTrue(routines.saved.isEmpty())
    }

    // --- Borrado ---

    @Test
    fun `borrar desde el editor pide confirmacion y borra`() = runTest {
        val vm = viewModel("r1")

        vm.onDeleteClick()
        assertEquals(RoutineEditDialog.DELETE, vm.state.dialog)
        vm.onDeleteConfirm()

        assertEquals(listOf("r1"), routines.deleted)
        assertEquals(RoutineEditEvent.Deleted, vm.eventFlow.first())
    }

    @Test
    fun `una rutina nueva no se puede borrar`() = runTest {
        val vm = viewModel()

        vm.onDeleteClick()

        assertNull(vm.state.dialog)
        assertTrue(routines.deleted.isEmpty())
    }
}
