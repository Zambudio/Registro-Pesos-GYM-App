package com.zambudio.registropesos.feature.exercises

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.di.IoDispatcher
import com.zambudio.registropesos.core.navigation.ExerciseEditRoute
import com.zambudio.registropesos.domain.model.Exercise
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.usecase.ExerciseNameError
import com.zambudio.registropesos.domain.usecase.ValidateExerciseName
import com.zambudio.registropesos.domain.util.IdGenerator
import com.zambudio.registropesos.domain.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

data class ExerciseEditUiState(
    val isLoading: Boolean = true,
    val isNotFound: Boolean = false,
    val isNew: Boolean = true,
    val name: String = "",
    val nameError: ExerciseNameError? = null,
    val notes: String = "",
    val muscleGroupId: String? = null,
    val isGroupMissing: Boolean = false,
    val groups: List<MuscleGroupOption> = emptyList(),
    val imageFile: File? = null,
    val isImageBusy: Boolean = false,
    val isArchived: Boolean = false,
    val isSaving: Boolean = false,
) {
    val selectedGroupName: String? get() = groups.firstOrNull { it.id == muscleGroupId }?.name
    val isBusy: Boolean get() = isSaving || isImageBusy
    val canEdit: Boolean get() = !isLoading && !isNotFound && !isBusy
}

sealed interface ExerciseEditEvent {
    /** Guardado correcto o salida sin guardar: volver a la pantalla anterior. */
    data object Close : ExerciseEditEvent
    data class ShowMessage(val message: UiMessage) : ExerciseEditEvent
}

/**
 * Ficha de ejercicio. La imagen nueva se guarda ya redimensionada en cuanto se elige, pero la
 * anterior solo se borra al guardar; si se sale sin guardar se borra la nueva (huérfana).
 * LongParameterList suprimido: son dependencias inyectadas por Hilt, todas necesarias.
 */
@HiltViewModel
class ExerciseEditViewModel @Suppress("LongParameterList") @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val muscleGroupRepository: MuscleGroupRepository,
    private val imageStore: ImageStore,
    private val ids: IdGenerator,
    private val time: TimeProvider,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ExerciseEditRoute>()

    private val _uiState = MutableStateFlow(ExerciseEditUiState(isNew = route.exerciseId == null))
    val uiState: StateFlow<ExerciseEditUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<ExerciseEditEvent>(Channel.BUFFERED)
    val events: Flow<ExerciseEditEvent> = eventChannel.receiveAsFlow()

    /** Ejercicio tal como está guardado (null si es nuevo). */
    private var saved: Exercise? = null

    /** Imagen que se guardaría ahora (relativa a ImageStore). */
    private var imagePath: String? = null
    private var isClosing = false

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            muscleGroupRepository.observeAll()
                .catch { /* El selector queda vacío; guardar avisará de que falta el grupo. */ }
                .collect { groups ->
                    val options = groups.sortedBy { it.sortOrder }.map { it.toOption() }
                    _uiState.update { it.copy(groups = options) }
                }
        }
    }

    private suspend fun load() {
        val exerciseId = route.exerciseId
        if (exerciseId == null) {
            _uiState.update { it.copy(isLoading = false, muscleGroupId = route.muscleGroupId) }
            return
        }
        val exercise = exerciseRepository.getById(exerciseId)
        if (exercise == null) {
            _uiState.update { it.copy(isLoading = false, isNotFound = true) }
            return
        }
        saved = exercise
        imagePath = exercise.imagePath
        _uiState.update {
            it.copy(
                isLoading = false,
                name = exercise.name,
                notes = exercise.notes.orEmpty(),
                muscleGroupId = exercise.muscleGroupId,
                imageFile = imageStore.fileOrNull(exercise.imagePath),
                isArchived = exercise.isArchived,
            )
        }
    }

    fun onNameChange(value: String) {
        // Si ya se mostró un error, se revalida en vivo; si no, no se molesta mientras escribe.
        _uiState.update { it.copy(name = value, nameError = it.nameError?.let { ValidateExerciseName(value) }) }
    }

    fun onNotesChange(value: String) {
        _uiState.update { it.copy(notes = value) }
    }

    fun onGroupSelected(groupId: String) {
        _uiState.update { it.copy(muscleGroupId = groupId, isGroupMissing = false) }
    }

    /** Resultado del Photo Picker (null si el usuario canceló). */
    fun onImagePicked(uri: String?) {
        if (uri != null) replaceImage(uri, cameraTempPath = null)
    }

    /** Resultado de TakePicture sobre el fichero temporal [tempPath] expuesto como [uri]. */
    fun onPhotoTaken(success: Boolean, uri: String, tempPath: String) {
        if (success) {
            replaceImage(uri, cameraTempPath = tempPath)
        } else {
            viewModelScope.launch { deleteTempFile(tempPath) }
        }
    }

    fun onCameraUnavailable(tempPath: String?) {
        viewModelScope.launch {
            tempPath?.let { deleteTempFile(it) }
            showMessage(R.string.exercises_camera_unavailable)
        }
    }

    fun onGalleryUnavailable() {
        viewModelScope.launch { showMessage(R.string.exercises_gallery_unavailable) }
    }

    fun onRemoveImage() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            deletePendingImage()
            imagePath = null
            _uiState.update { it.copy(imageFile = null) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (!state.canEdit || isClosing) return
        val nameError = ValidateExerciseName(state.name)
        val groupId = state.muscleGroupId
        if (nameError != null || groupId == null) {
            _uiState.update { it.copy(nameError = nameError, isGroupMissing = groupId == null) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val previous = saved
            val now = time.now()
            val exercise = Exercise(
                id = previous?.id ?: ids.newId(),
                muscleGroupId = groupId,
                name = state.name.trim(),
                notes = state.notes.trim().ifEmpty { null },
                imagePath = imagePath,
                isArchived = previous?.isArchived ?: false,
                createdAt = previous?.createdAt ?: now,
                updatedAt = now,
            )
            try {
                exerciseRepository.upsert(exercise)
            } catch (ignored: SQLException) {
                _uiState.update { it.copy(isSaving = false) }
                showMessage(R.string.exercises_save_error)
                return@launch
            }
            saved = exercise
            val oldImage = previous?.imagePath
            if (oldImage != null && oldImage != exercise.imagePath) imageStore.delete(oldImage)
            close()
        }
    }

    /** Salida sin guardar: descarta la imagen nueva que no llegó a guardarse. */
    fun onBack() {
        if (isClosing || _uiState.value.isSaving) return
        viewModelScope.launch {
            deletePendingImage()
            imagePath = saved?.imagePath
            close()
        }
    }

    fun onToggleArchived() {
        val current = saved ?: return
        val archived = !current.isArchived
        viewModelScope.launch {
            exerciseRepository.setArchived(current.id, archived)
            saved = current.copy(isArchived = archived)
            _uiState.update { it.copy(isArchived = archived) }
            showMessage(if (archived) R.string.exercises_archived_done else R.string.exercises_unarchived_done)
        }
    }

    override fun onCleared() {
        // Red de seguridad si la pantalla desaparece sin pasar por onBack (no puede suspender).
        if (!isClosing) pendingImage()?.let { path -> imageStore.fileOrNull(path)?.delete() }
        super.onCleared()
    }

    private fun replaceImage(sourceUri: String, cameraTempPath: String?) {
        if (_uiState.value.isBusy) {
            cameraTempPath?.let { viewModelScope.launch { deleteTempFile(it) } }
            return
        }
        _uiState.update { it.copy(isImageBusy = true) }
        viewModelScope.launch {
            try {
                val newPath = imageStore.saveResized(sourceUri)
                deletePendingImage()
                imagePath = newPath
                _uiState.update { it.copy(imageFile = imageStore.fileOrNull(newPath)) }
            } catch (ignored: IOException) {
                showMessage(R.string.exercises_image_error)
            } catch (ignored: SecurityException) {
                showMessage(R.string.exercises_image_error)
            } catch (ignored: IllegalArgumentException) {
                showMessage(R.string.exercises_image_error)
            } finally {
                cameraTempPath?.let { deleteTempFile(it) }
                _uiState.update { it.copy(isImageBusy = false) }
            }
        }
    }

    /** Imagen elegida en esta edición que aún no está guardada en el ejercicio. */
    private fun pendingImage(): String? = imagePath?.takeIf { it != saved?.imagePath }

    private suspend fun deletePendingImage() {
        pendingImage()?.let { imageStore.delete(it) }
    }

    private suspend fun deleteTempFile(path: String) {
        withContext(io) { File(path).delete() }
    }

    private suspend fun close() {
        isClosing = true
        eventChannel.send(ExerciseEditEvent.Close)
    }

    private suspend fun showMessage(resId: Int) {
        eventChannel.send(ExerciseEditEvent.ShowMessage(UiMessage(resId)))
    }
}
