package com.zambudio.registropesos.feature.exercises.musclegroups

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.feature.exercises.CountingIds
import com.zambudio.registropesos.feature.exercises.FakeMuscleGroupRepository
import com.zambudio.registropesos.feature.exercises.FixedClock
import com.zambudio.registropesos.feature.exercises.group
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
}
