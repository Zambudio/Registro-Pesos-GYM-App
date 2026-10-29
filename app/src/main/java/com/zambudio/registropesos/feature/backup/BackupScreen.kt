package com.zambudio.registropesos.feature.backup

import android.content.ClipData
import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zambudio.registropesos.R
import com.zambudio.registropesos.core.theme.GymTheme
import com.zambudio.registropesos.core.ui.ConfirmDialog
import com.zambudio.registropesos.core.ui.ObserveAsEvents
import com.zambudio.registropesos.core.util.formatDateTime
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.backup.TableReport
import com.zambudio.registropesos.feature.backup.io.BackupZipFormat
import kotlinx.coroutines.launch

@Composable
fun BackupRoute(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupZipFormat.MIME_TYPE),
    ) { uri -> viewModel.saveTo(uri?.toString()) }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onImportFileChosen(uri?.toString())
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is BackupEvent.Share -> {
                val uri = event.uri.toUri()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = BackupZipFormat.MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.backup_share_chooser)))
            }
            BackupEvent.Saved -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.backup_saved))
            }
            is BackupEvent.Error -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(event.error.messageRes()))
            }
        }
    }

    // Mientras hay una operación en curso, "atrás" del sistema no cierra la pantalla a medias.
    BackHandler(enabled = state.isWorking) {}

    BackupScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onExport = viewModel::export,
        onSaveAs = { state.exported?.let { saveLauncher.launch(it.fileName) } },
        onShare = viewModel::share,
        onImport = { openLauncher.launch(BackupZipFormat.importMimeTypes) },
        onConfirmImport = viewModel::confirmImport,
        onCancelImport = viewModel::cancelImport,
        onDismissReport = viewModel::dismissReport,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onImport: () -> Unit,
    onConfirmImport: () -> Unit,
    onCancelImport: () -> Unit,
    onDismissReport: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !state.isWorking) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.operation?.let { WorkingIndicator(it) }
            state.outcome?.let { ImportReportCard(it, onDismissReport) }
            ExportCard(state, onExport = onExport, onSaveAs = onSaveAs, onShare = onShare)
            SectionCard(
                title = stringResource(R.string.backup_import_title),
                body = stringResource(R.string.backup_import_body),
            ) {
                Button(onClick = onImport, enabled = !state.isWorking) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null)
                    Text(stringResource(R.string.backup_import_action), modifier = Modifier.padding(start = 8.dp))
                }
            }
            SectionCard(
                title = stringResource(R.string.backup_auto_title),
                body = stringResource(R.string.backup_auto_body),
            )
        }
    }

    state.preview?.let { preview ->
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            message = stringResource(
                R.string.backup_import_confirm_message,
                formatDateTime(preview.exportedAt),
                preview.appVersion,
                pluralStringResource(R.plurals.backup_count_exercises, preview.exerciseCount, preview.exerciseCount),
                pluralStringResource(R.plurals.backup_count_routines, preview.routineCount, preview.routineCount),
                pluralStringResource(R.plurals.backup_count_sessions, preview.sessionCount, preview.sessionCount),
            ),
            confirmLabel = stringResource(R.string.backup_import_confirm_action),
            onConfirm = onConfirmImport,
            onDismiss = onCancelImport,
        )
    }
}

@Composable
private fun ExportCard(state: BackupUiState, onExport: () -> Unit, onSaveAs: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    SectionCard(
        title = stringResource(R.string.backup_export_title),
        body = stringResource(R.string.backup_export_body),
    ) {
        Button(onClick = onExport, enabled = !state.isWorking) {
            Icon(Icons.Filled.FileDownload, contentDescription = null)
            Text(stringResource(R.string.backup_export_action), modifier = Modifier.padding(start = 8.dp))
        }
        state.exported?.let { exported ->
            Text(
                stringResource(
                    R.string.backup_export_ready,
                    exported.fileName,
                    Formatter.formatShortFileSize(context, exported.sizeBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (exported.imagesMissing > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.backup_export_images_missing,
                        exported.imagesMissing,
                        exported.imagesMissing,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSaveAs, enabled = !state.isWorking) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text(stringResource(R.string.backup_save_as), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onShare, enabled = !state.isWorking) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.backup_share), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkingIndicator(operation: BackupOperation) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(operation.labelRes()), style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportReportCard(outcome: ImportOutcome, onDismiss: () -> Unit) {
    val report = outcome.report
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.backup_report_title), style = MaterialTheme.typography.titleMedium)
            ReportRow(R.string.backup_report_groups, report.muscleGroups)
            ReportRow(R.string.backup_report_exercises, report.exercises)
            ReportRow(R.string.backup_report_routines, report.routines)
            ReportRow(R.string.backup_report_sessions, report.sessions)
            if (report.deleted > 0) Text(stringResource(R.string.backup_report_deleted, report.deleted))
            if (outcome.imagesCopied > 0) Text(stringResource(R.string.backup_report_images, outcome.imagesCopied))
            if (outcome.imagesMissing > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.backup_report_images_missing,
                        outcome.imagesMissing,
                        outcome.imagesMissing,
                    ),
                )
            }
            if (report.warnings.isNotEmpty()) {
                Text(stringResource(R.string.backup_report_warnings), style = MaterialTheme.typography.titleSmall)
                report.warnings.forEach { warning ->
                    Text("• $warning", style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_report_close)) }
        }
    }
}

@Composable
private fun ReportRow(labelRes: Int, table: TableReport) {
    Text(
        stringResource(
            R.string.backup_report_row,
            stringResource(labelRes),
            table.inserted,
            table.updated,
            table.skipped,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun SectionCard(title: String, body: String, content: @Composable () -> Unit = {}) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

private fun BackupOperation.labelRes(): Int = when (this) {
    BackupOperation.EXPORTING -> R.string.backup_working_exporting
    BackupOperation.SAVING -> R.string.backup_working_saving
    BackupOperation.READING -> R.string.backup_working_reading
    BackupOperation.IMPORTING -> R.string.backup_working_importing
}

private fun BackupError.messageRes(): Int = when (this) {
    BackupError.EXPORT_FAILED -> R.string.backup_error_export
    BackupError.SAVE_FAILED -> R.string.backup_error_save
    BackupError.SHARE_FAILED -> R.string.backup_error_share
    BackupError.READ_FAILED -> R.string.backup_error_read
    BackupError.INVALID_FILE -> R.string.backup_error_invalid
    BackupError.UNSUPPORTED_VERSION -> R.string.backup_error_version
    BackupError.IMPORT_FAILED -> R.string.backup_error_import
}

@Preview(showBackground = true)
@Composable
private fun BackupScreenPreview() {
    GymTheme {
        BackupScreen(
            state = BackupUiState(
                exported = ExportedBackup("gym-backup-20261029-1830.zip", "", 524_288L, imagesMissing = 0),
                outcome = ImportOutcome(
                    report = ImportReport(exercises = TableReport(inserted = 3, updated = 1)),
                    imagesCopied = 2,
                    imagesMissing = 0,
                ),
            ),
            onBack = {},
            onExport = {},
            onSaveAs = {},
            onShare = {},
            onImport = {},
            onConfirmImport = {},
            onCancelImport = {},
            onDismissReport = {},
        )
    }
}
