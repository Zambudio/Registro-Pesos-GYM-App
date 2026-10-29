package com.zambudio.registropesos.screenshots

import com.zambudio.registropesos.domain.backup.ImportReport
import com.zambudio.registropesos.domain.backup.TableReport
import com.zambudio.registropesos.feature.backup.BackupOperation
import com.zambudio.registropesos.feature.backup.BackupScreen
import com.zambudio.registropesos.feature.backup.BackupUiState
import com.zambudio.registropesos.feature.backup.ExportedBackup
import com.zambudio.registropesos.feature.backup.ImportOutcome
import com.zambudio.registropesos.feature.settings.AboutScreen
import org.junit.Test

/** Galería de copias de seguridad y "Acerca de". */
class ScreenshotGallerySettingsTest : ScreenshotGallery() {

    @Test
    fun backupInitial() = snap("backup") {
        BackupScreen(
            state = BackupUiState(),
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

    @Test
    fun backupExportedWithReport() = snap("backup-exported") {
        BackupScreen(
            state = BackupUiState(
                operation = BackupOperation.SAVING,
                exported = ExportedBackup("gym-backup-20261029-1830.zip", "", 524_288L, imagesMissing = 1),
                outcome = ImportOutcome(
                    report = ImportReport(
                        muscleGroups = TableReport(skipped = 8),
                        exercises = TableReport(inserted = 3, updated = 1, skipped = 54),
                        routines = TableReport(updated = 2, skipped = 2),
                        sessions = TableReport(inserted = 12),
                    ),
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

    @Test
    fun about() = snap("about") {
        AboutScreen(versionName = "0.2.0", onBack = {})
    }
}
