package com.zambudio.registropesos.feature.workout.session

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.util.formatKg
import com.zambudio.registropesos.feature.workout.common.formatWeightInput
import com.zambudio.registropesos.feature.workout.common.parseRepsText
import com.zambudio.registropesos.feature.workout.common.parseWeightText

private const val DONE_ROW_ALPHA = 0.35f

@Composable
internal fun ExerciseCard(
    exercise: WorkoutExerciseUi,
    expanded: Boolean,
    weightStepKg: Double,
    enabled: Boolean,
    actions: WorkoutActions,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        ExerciseHeader(exercise, expanded, onClick = { actions.onToggleExercise(exercise.exerciseId) })
        if (expanded) {
            Column(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                PreviousRow(exercise, enabled, onCopy = { actions.onCopyPrevious(exercise.exerciseId) })
                SetHeaderRow()
                exercise.sets.forEach { set ->
                    key(set.setId) {
                        SetRow(set, weightStepKg, enabled, actions)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = { actions.onAddSet(exercise.exerciseId) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.workout_add_set), Modifier.padding(start = 4.dp))
                    }
                    TextButton(
                        onClick = { actions.onRemoveSet(exercise.exerciseId) },
                        enabled = enabled && exercise.canRemoveSet,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.workout_remove_set))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseHeader(exercise: WorkoutExerciseUi, expanded: Boolean, onClick: () -> Unit) {
    val stateLabel = stringResource(if (expanded) R.string.workout_collapse else R.string.workout_expand)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stateLabel, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = pluralStringResource(
                    R.plurals.workout_sets_done,
                    exercise.sets.size,
                    exercise.doneCount,
                    exercise.sets.size,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
    }
}

@Composable
private fun PreviousRow(exercise: WorkoutExerciseUi, enabled: Boolean, onCopy: () -> Unit) {
    when (exercise.hasPrevious) {
        true -> Row(verticalAlignment = Alignment.CenterVertically) {
            val noValue = stringResource(R.string.workout_no_value)
            val summary = exercise.previousSets.orEmpty()
                .map { ghost ->
                    stringResource(
                        R.string.workout_weight_x_reps,
                        ghost.weightKg?.let(::formatKg) ?: noValue,
                        ghost.reps?.toString() ?: noValue,
                    )
                }
                .joinToString(" · ")
            Text(
                text = stringResource(R.string.workout_previous_label, summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            TextButton(onClick = onCopy, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Text(stringResource(R.string.workout_copy_previous), Modifier.padding(start = 4.dp))
            }
        }
        false -> Text(
            text = stringResource(R.string.workout_previous_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        null -> Unit
    }
}

@Composable
private fun SetHeaderRow() {
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.workout_column_set),
            Modifier.width(SET_NUMBER_WIDTH),
            style = style,
            color = color,
        )
        Text(
            stringResource(R.string.workout_column_kg),
            Modifier.weight(WEIGHT_GROUP_WEIGHT),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.workout_column_reps),
            Modifier.weight(REPS_WEIGHT),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.workout_column_done),
            Modifier.width(CHECK_WIDTH),
            style = style,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

private val SET_NUMBER_WIDTH = 28.dp
private val CHECK_WIDTH = 48.dp
private const val WEIGHT_GROUP_WEIGHT = 2.4f
private const val WEIGHT_FIELD_WEIGHT = 1f
private const val REPS_WEIGHT = 0.9f

@Composable
private fun SetRow(set: WorkoutSetUi, weightStepKg: Double, enabled: Boolean, actions: WorkoutActions) {
    val focusManager = LocalFocusManager.current
    val step = formatKg(weightStepKg)
    val rowColor = if (set.isDone) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = DONE_ROW_ALPHA)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowColor, MaterialTheme.shapes.small)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = set.number.toString(),
            modifier = Modifier.width(SET_NUMBER_WIDTH),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.weight(WEIGHT_GROUP_WEIGHT), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { actions.onStepWeight(set.setId, false) },
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
            ) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.workout_weight_minus, step))
            }
            NumberField(
                value = set.weightKg,
                parse = ::parseWeightText,
                format = ::formatWeightInput,
                placeholder = set.ghostWeightKg?.let(::formatWeightInput),
                onValueChange = { actions.onWeightChange(set.setId, it) },
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next,
                onIme = { focusManager.moveFocus(FocusDirection.Next) },
                enabled = enabled,
                description = stringResource(R.string.workout_weight_field, set.number),
                modifier = Modifier.weight(WEIGHT_FIELD_WEIGHT),
            )
            IconButton(
                onClick = { actions.onStepWeight(set.setId, true) },
                enabled = enabled,
                modifier = Modifier.focusProperties { canFocus = false },
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workout_weight_plus, step))
            }
        }
        NumberField(
            value = set.reps,
            parse = ::parseRepsText,
            format = { it?.toString().orEmpty() },
            placeholder = set.ghostReps?.toString(),
            onValueChange = { actions.onRepsChange(set.setId, it) },
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            onIme = { focusManager.clearFocus() },
            enabled = enabled,
            description = stringResource(R.string.workout_reps_field, set.number),
            modifier = Modifier.weight(REPS_WEIGHT).padding(start = 4.dp),
        )
        val doneDescription = stringResource(R.string.workout_set_done, set.number)
        Checkbox(
            checked = set.isDone,
            onCheckedChange = { actions.onToggleDone(set.setId) },
            enabled = enabled,
            modifier = Modifier
                .width(CHECK_WIDTH)
                .focusProperties { canFocus = false }
                .semantics { contentDescription = doneDescription },
        )
    }
}

/**
 * Sincroniza el texto de un campo con el valor del estado sin pisar lo que se teclea.
 *
 * El texto es local y solo se reescribe cuando el valor cambia por fuera (±2,5, copiar anterior)
 * y el texto ya no lo representa: "82," o "82,50" no se convierten en "82" y el cursor no salta.
 * Los valores que llegan con retraso de pulsaciones anteriores ("8" mientras ya pone "82") son
 * ecos de lo tecleado y se ignoran.
 */
internal class NumberFieldSync<T>(private val parse: (String) -> T?) {
    // Lista y no ArrayDeque: ArrayDeque.contains(null) devuelve true estando vacía (recorre el búfer).
    private val pendingEchoes = mutableListOf<T?>()

    /** Registra una pulsación; devuelve true si el texto cambió y hay que avisar al ViewModel. */
    fun onTyped(previousText: String, newText: String): Boolean {
        if (previousText == newText) return false
        pendingEchoes += parse(newText)
        if (pendingEchoes.size > MAX_PENDING_ECHOES) pendingEchoes.removeAt(0)
        return true
    }

    /** Llega un valor del estado: devuelve el texto nuevo a mostrar o null si hay que conservar el actual. */
    fun onValue(currentText: String, value: T?, format: (T?) -> String): String? = when {
        parse(currentText) == value -> {
            pendingEchoes.clear()
            null
        }
        value in pendingEchoes -> null
        else -> {
            pendingEchoes.clear()
            format(value)
        }
    }

    private companion object {
        const val MAX_PENDING_ECHOES = 16
    }
}

@Composable
private fun <T> NumberField(
    value: T?,
    parse: (String) -> T?,
    format: (T?) -> String,
    placeholder: String?,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onIme: () -> Unit,
    enabled: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val text = format(value)
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }
    val sync = remember { NumberFieldSync(parse) }
    LaunchedEffect(value) {
        sync.onValue(field.text, value, format)?.let { text ->
            field = TextFieldValue(text, TextRange(text.length))
        }
    }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            val changed = sync.onTyped(field.text, next.text)
            field = next
            if (changed) onValueChange(next.text)
        },
        modifier = modifier.semantics { contentDescription = description },
        enabled = enabled,
        singleLine = true,
        isError = field.text.isNotBlank() && parse(field.text) == null,
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        placeholder = placeholder?.let {
            {
                Text(
                    text = it,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onIme() }, onDone = { onIme() }),
    )
}
