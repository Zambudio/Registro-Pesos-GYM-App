package com.zambudio.registropesos.feature.exercises

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.usecase.ExerciseNameError
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

// Robolectric: savedStateHandle.toRoute() necesita android.os.Bundle real.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class ExerciseEditViewModelTest {
    private val groups = FakeMuscleGroupRepository(listOf(group("g1", "Pecho", 0), group("g2", "Espalda", 1)))
    private val exercises = FakeExerciseRepository(
        listOf(exercise("e1", "Press banca", "g1", imagePath = "old.jpg", createdAt = 100L)),
    )
    private val images = FakeImageStore()
    private val clock = FixedClock(now = 5_000L)
    private val events = mutableListOf<ExerciseEditEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.newViewModel(
        exerciseId: String? = null,
        muscleGroupId: String? = null,
    ): ExerciseEditViewModel {
        val args = buildMap {
            exerciseId?.let { put("exerciseId", it) }
            muscleGroupId?.let { put("muscleGroupId", it) }
        }
        val viewModel = ExerciseEditViewModel(
            savedStateHandle = SavedStateHandle(args),
            exerciseRepository = exercises,
            muscleGroupRepository = groups,
            imageStore = images,
            ids = CountingIds(),
            time = clock,
            io = UnconfinedTestDispatcher(testScheduler),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return viewModel
    }

    private fun messages() = events.filterIsInstance<ExerciseEditEvent.ShowMessage>().map { it.message.resId }

    @Test
    fun newExerciseStartsWithRouteGroupAndGroupsLoaded() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g2")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isNew)
        assertEquals("g2", state.muscleGroupId)
        assertEquals("Espalda", state.selectedGroupName)
        assertEquals(listOf("Pecho", "Espalda"), state.groups.map { it.name })
    }

    @Test
    fun emptyNameShowsErrorAndDoesNotSave() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g1")
        viewModel.onNameChange("   ")

        viewModel.onSave()

        assertEquals(ExerciseNameError.EMPTY, viewModel.uiState.value.nameError)
        assertTrue(exercises.upserts.isEmpty())
        assertTrue(events.isEmpty())

        // Al corregirlo se revalida en vivo.
        viewModel.onNameChange("Fondos")
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun tooLongNameShowsError() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g1")
        viewModel.onNameChange("x".repeat(61))

        viewModel.onSave()

        assertEquals(ExerciseNameError.TOO_LONG, viewModel.uiState.value.nameError)
        assertTrue(exercises.upserts.isEmpty())
    }

    @Test
    fun missingGroupBlocksSave() = runTest {
        val viewModel = newViewModel()
        viewModel.onNameChange("Fondos")

        viewModel.onSave()

        assertTrue(viewModel.uiState.value.isGroupMissing)
        assertTrue(exercises.upserts.isEmpty())

        viewModel.onGroupSelected("g1")
        assertFalse(viewModel.uiState.value.isGroupMissing)
    }

    @Test
    fun savingNewExerciseCreatesItAndCloses() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g1")
        viewModel.onNameChange("  Fondos  ")
        viewModel.onNotesChange("   ")

        viewModel.onSave()

        val saved = exercises.upserts.single()
        assertEquals("new-1", saved.id)
        assertEquals("Fondos", saved.name)
        assertEquals("g1", saved.muscleGroupId)
        assertNull(saved.notes)
        assertNull(saved.imagePath)
        assertFalse(saved.isArchived)
        assertEquals(5_000L, saved.createdAt)
        assertEquals(5_000L, saved.updatedAt)
        assertEquals(listOf<ExerciseEditEvent>(ExerciseEditEvent.Close), events)
    }

    @Test
    fun editingKeepsIdAndCreatedAt() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")
        assertFalse(viewModel.uiState.value.isNew)
        assertEquals("Press banca", viewModel.uiState.value.name)
        assertEquals("old.jpg", viewModel.uiState.value.imageFile?.name)

        viewModel.onNameChange("Press banca inclinado")
        viewModel.onGroupSelected("g2")
        viewModel.onNotesChange("Banco a 30º")
        viewModel.onSave()

        val saved = exercises.upserts.single()
        assertEquals("e1", saved.id)
        assertEquals(100L, saved.createdAt)
        assertEquals(5_000L, saved.updatedAt)
        assertEquals("g2", saved.muscleGroupId)
        assertEquals("Banco a 30º", saved.notes)
        assertEquals("old.jpg", saved.imagePath)
        assertTrue(images.deleted.isEmpty())
    }

    @Test
    fun replacingImageDeletesOldOneOnlyWhenSaving() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")

        viewModel.onImagePicked("content://media/1")

        assertEquals("exercise_new1.jpg", viewModel.uiState.value.imageFile?.name)
        assertTrue("la anterior no se borra antes de guardar", images.deleted.isEmpty())

        viewModel.onSave()

        assertEquals("exercise_new1.jpg", exercises.upserts.single().imagePath)
        assertEquals(listOf("old.jpg"), images.deleted)
    }

    @Test
    fun pickingTwiceDeletesTheIntermediateImage() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")

        viewModel.onImagePicked("content://media/1")
        viewModel.onImagePicked("content://media/2")

        assertEquals(listOf("exercise_new1.jpg"), images.deleted)
        viewModel.onSave()
        assertEquals("exercise_new2.jpg", exercises.upserts.single().imagePath)
        assertEquals(listOf("exercise_new1.jpg", "old.jpg"), images.deleted)
    }

    @Test
    fun leavingWithoutSavingAsksFirstAndThenDeletesOnlyTheNewImage() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")
        viewModel.onImagePicked("content://media/1")

        viewModel.onBack()

        assertTrue(viewModel.uiState.value.showDiscardDialog)
        assertTrue("aún no se descarta nada", images.deleted.isEmpty())
        assertTrue(events.isEmpty())

        viewModel.onDiscardConfirm()

        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals(listOf("exercise_new1.jpg"), images.deleted)
        assertTrue(exercises.upserts.isEmpty())
        assertEquals(listOf<ExerciseEditEvent>(ExerciseEditEvent.Close), events)
    }

    @Test
    fun keepEditingDismissesDialogAndKeepsChanges() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")
        viewModel.onNameChange("Press banca inclinado")

        viewModel.onBack()
        viewModel.onDiscardDismiss()

        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals("Press banca inclinado", viewModel.uiState.value.name)
        assertTrue(events.isEmpty())

        // Volver al valor guardado ya no cuenta como cambio: sale sin preguntar.
        viewModel.onNameChange("Press banca ")
        viewModel.onBack()
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals(listOf<ExerciseEditEvent>(ExerciseEditEvent.Close), events)
    }

    @Test
    fun backWithoutChangesClosesWithoutAsking() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g1")

        viewModel.onBack()

        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertEquals(listOf<ExerciseEditEvent>(ExerciseEditEvent.Close), events)
    }

    @Test
    fun newExerciseWithDataAsksBeforeLeaving() = runTest {
        val viewModel = newViewModel(muscleGroupId = "g1")
        viewModel.onGroupSelected("g2")

        viewModel.onBack()

        assertTrue(viewModel.uiState.value.showDiscardDialog)
        assertTrue(events.isEmpty())
    }

    @Test
    fun removingImageClearsItAndDeletesOldOnSave() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")

        viewModel.onRemoveImage()
        assertNull(viewModel.uiState.value.imageFile)
        assertTrue(images.deleted.isEmpty())

        viewModel.onSave()
        assertNull(exercises.upserts.single().imagePath)
        assertEquals(listOf("old.jpg"), images.deleted)
    }

    @Test
    fun cameraPhotoIsStoredAndTempFileDeleted() = runTest {
        val temp = File.createTempFile("capture", ".jpg")
        val viewModel = newViewModel(muscleGroupId = "g1")

        viewModel.onPhotoTaken(success = true, uri = "content://provider/camera/x.jpg", tempPath = temp.absolutePath)

        assertEquals("exercise_new1.jpg", viewModel.uiState.value.imageFile?.name)
        assertFalse(viewModel.uiState.value.isImageBusy)
        assertFalse(temp.exists())
    }

    @Test
    fun cancelledCameraOnlyDeletesTempFile() = runTest {
        val temp = File.createTempFile("capture", ".jpg")
        val viewModel = newViewModel(muscleGroupId = "g1")

        viewModel.onPhotoTaken(success = false, uri = "content://provider/camera/x.jpg", tempPath = temp.absolutePath)

        assertNull(viewModel.uiState.value.imageFile)
        assertTrue(images.saved.isEmpty())
        assertFalse(temp.exists())
        assertTrue(events.isEmpty())
    }

    @Test
    fun cameraUnavailableShowsMessage() = runTest {
        val temp = File.createTempFile("capture", ".jpg")
        val viewModel = newViewModel(muscleGroupId = "g1")

        viewModel.onCameraUnavailable(temp.absolutePath)

        assertEquals(listOf(R.string.exercises_camera_unavailable), messages())
        assertFalse(temp.exists())
    }

    @Test
    fun imageFailureShowsMessageWithoutChangingImage() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")
        images.failNextSave = true

        viewModel.onImagePicked("content://media/broken")

        assertEquals("old.jpg", viewModel.uiState.value.imageFile?.name)
        assertFalse(viewModel.uiState.value.isImageBusy)
        assertEquals(listOf(R.string.exercises_image_error), messages())
    }

    @Test
    fun archiveToggleIsPersistedAndKeptOnSave() = runTest {
        val viewModel = newViewModel(exerciseId = "e1")

        viewModel.onToggleArchived()

        assertTrue(viewModel.uiState.value.isArchived)
        assertTrue(exercises.exercises.value.single().isArchived)
        assertEquals(listOf(R.string.exercises_archived_done), messages())

        viewModel.onSave()
        assertTrue(exercises.upserts.single().isArchived)
    }

    @Test
    fun unknownExerciseIsNotFound() = runTest {
        val viewModel = newViewModel(exerciseId = "missing")

        assertTrue(viewModel.uiState.value.isNotFound)
        viewModel.onSave()
        assertTrue(exercises.upserts.isEmpty())
    }
}
