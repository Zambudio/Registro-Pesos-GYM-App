package com.zambudio.registropesos.feature.exercises.musclegroups

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteFullException
import com.zambudio.registropesos.R
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.feature.exercises.CountingIds
import com.zambudio.registropesos.feature.exercises.FakeMuscleGroupRepository
import com.zambudio.registropesos.feature.exercises.FixedClock
import com.zambudio.registropesos.feature.exercises.UiMessage
import com.zambudio.registropesos.feature.exercises.group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** La excepción real de SQLite solo existe con Robolectric (en la JVM pura es un stub). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MuscleGroupsConstraintTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uniqueConstraintFromRepositoryIsShownAsDuplicate() = runTest {
        val repository = FakeMuscleGroupRepository(listOf(group("g1", "Pecho", 0)))
        repository.upsertError = { SQLiteConstraintException("UNIQUE constraint failed: muscle_groups.name") }
        val viewModel = MuscleGroupsViewModel(repository, CountingIds(), FixedClock())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onCreateClick()

        // "Pécho" pasa la validación previa (distinto texto) pero el índice único lo rechaza.
        viewModel.onEditorSubmit("Pécho")

        assertEquals(GroupNameError.DUPLICATE, viewModel.uiState.value.editor?.error)
        assertEquals(1, repository.groups.value.size)
    }

    @Test
    fun otherDatabaseErrorOnSaveShowsMessageAndKeepsDialogOpen() = runTest {
        val repository = FakeMuscleGroupRepository(listOf(group("g1", "Pecho", 0)))
        repository.upsertError = { SQLiteFullException("database or disk is full") }
        val (viewModel, messages) = viewModelWithMessages(repository)
        viewModel.onCreateClick()

        viewModel.onEditorSubmit("Antebrazos")

        assertEquals(listOf(UiMessage(R.string.common_error_generic)), messages)
        assertNotNull("el diálogo sigue abierto para reintentar", viewModel.uiState.value.editor)
        assertEquals(null, viewModel.uiState.value.editor?.error)
        assertEquals(1, repository.groups.value.size)
    }

    @Test
    fun databaseErrorOnReorderShowsMessage() = runTest {
        val repository = FakeMuscleGroupRepository(listOf(group("g1", "Pecho", 0), group("g2", "Espalda", 1)))
        repository.reorderError = { SQLiteDiskIOException("disk I/O error") }
        val (viewModel, messages) = viewModelWithMessages(repository)

        viewModel.onMoveDown("g1")

        assertEquals(listOf(UiMessage(R.string.common_error_generic)), messages)
        assertEquals(listOf("g1", "g2"), repository.groups.value.sortedBy { it.sortOrder }.map { it.id })
    }

    private fun TestScope.viewModelWithMessages(
        repository: FakeMuscleGroupRepository,
    ): Pair<MuscleGroupsViewModel, List<UiMessage>> {
        val viewModel = MuscleGroupsViewModel(repository, CountingIds(), FixedClock())
        val messages = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { event -> messages += (event as MuscleGroupsEvent.ShowMessage).message }
        }
        return viewModel to messages
    }
}
