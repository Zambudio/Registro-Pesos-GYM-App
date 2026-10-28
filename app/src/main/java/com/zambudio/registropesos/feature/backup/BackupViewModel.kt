package com.zambudio.registropesos.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zambudio.registropesos.BuildConfig
import com.zambudio.registropesos.core.di.IoDispatcher
import com.zambudio.registropesos.domain.backup.BackupSnapshot
import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.repository.BackupRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.usecase.backup.BackupSerializer
import com.zambudio.registropesos.domain.usecase.backup.SetsCsvWriter
import com.zambudio.registropesos.domain.usecase.backup.UnsupportedBackupVersionException
import com.zambudio.registropesos.domain.util.TimeProvider
import com.zambudio.registropesos.feature.backup.io.BackupFileStore
import com.zambudio.registropesos.feature.backup.io.BackupImageSource
import com.zambudio.registropesos.feature.backup.io.BackupZipFormat
import com.zambudio.registropesos.feature.backup.io.BackupZipReader
import com.zambudio.registropesos.feature.backup.io.BackupZipWriter
import com.zambudio.registropesos.feature.backup.io.InvalidBackupFileException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Exportar: instantánea de la BD -> ZIP en caché -> "Guardar en…" (SAF) o Share Sheet.
 * Importar: ZIP elegido con SAF -> resumen -> confirmación -> merge -> copia de imágenes -> informe.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val imageStore: ImageStore,
    private val files: BackupFileStore,
    private val time: TimeProvider,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    private val writer = BackupZipWriter()
    private val reader = BackupZipReader()
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val eventChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events: Flow<BackupEvent> = eventChannel.receiveAsFlow()

    private var pendingSnapshot: BackupSnapshot? = null
    private var pendingUri: String? = null

    fun export() = runOperation(BackupOperation.EXPORTING) {
        attempt {
            withContext(io) { writeExport() }
        }.onSuccess { exported -> _state.update { it.copy(exported = exported) } }
            .onFailure { sendError(BackupError.EXPORT_FAILED) }
    }

    /** Destino elegido en "Guardar en…" (null si el usuario canceló). */
    fun saveTo(uri: String?) {
        val exported = _state.value.exported ?: return
        if (uri == null) return
        runOperation(BackupOperation.SAVING) {
            attempt {
                withContext(io) {
                    File(exported.filePath).inputStream().use { input ->
                        files.openOutput(uri).use { output -> input.copyTo(output) }
                    }
                }
            }.onSuccess { eventChannel.send(BackupEvent.Saved) }
                .onFailure { sendError(BackupError.SAVE_FAILED) }
        }
    }

    fun share() {
        val exported = _state.value.exported ?: return
        viewModelScope.launch {
            attempt { files.shareUri(File(exported.filePath)) }
                .onSuccess { uri -> eventChannel.send(BackupEvent.Share(uri)) }
                .onFailure { sendError(BackupError.SHARE_FAILED) }
        }
    }

    /** Fichero elegido para importar (null si el usuario canceló): lo lee y pide confirmación. */
    fun onImportFileChosen(uri: String?) {
        if (uri == null) return
        runOperation(BackupOperation.READING) {
            attempt {
                withContext(io) {
                    val json = files.openInput(uri).use { reader.readBackupJson(it) }
                    BackupSerializer.decode(json)
                }
            }.onSuccess { snapshot ->
                pendingSnapshot = snapshot
                pendingUri = uri
                _state.update { it.copy(preview = snapshot.toPreview(), outcome = null) }
            }.onFailure { error -> sendError(readErrorOf(error)) }
        }
    }

    fun confirmImport() {
        val snapshot = pendingSnapshot ?: return
        val uri = pendingUri ?: return
        clearPending()
        runOperation(BackupOperation.IMPORTING) {
            attempt { backupRepository.importSnapshot(snapshot) }
                .onSuccess { report ->
                    val outcome = copyImages(uri, report)
                    _state.update { it.copy(outcome = outcome) }
                }
                .onFailure { error ->
                    // El repositorio lanza IllegalArgumentException si el formato es más nuevo que la app.
                    val mapped = if (error is IllegalArgumentException) {
                        BackupError.UNSUPPORTED_VERSION
                    } else {
                        BackupError.IMPORT_FAILED
                    }
                    sendError(mapped)
                }
        }
    }

    fun cancelImport() = clearPending()

    fun dismissReport() = _state.update { it.copy(outcome = null) }

    private fun clearPending() {
        pendingSnapshot = null
        pendingUri = null
        _state.update { it.copy(preview = null) }
    }

    private suspend fun writeExport(): ExportedBackup {
        val now = time.now()
        val snapshot = backupRepository.exportSnapshot(BuildConfig.VERSION_NAME, now)
        val fileName = exportFileName(now, zone)
        val file = files.newExportFile(fileName)
        val images = snapshot.exercises.mapNotNull { exercise ->
            val zipPath = exercise.imageFile ?: return@mapNotNull null
            runCatching { BackupImageSource(zipPath, imageStore.resolve(zipPath)) }.getOrNull()
        }
        val result = file.outputStream().buffered().use { output ->
            writer.write(output, BackupSerializer.encode(snapshot), SetsCsvWriter.write(snapshot, zone), images)
        }
        return ExportedBackup(fileName, file.absolutePath, file.length(), result.imagesMissing)
    }

    /**
     * Segunda pasada por el ZIP para copiar las imágenes de los ejercicios importados. Un fallo aquí
     * no deshace el import (los datos ya están): las imágenes no copiadas se cuentan como ausentes.
     */
    private suspend fun copyImages(uri: String, report: ImportReport): ImportOutcome {
        val wanted = report.imagesToCopy.values.toSet()
        var copied = 0
        if (wanted.isNotEmpty()) {
            attempt {
                withContext(io) {
                    files.openInput(uri).use { input ->
                        reader.forEachImage(input, wanted) { path, bytes ->
                            if (attempt { imageStore.importRaw(path, bytes) }.isSuccess) copied++
                        }
                    }
                }
            }
        }
        return ImportOutcome(report = report, imagesCopied = copied, imagesMissing = wanted.size - copied)
    }

    private fun runOperation(operation: BackupOperation, block: suspend () -> Unit) {
        if (_state.value.isWorking) return
        _state.update { it.copy(operation = operation) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(operation = null) }
            }
        }
    }

    private suspend fun sendError(error: BackupError) = eventChannel.send(BackupEvent.Error(error))

    companion object {
        private val FileNameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

        /** gym-backup-YYYYMMDD-HHmm.zip */
        fun exportFileName(now: Long, zone: ZoneId): String =
            BackupZipFormat.FILE_PREFIX + FileNameFormat.format(Instant.ofEpochMilli(now).atZone(zone)) + ".zip"

        internal fun readErrorOf(error: Throwable): BackupError = when (error) {
            is UnsupportedBackupVersionException -> BackupError.UNSUPPORTED_VERSION
            is InvalidBackupFileException, is SerializationException, is IllegalArgumentException ->
                BackupError.INVALID_FILE
            else -> BackupError.READ_FAILED
        }
    }
}

private fun BackupSnapshot.toPreview() = ImportPreview(
    exportedAt = exportedAt,
    appVersion = appVersion,
    exerciseCount = exercises.size,
    routineCount = routines.size,
    sessionCount = sessions.size,
)

/** runCatching que no se traga la cancelación de la corrutina. */
private inline fun <T> attempt(block: () -> T): Result<T> =
    runCatching(block).onFailure { if (it is CancellationException) throw it }
