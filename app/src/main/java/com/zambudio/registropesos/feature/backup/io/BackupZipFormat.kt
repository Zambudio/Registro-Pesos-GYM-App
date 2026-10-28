package com.zambudio.registropesos.feature.backup.io

import java.io.IOException

// Estructura del ZIP de copia de seguridad (kickoff §1.6).

object BackupZipFormat {
    const val BACKUP_JSON = "backup.json"
    const val SETS_CSV = "sets.csv"
    const val IMAGES_DIR = "images/"
    const val FILE_PREFIX = "gym-backup-"
    const val MIME_TYPE = "application/zip"

    /** El selector de documentos a veces etiqueta los ZIP como binario genérico. */
    val importMimeTypes = arrayOf(MIME_TYPE, "application/octet-stream", "application/x-zip-compressed")

    /**
     * Rechaza rutas que podrían salir del destino al descomprimir (zip-slip): absolutas,
     * con unidad de Windows, con segmentos "..", vacías o con caracteres de control.
     */
    fun isSafeEntryName(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\")) return false
        if (name.length >= 2 && name[1] == ':') return false
        if (name.any { it.isISOControl() }) return false
        return name.split('/', '\\').none { it == ".." }
    }
}

/** El fichero no es un backup de esta app o está dañado/manipulado. */
class InvalidBackupFileException(message: String, cause: Throwable? = null) : IOException(message, cause)
