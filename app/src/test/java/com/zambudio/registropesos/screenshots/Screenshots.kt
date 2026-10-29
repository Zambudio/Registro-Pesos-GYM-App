package com.zambudio.registropesos.screenshots

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.core.theme.GymTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Base de la galería de capturas: renderiza una pantalla en claro y en oscuro y guarda los PNG en
 * `app/build/screenshots/<nombre>-light.png` / `-dark.png`. No compara contra referencias: sirve
 * para revisar el diseño sin dispositivo. Solo corre con `-Pgym.screenshots=true`:
 *
 * ```
 * .\gradlew.bat :app:testDebugUnitTest --tests "*ScreenshotGallery*" -Pgym.screenshots=true
 * ```
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
abstract class ScreenshotGallery {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun onlyWhenRequested() {
        assumeTrue("Galería desactivada (usa -Pgym.screenshots=true)", System.getProperty("gym.screenshots") == "true")
    }

    /** Captura [content] con el tema claro y con el oscuro. */
    protected fun snap(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(false)
        compose.setContent {
            GymTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
            }
        }
        save("$name-light")
        dark = true
        save("$name-dark")
    }

    private fun save(fileName: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val file = File("build/screenshots/$fileName.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
