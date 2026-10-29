package com.zambudio.registropesos.feature.workout.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.zambudio.registropesos.R

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
        Surface(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.workout_picker_title, picker.muscleGroupName),
                        style = MaterialTheme.typography.titleLarge,
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
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.workout_picker_empty, picker.muscleGroupName),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(onClick = onCreateExercise) {
                            Text(stringResource(R.string.workout_picker_create))
                        }
                    }
                    else -> PickerGrid(picker, onPick, onCreateExercise)
                }
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
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(PICKER_COLUMNS),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(picker.exercises, key = { it.exerciseId }) { exercise ->
            PickerItem(exercise, onClick = { onPick(exercise.exerciseId) })
        }
        item(key = "create") {
            OutlinedButton(onClick = onCreateExercise, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.workout_picker_create))
            }
        }
    }
}

@Composable
private fun PickerItem(exercise: PickerExerciseUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val outline = if (exercise.alreadyAdded) {
        Modifier.border(2.dp, MaterialTheme.colorScheme.secondary, shape)
    } else {
        Modifier
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (exercise.alreadyAdded) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(outline)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box {
            PickerImage(exercise)
            if (exercise.alreadyAdded) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = NAME_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            if (exercise.alreadyAdded) {
                Text(
                    text = stringResource(R.string.workout_picker_added),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun PickerImage(exercise: PickerExerciseUi) {
    val imageModifier = Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .background(MaterialTheme.colorScheme.surface)
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
            modifier = imageModifier,
        )
    } else {
        Box(imageModifier, contentAlignment = Alignment.Center) {
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
