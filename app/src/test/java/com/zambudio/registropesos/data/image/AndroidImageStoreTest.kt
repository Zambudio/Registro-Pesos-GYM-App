package com.zambudio.registropesos.data.image

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.data.SequentialIds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidImageStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun saveResizedScalesLongestSideTo1280AsJpeg() = runTest {
        val store = AndroidImageStore(context, SequentialIds("img"), StandardTestDispatcher(testScheduler))
        val source = File(context.cacheDir, "big.png")
        Bitmap.createBitmap(3000, 2000, Bitmap.Config.ARGB_8888).run {
            source.outputStream().use { compress(Bitmap.CompressFormat.PNG, 100, it) }
            recycle()
        }

        val name = store.saveResized("file://${source.absolutePath}")

        assertEquals("exercise_img-1.jpg", name)
        val saved = store.resolve(name)
        assertEquals(File(context.filesDir, "images"), saved.parentFile)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeByteArray: decodeFile nativo de Robolectric deja el fichero bloqueado en Windows.
        val bytes = saved.readBytes()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        assertEquals(1280, bounds.outWidth)
        assertEquals(853, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(listOf(saved), store.listAll())

        store.delete(name)
        assertFalse(saved.exists())
    }

    @Test
    fun importRawKeepsOnlyTheFileName() = runTest {
        val store = AndroidImageStore(context, SequentialIds(), StandardTestDispatcher(testScheduler))

        store.importRaw("images/../../exercise_x.jpg", byteArrayOf(1, 2, 3))

        val file = store.resolve("exercise_x.jpg")
        assertTrue(file.exists())
        assertEquals(File(context.filesDir, "images"), file.parentFile)
    }

    @Test
    fun sampleSizeKeepsDecodedSideAtLeastTarget() {
        assertEquals(1, AndroidImageStore.sampleSizeFor(1280))
        assertEquals(1, AndroidImageStore.sampleSizeFor(2559))
        assertEquals(2, AndroidImageStore.sampleSizeFor(2560))
        assertEquals(4, AndroidImageStore.sampleSizeFor(6000))
    }
}
