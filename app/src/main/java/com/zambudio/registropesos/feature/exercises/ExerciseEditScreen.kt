package com.zambudio.registropesos.feature.exercises

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.ErrorState
import com.zambudio.registropesos.core.ui.LoadingState
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.domain.usecase.ExerciseNameError
import com.zambudio.registropesos.domain.usecase.ValidateExerciseName
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExerciseEditorRoute(
    onClose: () -> Unit,
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExerciseEditEvent.Close -> onClose()
            is ExerciseEditEvent.ShowMessage -> {
                val text = context.text(event.message)
                scope.launch { snackbarHostState.showSnackbar(text) }
            }
        }
    }

    // Ruta del temporal de la cámara: sobrevive a la recreación de la actividad mientras la cámara está abierta.
    var pendingCameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onImagePicked(uri?.toString())
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (path != null) viewModel.onPhotoTaken(success, cameraUriFor(context, File(path)).toString(), path)
    }

    BackHandler(onBack = viewModel::onBack)

    ExerciseEditScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = viewModel::onBack,
        onNameChange = viewModel::onNameChange,
        onNotesChange = viewModel::onNotesChange,
        onGroupSelected = viewModel::onGroupSelected,
        onPickFromGallery = {
            val request = PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
            try {
                galleryLauncher.launch(request)
            } catch (ignored: ActivityNotFoundException) {
                viewModel.onGalleryUnavailable()
            }
        },
        onTakePhoto = {
            val file = createCameraTempFile(context)
            pendingCameraPath = file.absolutePath
            try {
                cameraLauncher.launch(cameraUriFor(context, file))
            } catch (ignored: ActivityNotFoundException) {
                pendingCameraPath = null
                viewModel.onCameraUnavailable(file.absolutePath)
            } catch (ignored: IllegalArgumentException) {
                // FileProvider no reconoce la ruta: se trata igual que una cámara no disponible.
                pendingCameraPath = null
                viewModel.onCameraUnavailable(file.absolutePath)
            }
        },
        onRemoveImage = viewModel::onRemoveImage,
        onToggleArchived = viewModel::onToggleArchived,
        onSave = viewModel::onSave,
        onDiscardConfirm = viewModel::onDiscardConfirm,
        onDiscardDismiss = viewModel::onDiscardDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditScreen(
    state: ExerciseEditUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onGroupSelected: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: () -> Unit,
    onToggleArchived: () -> Unit,
    onSave: () -> Unit,
    onDiscardConfirm: () -> Unit,
    onDiscardDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var showArchiveConfirm by rememberSaveable { mutableStateOf(false) }
    val canArchive = !state.isNew && !state.isLoading && !state.isNotFound

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) {
                                R.string.exercises_edit_title_new
                            } else {
                                R.string.exercises_edit_title_existing
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (canArchive) {
                        IconButton(onClick = { showArchiveConfirm = true }, enabled = !state.isBusy) {
                            Icon(
                                imageVector = if (state.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                                contentDescription = stringResource(
                                    if (state.isArchived) R.string.exercises_unarchive else R.string.exercises_archive,
                                ),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> LoadingState(contentModifier)
            state.isNotFound -> ErrorState(contentModifier, message = stringResource(R.string.exercises_not_found))
            else -> ExerciseForm(
                state = state,
                modifier = contentModifier,
                onNameChange = onNameChange,
                onNotesChange = onNotesChange,
                onGroupSelected = onGroupSelected,
                onPickFromGallery = onPickFromGallery,
                onTakePhoto = onTakePhoto,
                onRemoveImage = onRemoveImage,
                onSave = onSave,
            )
        }
    }

    if (showArchiveConfirm) {
        ArchiveConfirmDialog(
            isArchived = state.isArchived,
            onConfirm = {
                showArchiveConfirm = false
                onToggleArchived()
            },
            onDismiss = { showArchiveConfirm = false },
        )
    }

    if (state.showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.exercises_discard_title),
            message = stringResource(R.string.exercises_discard_message),
            confirmLabel = stringResource(R.string.exercises_discard_confirm),
            dismissLabel = stringResource(R.string.exercises_discard_keep_editing),
            onConfirm = onDiscardConfirm,
            onDismiss = onDiscardDismiss,
        )
    }
}

@Composable
private fun ExerciseForm(
    state: ExerciseEditUiState,
    modifier: Modifier,
    onNameChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onGroupSelected: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: () -> Unit,
    onSave: () -> Unit,
) {
    var showGroupPicker by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.isArchived) {
            Text(
                text = stringResource(R.string.exercises_archived_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.exercises_field_name)) },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { error -> { Text(nameErrorText(error)) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        GroupSelector(state = state, onClick = { showGroupPicker = true })
        OutlinedTextField(
            value = state.notes,
            onValueChange = onNotesChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.exercises_field_notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        ImageSection(
            state = state,
            onPickFromGallery = onPickFromGallery,
            onTakePhoto = onTakePhoto,
            onRemoveImage = onRemoveImage,
        )
        Button(
            onClick = onSave,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.common_save))
        }
    }

    if (showGroupPicker) {
        MuscleGroupPickerDialog(
            groups = state.groups,
            onSelect = { groupId ->
                showGroupPicker = false
                onGroupSelected(groupId)
            },
            onDismiss = { showGroupPicker = false },
        )
    }
}

@Composable
private fun nameErrorText(error: ExerciseNameError): String = when (error) {
    ExerciseNameError.EMPTY -> stringResource(R.string.exercises_error_name_empty)
    ExerciseNameError.TOO_LONG ->
        stringResource(R.string.exercises_error_name_too_long, ValidateExerciseName.MAX_LENGTH)
}

@Composable
private fun GroupSelector(state: ExerciseEditUiState, onClick: () -> Unit) {
    Column {
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                overlineContent = { Text(stringResource(R.string.exercises_field_group)) },
                headlineContent = {
                    Text(state.selectedGroupName ?: stringResource(R.string.exercises_field_group_none))
                },
                modifier = Modifier.clickableRow(onClick),
            )
        }
        if (state.isGroupMissing) {
            Text(
                text = stringResource(R.string.exercises_error_group_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun ImageSection(
    state: ExerciseEditUiState,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.exercises_image_label), style = MaterialTheme.typography.titleSmall)
        val imageFile = state.imageFile
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = stringResource(R.string.exercises_image_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExerciseThumbnail(imageFile = null, size = 64.dp)
                Text(
                    text = stringResource(R.string.exercises_image_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
        if (state.isImageBusy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPickFromGallery, enabled = !state.isBusy) {
                ButtonIcon(Icons.Filled.PhotoLibrary)
                Text(stringResource(R.string.exercises_image_gallery), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onTakePhoto, enabled = !state.isBusy) {
                ButtonIcon(Icons.Filled.PhotoCamera)
                Text(stringResource(R.string.exercises_image_camera), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (imageFile != null) {
            TextButton(onClick = onRemoveImage, enabled = !state.isBusy) {
                ButtonIcon(Icons.Filled.Delete)
                Text(stringResource(R.string.exercises_image_remove), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun ArchiveConfirmDialog(isArchived: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (isArchived) {
        ConfirmDialog(
            title = stringResource(R.string.exercises_unarchive_confirm_title),
            message = stringResource(R.string.exercises_unarchive_confirm_message),
            confirmLabel = stringResource(R.string.exercises_unarchive),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    } else {
        ConfirmDialog(
            title = stringResource(R.string.exercises_archive_confirm_title),
            message = stringResource(R.string.exercises_archive_confirm_message),
            confirmLabel = stringResource(R.string.exercises_archive),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ButtonIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
}

@Preview(showBackground = true)
@Composable
private fun ExerciseEditScreenPreview() {
    GymTheme {
        ExerciseEditScreen(
            state = ExerciseEditUiState(
                isLoading = false,
                isNew = false,
                name = "Press banca",
                muscleGroupId = "g1",
                groups = listOf(MuscleGroupOption("g1", "Pecho")),
                nameError = null,
            ),
            onBack = {},
            onNameChange = {},
            onNotesChange = {},
            onGroupSelected = {},
            onPickFromGallery = {},
            onTakePhoto = {},
            onRemoveImage = {},
            onToggleArchived = {},
            onSave = {},
            onDiscardConfirm = {},
            onDiscardDismiss = {},
        )
    }
}
