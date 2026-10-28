package com.zambudio.registropesos.feature.backup

import com.zambudio.registropesos.domain.backup.ImportReport

enum class BackupOperation { EXPORTING, SAVING, READING, IMPORTING }

/** ZIP ya generado en caché, listo para guardar o compartir. */
data class ExportedBackup(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val imagesMissing: Int,
)

/** Resumen del fichero elegido, para confirmar antes de importar. */
data class ImportPreview(
    val exportedAt: Long,
    val appVersion: String,
    val exerciseCount: Int,
    val routineCount: Int,
    val sessionCount: Int,
)

data class ImportOutcome(
    val report: ImportReport,
    val imagesCopied: Int,
    val imagesMissing: Int,
)

data class BackupUiState(
    val operation: BackupOperation? = null,
    val exported: ExportedBackup? = null,
    val preview: ImportPreview? = null,
    val outcome: ImportOutcome? = null,
) {
    val isWorking: Boolean get() = operation != null
}

enum class BackupError {
    EXPORT_FAILED,
    SAVE_FAILED,
    SHARE_FAILED,
    READ_FAILED,
    INVALID_FILE,
    UNSUPPORTED_VERSION,
    IMPORT_FAILED,
}

sealed interface BackupEvent {
    data class Share(val uri: String) : BackupEvent
    data object Saved : BackupEvent
    data class Error(val error: BackupError) : BackupEvent
}
