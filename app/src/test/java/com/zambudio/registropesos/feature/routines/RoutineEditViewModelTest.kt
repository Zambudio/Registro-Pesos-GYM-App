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
    private val ids = SequentialIdGenerator()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(routineId: String? = null): RoutineEditViewModel {
        val handle = if (routineId == null) SavedStateHandle() else SavedStateHandle(mapOf("routineId" to routineId))
        val viewModel = RoutineEditViewModel(handle, routines, groups, ids, TimeProvider { now })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private val RoutineEditViewModel.state get() = uiState.value

    /** Añade el grupo como lo hace el usuario: botón y selector. */
    private fun RoutineEditViewModel.addGroup(group: com.zambudio.registropesos.domain.model.MuscleGroup): String {
        onAddBlockClick()
        onGroupSelected(group)
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
        assertEquals(listOf(pecho, triceps, cardio), vm.state.availableGroups)
    }

    @Test
    fun `editar carga la rutina y conserva sus ejercicios heredados`() = runTest {
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

    // --- Grupos ---

    @Test
    fun `anadir grupo lo pone al final y el selector solo ofrece los que faltan`() = runTest {
        val vm = viewModel()

        vm.onAddBlockClick()
        assertTrue(vm.state.showGroupPicker)
        vm.onGroupSelected(triceps)
        assertFalse(vm.state.showGroupPicker)
        vm.addGroup(pecho)

        assertEquals(listOf("Tríceps", "Pecho"), vm.state.draft.blocks.map { it.muscleGroup.name })
        assertTrue(vm.state.draft.blocks.all { it.exercises.isEmpty() })
        assertEquals(listOf(cardio), vm.state.availableGroups)
    }

    @Test
    fun `un grupo ya presente no se repite`() = runTest {
        val vm = viewModel()
        vm.addGroup(pecho)

        vm.onGroupSelected(pecho)

        assertEquals(1, vm.state.draft.blocks.size)
    }

    @Test
    fun `cancelar el selector no anade nada`() = runTest {
        val vm = viewModel()

        vm.onAddBlockClick()
        vm.onGroupPickerDismiss()

        assertFalse(vm.state.showGroupPicker)
        assertTrue(vm.state.draft.blocks.isEmpty())
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

    // --- Validación ---

    @Test
    fun `guardar sin nombre ni grupos muestra errores y no guarda`() = runTest {
        val vm = viewModel()
        assertTrue(vm.state.errors.isEmpty())

        vm.onSave()

        assertEquals(setOf(RoutineError.NAME_EMPTY, RoutineError.NO_GROUPS), vm.state.errors)
        assertTrue(routines.saved.isEmpty())

        // Tras el primer intento, los errores se recalculan en vivo.
        vm.onNameChange("Día 3")
        assertEquals(setOf(RoutineError.NO_GROUPS), vm.state.errors)
        vm.addGroup(pecho)
        assertTrue(vm.state.errors.isEmpty()) // un grupo sin ejercicios basta
    }

    @Test
    fun `nombre solo con espacios no es valido`() = runTest {
        val vm = viewModel()
        vm.addGroup(pecho)
        vm.onNameChange("   ")

        vm.onSave()

        assertEquals(setOf(RoutineError.NAME_EMPTY), vm.state.errors)
        assertTrue(routines.saved.isEmpty())
    }

    // --- Guardado ---

    @Test
    fun `guardar nueva solo con grupos y comentario genera ids, fechas y la coloca al final`() = runTest {
        val vm = viewModel()
        vm.onNameChange("  Día 3 ")
        vm.onSubtitleChange("   ")
        vm.addGroup(pecho)
        vm.addGroup(triceps)

        vm.onSave()

        val saved = routines.saved.single()
        assertEquals("id-1", saved.routine.id) // primer id generado: el de la rutina
        assertEquals("Día 3", saved.routine.name)
        assertNull(saved.routine.subtitle)
        assertEquals(4, saved.routine.sortOrder) // la última existente tiene sortOrder 3
        assertEquals(now, saved.routine.createdAt)
        assertEquals(now, saved.routine.updatedAt)
        assertEquals(listOf(pecho.id, triceps.id), saved.blocks.map { it.block.muscleGroupId })
        assertEquals(listOf(0, 1), saved.blocks.map { it.block.sortOrder })
        assertTrue(saved.blocks.all { it.block.routineId == "id-1" && it.block.createdAt == now })
        assertTrue(saved.blocks.all { it.exercises.isEmpty() })
        val allIds = listOf(saved.routine.id) + saved.blocks.map { it.block.id }
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
        vm.onSubtitleChange("Pecho: 4 · Tríceps: 2")
        vm.onMoveBlockDown(pechoBlock.id)

        vm.onSave()

        val saved = routines.saved.single()
        assertEquals("r1", saved.routine.id)
        assertEquals("Día 1 bis", saved.routine.name)
        assertEquals("Pecho: 4 · Tríceps: 2", saved.routine.subtitle)
        assertEquals(original.routine.sortOrder, saved.routine.sortOrder)
        assertEquals(original.routine.createdAt, saved.routine.createdAt)
        assertEquals(now, saved.routine.updatedAt)
        assertEquals(listOf("r1-b2", "r1-b1"), saved.blocks.map { it.block.id })
        assertEquals(listOf(0, 1), saved.blocks.map { it.block.sortOrder })
        assertTrue(saved.blocks.all { it.block.createdAt == 100L && it.block.updatedAt == now })
        // Los ejercicios preseleccionados de la v1 se guardan tal cual aunque el editor no los muestre.
        val pechoItems = saved.blocks[1].exercises.map { it.routineExercise }
        assertEquals(listOf("r1-i1", "r1-i2"), pechoItems.map { it.id })
        assertEquals(listOf(100L, 100L), pechoItems.map { it.createdAt })
        assertEquals(listOf(4, 3), pechoItems.map { it.defaultSets })
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

        vm.onNameChange("Día 1!")
        assertTrue(vm.state.isDirty)
        vm.onNameChange("Día 1")
        assertFalse(vm.state.isDirty)

        vm.onSubtitleChange("otro")
        assertTrue(vm.state.isDirty)
        vm.onSubtitleChange("Pecho y tríceps")
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
