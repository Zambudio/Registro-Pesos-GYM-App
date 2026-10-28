package com.zambudio.registropesos.feature.exercises

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zambudio.registropesos.BuildConfig
import java.io.File
import java.util.UUID

// Fichero temporal para ActivityResultContracts.TakePicture, en cacheDir/camera/
// (declarado en res/xml/file_paths.xml). El ViewModel lo borra tras procesar la foto.

private const val CAMERA_DIR = "camera"
private const val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"

internal fun createCameraTempFile(context: Context): File {
    val directory = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
    return File(directory, "capture_${UUID.randomUUID()}.jpg")
}

internal fun cameraUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
