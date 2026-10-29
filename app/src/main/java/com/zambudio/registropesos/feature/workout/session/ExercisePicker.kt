package com.zambudio.registropesos.feature.workout.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.ui.Pill
import com.zambudio.registropesos.core.ui.PhotoBackground
import com.zambudio.registropesos.core.ui.ScreenPadding

// Selector de ejercicios de un grupo durante la sesión: rejilla con la foto grande y el nombre
// legible. Elegir uno lo añade y abre su registro; uno ya añadido solo se abre (no se duplica).

private const val PICKER_COLUMNS = 2
private const val NAME_MAX_LINES = 3

@Composable
internal fun ExercisePickerDialog(
    picker: ExercisePickerUi,
    onPick: (exerciseId: String) -> Unit,
    onCreateExercise: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ExercisePickerContent(picker, onPick, onCreateExercise, onDismiss, Modifier.systemBarsPadding())
    }
}

/** Contenido del selector, sin el diálogo (también lo usan la galería de capturas y las vistas previas). */
@Composable
internal fun ExercisePickerContent(
    picker: ExercisePickerUi,
    onPick: (exerciseId: String) -> Unit,
    onCreateExercise: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding, end = 4.dp, top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.workout_picker_title, picker.muscleGroupName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.workout_picker_close))
                }
            }
            when {
                picker.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                picker.exercises.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workout_picker_empty, picker.muscleGroupName),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    CreateExerciseButton(onCreateExercise)
                }
                else -> PickerGrid(picker, onPick, onCreateExercise)
            }
        }
    }
}

@Composable
private fun PickerGrid(picker: ExercisePickerUi, onPick: (String) -> Unit, onCreateExercise: () -> Unit) {
    Text(
        text = stringResource(R.string.workout_picker_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = ScreenPadding),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(PICKER_COLUMNS),
        contentPadding = PaddingValues(ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(picker.exercises, key = { it.exerciseId }) { exercise ->
            PickerItem(exercise, onClick = { onPick(exercise.exerciseId) })
        }
        item(key = "create", span = { GridItemSpan(maxLineSpan) }) {
            CreateExerciseButton(onCreateExercise)
        }
    }
}

@Composable
private fun CreateExerciseButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.workout_picker_create), Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PickerItem(exercise: PickerExerciseUi, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = if (exercise.alreadyAdded) {
            BorderStroke(2.dp, colors.primary)
        } else {
            BorderStroke(1.dp, colors.outlineVariant)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box {
                PickerImage(exercise)
                if (exercise.alreadyAdded) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(colors.surfaceContainerLow, MaterialTheme.shapes.medium),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                if (exercise.alreadyAdded) {
                    Pill(
                        text = stringResource(R.string.workout_picker_added),
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerImage(exercise: PickerExerciseUi) {
    val file = exercise.imageFile
    if (file != null) {
        val fallback = rememberVectorPainter(Icons.Filled.FitnessCenter)
        AsyncImage(
            model = file,
            // El nombre va debajo en texto; la foto no repite la descripción.
            contentDescription = null,
            contentScale = ContentScale.Fit,
            placeholder = fallback,
            error = fallback,
            // Las fotos del catálogo tienen fondo blanco: el marco blanco las integra en claro y en oscuro.
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(PhotoBackground)
                .padding(6.dp),
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    stringResource(R.string.workout_picker_no_image),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
