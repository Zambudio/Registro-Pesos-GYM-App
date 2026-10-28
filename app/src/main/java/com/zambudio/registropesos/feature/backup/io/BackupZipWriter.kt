package com.zambudio.registropesos.feature.backup.io

import com.zambudio.registropesos.domain.usecase.backup.SetsCsvWriter
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Imagen a incluir en el ZIP: [zipPath] es la ruta dentro del ZIP ("images/x.jpg"). */
data class BackupImageSource(val zipPath: String, val file: File)

data class BackupZipWriteResult(val imagesWritten: Int, val imagesMissing: Int)

/**
 * Escribe el ZIP de backup: backup.json, sets.csv (UTF-8 con BOM para Excel) e images/.
 * No cierra [output]: lo gestiona quien lo abre.
 */
class BackupZipWriter {

    fun write(
        output: OutputStream,
        backupJson: String,
        setsCsv: String,
        images: List<BackupImageSource>,
    ): BackupZipWriteResult {
        var written = 0
        var missing = 0
        val zip = ZipOutputStream(output)
        zip.putText(BackupZipFormat.BACKUP_JSON, backupJson)
        zip.putText(BackupZipFormat.SETS_CSV, SetsCsvWriter.UTF8_BOM + setsCsv)
        images.distinctBy { it.zipPath }.forEach { image ->
            require(BackupZipFormat.isSafeEntryName(image.zipPath)) { "Ruta de imagen no válida: ${image.zipPath}" }
            if (image.file.isFile) {
                zip.putNextEntry(ZipEntry(image.zipPath))
                image.file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                written++
            } else {
                missing++
            }
        }
        // finish() y no close(): el stream de destino lo cierra su dueño.
        zip.finish()
        zip.flush()
        return BackupZipWriteResult(imagesWritten = written, imagesMissing = missing)
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
