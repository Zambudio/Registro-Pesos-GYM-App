package com.zambudio.registropesos.feature.backup.io

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Lee el ZIP de backup en streaming, sin descomprimir a disco. Cualquier entrada con ruta
 * peligrosa (zip-slip) invalida el fichero entero, y cada entrada tiene un tamaño máximo
 * para no agotar la memoria con un ZIP manipulado (zip bomb). No cierra [InputStream].
 */
class BackupZipReader(
    private val maxJsonBytes: Long = DEFAULT_MAX_JSON_BYTES,
    private val maxImageBytes: Long = DEFAULT_MAX_IMAGE_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {

    /** Devuelve el texto de backup.json. */
    fun readBackupJson(input: InputStream): String {
        var json: String? = null
        forEachEntry(input) { name, zip ->
            if (name == BackupZipFormat.BACKUP_JSON && json == null) {
                json = readBounded(zip, maxJsonBytes, name).toString(Charsets.UTF_8)
            }
        }
        return json ?: throw InvalidBackupFileException("El ZIP no contiene ${BackupZipFormat.BACKUP_JSON}")
    }

    /** Recorre las imágenes cuya ruta esté en [wanted] y las entrega a [action]. Devuelve las encontradas. */
    inline fun forEachImage(input: InputStream, wanted: Set<String>, action: (String, ByteArray) -> Unit): Set<String> {
        val found = mutableSetOf<String>()
        forEachEntry(input) { name, zip ->
            if (name in wanted && name !in found) {
                action(name, readImage(zip, name))
                found += name
            }
        }
        return found
    }

    @PublishedApi
    internal fun readImage(zip: ZipInputStream, name: String): ByteArray = readBounded(zip, maxImageBytes, name)

    @PublishedApi
    internal inline fun forEachEntry(input: InputStream, action: (String, ZipInputStream) -> Unit) {
        val zip = ZipInputStream(input)
        var count = 0
        try {
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                validateEntry(entry.name, count)
                if (!entry.isDirectory) action(entry.name, zip)
                zip.closeEntry()
            }
        } catch (error: ZipException) {
            throw InvalidBackupFileException("El fichero no es un ZIP válido", error)
        }
        if (count == 0) throw InvalidBackupFileException("El fichero no es un ZIP válido o está vacío")
    }

    @PublishedApi
    internal fun validateEntry(name: String, position: Int) {
        if (position > maxEntries) throw InvalidBackupFileException("Demasiadas entradas en el ZIP")
        if (!BackupZipFormat.isSafeEntryName(name)) {
            throw InvalidBackupFileException("Entrada no permitida en el ZIP: $name")
        }
    }

    private fun readBounded(stream: InputStream, limit: Long, name: String): ByteArray {
        val buffer = ByteArray(BUFFER_SIZE)
        val output = ByteArrayOutputStream()
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw InvalidBackupFileException("La entrada $name supera el tamaño máximo")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        const val DEFAULT_MAX_JSON_BYTES = 64L * 1024 * 1024
        const val DEFAULT_MAX_IMAGE_BYTES = 20L * 1024 * 1024
        const val DEFAULT_MAX_ENTRIES = 20_000
        private const val BUFFER_SIZE = 16 * 1024
    }
}
