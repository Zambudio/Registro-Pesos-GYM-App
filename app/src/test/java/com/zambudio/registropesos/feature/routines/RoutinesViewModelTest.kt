package com.zambudio.registropesos.feature.routines

import com.zambudio.registropesos.feature.routines.list.RoutinesEvent
import com.zambudio.registropesos.feature.routines.list.RoutinesViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    private val repository = FakeRoutineRepository(
        listOf(
            storedRoutine("r1", "Día 1", sortOrder = 0),
            storedRoutine("r2", "Día 2", sortOrder = 1),
            storedRoutine("r3", "Día 3", sortOrder = 2),
        ),
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.subscribedViewModel(): RoutinesViewModel {
        val viewModel = RoutinesViewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun RoutinesViewModel.ids() = uiState.value.routines.map { it.id }

    @Test
    fun `carga la lista en orden con recuentos`() = runTest {
        val viewModel = subscribedViewModel()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("r1", "r2", "r3"), viewModel.ids())
        assertEquals(2, state.routines.first().blockCount)
        assertEquals(3, state.routines.first().exerciseCount)
    }

    @Test
    fun `lista vacia sin error`() = runTest {
        val viewModel = RoutinesViewModel(FakeRoutineRepository())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertTrue(state.routines.isEmpty())
    }

    @Test
    fun `subir y bajar persisten el orden completo`() = runTest {
        val viewModel = subscribedViewModel()

        viewModel.onMoveUp("r3")
        assertEquals(listOf("r1", "r3", "r2"), repository.reorderCalls.last())
        assertEquals(listOf("r1", "r3", "r2"), viewModel.ids())

        viewModel.onMoveDown("r1")
        assertEquals(listOf("r3", "r1", "r2"), viewModel.ids())
    }

    @Test
    fun `mover fuera de los extremos no llama al repositorio`() = runTest {
        val viewModel = subscribedViewModel()

        viewModel.onMoveUp("r1")
        viewModel.onMoveDown("r3")

        assertTrue(repository.reorderCalls.isEmpty())
    }

    @Test
    fun `borrar pide confirmacion y solo borra al confirmar`() = runTest {
        val viewModel = subscribedViewModel()

        viewModel.onDeleteClick("r2")
        assertEquals("r2", viewModel.uiState.value.pendingDelete?.id)
        assertTrue(repository.deleted.isEmpty())

        viewModel.onDeleteConfirm()
        assertEquals(listOf("r2"), repository.deleted)
        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(listOf("r1", "r3"), viewModel.ids())
    }

    @Test
    fun `cancelar el borrado no borra`() = runTest {
        val viewModel = subscribedViewModel()

        viewModel.onDeleteClick("r2")
        viewModel.onDeleteDismiss()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertTrue(repository.deleted.isEmpty())
        assertEquals(3, viewModel.uiState.value.routines.size)
    }

    @Test
    fun `fallo al borrar emite evento`() = runTest {
        repository.failOnDelete = true
        val viewModel = subscribedViewModel()

        viewModel.onDeleteClick("r1")
        viewModel.onDeleteConfirm()

        assertEquals(RoutinesEvent.DeleteFailed, viewModel.eventFlow.first())
        assertEquals(3, viewModel.uiState.value.routines.size)
    }
}
