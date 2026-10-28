package com.zambudio.registropesos.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.net.toUri
import com.zambudio.registropesos.core.di.IoDispatcher
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.util.IdGenerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Imágenes de ejercicios en filesDir/images (incluido en Auto Backup). El dominio solo maneja
 * el nombre de fichero (ADR-007); cualquier ruta que llegue se reduce a su nombre.
 */
class AndroidImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ids: IdGenerator,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ImageStore {
    private val directory: File by lazy { File(context.filesDir, IMAGES_DIR) }

    override suspend fun saveResized(sourceUri: String): String = withContext(io) {
        val uri = sourceUri.toUri()
        val decoded = decodeSampled(uri)
        val output = scaleAndRotate(decoded, readRotation(uri))
        val name = "exercise_${ids.newId()}.jpg"
        val file = File(directory.also { it.mkdirs() }, name)
        try {
            writeJpeg(output, file)
        } finally {
            if (output !== decoded) output.recycle()
            decoded.recycle()
        }
        name
    }

    override fun resolve(relativePath: String): File = File(directory, safeName(relativePath))

    override suspend fun delete(relativePath: String) {
        withContext(io) { resolve(relativePath).delete() }
    }

    override fun listAll(): List<File> = directory.listFiles()?.filter { it.isFile }.orEmpty()

    override suspend fun importRaw(relativePath: String, bytes: ByteArray) {
        withContext(io) {
            directory.mkdirs()
            resolve(relativePath).writeBytes(bytes)
        }
    }

    private fun open(uri: Uri) =
        context.contentResolver.openInputStream(uri) ?: throw IOException("No se pudo abrir $uri")

    /** Decodifica con inSampleSize (sin cargar la imagen completa en memoria). */
    private fun decodeSampled(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val longestSide = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(longestSide) }
        val decoded = if (longestSide > 0) open(uri).use { BitmapFactory.decodeStream(it, null, options) } else null
        return decoded ?: throw IOException("No es una imagen válida: $uri")
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        val written = try {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        } catch (error: IOException) {
            file.delete()
            throw error
        }
        if (!written) {
            file.delete()
            throw IOException("No se pudo comprimir la imagen")
        }
    }

    /** Grados de rotación según EXIF; solo rotaciones simples (sin espejos). */
    private fun readRotation(uri: Uri): Float = try {
        open(uri).use { stream ->
            val orientation = ExifInterface(stream)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> ROTATE_90
                ExifInterface.ORIENTATION_ROTATE_180 -> ROTATE_180
                ExifInterface.ORIENTATION_ROTATE_270 -> ROTATE_270
                else -> 0f
            }
        }
    } catch (ignored: IOException) {
        0f
    }

    private fun scaleAndRotate(source: Bitmap, rotation: Float): Bitmap {
        val scale = min(1f, MAX_SIDE_PX.toFloat() / max(source.width, source.height))
        if (scale == 1f && rotation == 0f) return source
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    companion object {
        const val IMAGES_DIR = "images"
        const val MAX_SIDE_PX = 1280
        const val JPEG_QUALITY = 80
        private const val ROTATE_90 = 90f
        private const val ROTATE_180 = 180f
        private const val ROTATE_270 = 270f

        /** Mayor potencia de 2 que deja el lado mayor decodificado en >= [MAX_SIDE_PX]. */
        fun sampleSizeFor(longestSide: Int): Int {
            var sample = 1
            while (longestSide / (sample * 2) >= MAX_SIDE_PX) sample *= 2
            return sample
        }

        /** Reduce cualquier ruta (p. ej. "images/x.jpg" del ZIP) a un nombre de fichero seguro. */
        fun safeName(relativePath: String): String {
            val name = File(relativePath).name
            require(name.isNotBlank() && name != "." && name != "..") { "Nombre de imagen inválido: $relativePath" }
            return name
        }
    }
}
