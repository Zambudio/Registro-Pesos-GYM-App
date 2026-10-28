package com.zambudio.registropesos.feature.exercises

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zambudio.registropesos.R
import com.zambudio.registropesos.domain.model.MuscleGroup
import com.zambudio.registropesos.domain.repository.ImageStore
import java.io.File
import java.text.Normalizer
import java.util.Locale

// Piezas compartidas por las pantallas de ejercicios y grupos musculares.

/** Mensaje one-shot (Snackbar): recurso de texto con, como mucho, un argumento de formato. */
data class UiMessage(@param:StringRes val resId: Int, val arg: String? = null)

internal fun Context.text(message: UiMessage): String =
    if (message.arg == null) getString(message.resId) else getString(message.resId, message.arg)

/** Grupo muscular tal como lo necesitan los selectores. */
data class MuscleGroupOption(val id: String, val name: String)

internal fun MuscleGroup.toOption() = MuscleGroupOption(id, name)

private val CombiningMarks = Regex("\\p{Mn}+")

/** Clave de búsqueda: sin acentos, en minúsculas y sin espacios en los extremos. */
internal fun normalizeForSearch(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(CombiningMarks, "")
        .lowercase(Locale.ROOT)

/** Fichero de la imagen o null si no hay o la ruta guardada no es válida. */
internal fun ImageStore.fileOrNull(relativePath: String?): File? =
    relativePath?.let { path -> runCatching { resolve(path) }.getOrNull() }

/** Miniatura del ejercicio o un icono genérico si no tiene imagen (o no se puede cargar). */
@Composable
fun ExerciseThumbnail(imageFile: File?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(8.dp)
    val sized = modifier
        .size(size)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (imageFile != null) {
        val fallback = rememberVectorPainter(Icons.Filled.FitnessCenter)
        AsyncImage(
            model = imageFile,
            contentDescription = null,
            modifier = sized,
            contentScale = ContentScale.Crop,
            placeholder = fallback,
            error = fallback,
        )
    } else {
        Box(modifier = sized, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Diálogo con la lista de grupos musculares para elegir uno. */
@Composable
fun MuscleGroupPickerDialog(
    groups: List<MuscleGroupOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exercises_pick_group_title)) },
        text = {
            if (groups.isEmpty()) {
                Text(stringResource(R.string.exercises_no_groups))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(groups, key = { it.id }) { group ->
                        ListItem(
                            headlineContent = { Text(group.name) },
                            modifier = Modifier.clickableRow { onSelect(group.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Fila pulsable con altura mínima accesible. */
internal fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this
        .heightIn(min = 48.dp)
        .clickable(onClick = onClick)
