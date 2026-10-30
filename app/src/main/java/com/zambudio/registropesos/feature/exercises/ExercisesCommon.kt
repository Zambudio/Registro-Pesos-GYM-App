package com.zambudio.registropesos.feature.exercises

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.ui.DialogOption
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

/**
 * Miniatura del ejercicio. Debajo siempre se pinta el icono genérico sobre un fondo suave, así se ve
 * mientras la foto carga, si no hay foto o si no se puede leer; la foto, cuando llega, lo tapa.
 */
@Composable
fun ExerciseThumbnail(imageFile: File?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * ThumbnailCornerRatio))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = PlaceholderAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * PlaceholderIconRatio),
        )
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private const val ThumbnailCornerRatio = 0.25f
private const val PlaceholderAlpha = 0.55f
private const val PlaceholderIconRatio = 0.45f

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
                        DialogOption(text = group.name, icon = Icons.Filled.Category, onClick = { onSelect(group.id) })
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
