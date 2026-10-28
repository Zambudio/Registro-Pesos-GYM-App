package com.zambudio.registropesos.feature.backup.io

import android.content.Context
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Acceso a ficheros de la exportación/importación (caché, SAF y FileProvider), aislado para tests. */
interface BackupFileStore {
    /** Fichero nuevo en cacheDir/exports; borra exportaciones anteriores para no acumular. */
    fun newExportFile(fileName: String): File

    /** Abre un Uri elegido con el selector de documentos (SAF). */
    fun openInput(uri: String): InputStream
    fun openOutput(uri: String): OutputStream

    /** Uri content:// del fichero (FileProvider) para el Share Sheet. */
    fun shareUri(file: File): String
}

class AndroidBackupFileStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : BackupFileStore {

    override fun newExportFile(fileName: String): File {
        val directory = File(context.cacheDir, EXPORTS_DIR)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("No se pudo crear $directory")
        directory.listFiles()?.forEach { it.delete() }
        return File(directory, fileName)
    }

    override fun openInput(uri: String): InputStream =
        context.contentResolver.openInputStream(uri.toUri()) ?: throw IOException("No se pudo abrir $uri")

    override fun openOutput(uri: String): OutputStream =
        context.contentResolver.openOutputStream(uri.toUri()) ?: throw IOException("No se pudo escribir en $uri")

    override fun shareUri(file: File): String =
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file).toString()

    companion object {
        /** Debe coincidir con el cache-path "exports" de res/xml/file_paths.xml. */
        const val EXPORTS_DIR = "exports"

        /** Debe coincidir con android:authorities del FileProvider en el manifest. */
        const val AUTHORITY_SUFFIX = ".fileprovider"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupIoModule {
    @Binds
    abstract fun bindBackupFileStore(impl: AndroidBackupFileStore): BackupFileStore
}
