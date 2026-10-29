package com.zambudio.registropesos.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zambudio.registropesos.data.TestEnv.Companion.PECHO
import com.zambudio.registropesos.data.catalog.CatalogSeeder
import com.zambudio.registropesos.data.image.AndroidImageStore
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import com.zambudio.registropesos.data.local.entity.TombstoneType
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Catálogo incluido (assets/catalog, extraído del Excel "Rutina 5 Días"): 58 ejercicios únicos de
 * las 74 filas con foto, con su foto, sin duplicar ni pisar nada en instalaciones existentes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class CatalogSeederTest : DataTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var images: AndroidImageStore
    private lateinit var seeder: CatalogSeeder

    @Before
    fun setUp() {
        File(context.filesDir, AndroidImageStore.IMAGES_DIR).deleteRecursively()
        images = AndroidImageStore(context, env.ids, UnconfinedTestDispatcher())
        seeder = CatalogSeeder(context, env.db, images)
    }

    private fun asset(name: String): ByteArray =
        context.assets.open("${CatalogSeeder.IMAGES_DIR}/$name").use { it.readBytes() }

    @Test
    fun catalogFileHas58UniqueExercisesWithExistingImages() {
        val catalog = seeder.readCatalog()

        assertEquals(58, catalog.exercises.size)
        assertEquals(58, catalog.exercises.map { it.id }.toSet().size)
        assertEquals(58, catalog.exercises.map { it.group to it.name }.toSet().size)
        assertEquals(51, catalog.exercises.map { it.image }.toSet().size)
        catalog.exercises.forEach { assertTrue(it.image, asset(it.image).isNotEmpty()) }
        assertEquals(
            listOf("Pecho", "Espalda", "Hombros", "Bíceps", "Tríceps", "Abdomen", "Pierna", "Deltoides posterior"),
            catalog.groups.map { it.name },
        )
        // Los cuatro nombres anotados fuera de las tablas (sin foto) no se incluyen.
        val names = catalog.exercises.map { it.name.lowercase() }
        val outsideTables = listOf(
            "aperturas con mancuernas plano",
            "martillo con polea",
            "predicador mancuerna",
            "inverso polea individual",
        )
        outsideTables.forEach { assertTrue(it, it !in names) }
    }

    @Test
    fun freshInstallGetsWholeCatalogWithTheRightPhotos() = runTest {
        val result = seeder.seed()

        assertEquals(2, result.groupsCreated)
        assertEquals(58, result.exercisesCreated)
        val groups = env.groups.observeAll().first()
        assertEquals(DefaultMuscleGroups.all.size + 2, groups.size)
        val byName = groups.associateBy { it.name }
        val pierna = byName.getValue("Pierna")
        val deltoides = byName.getValue("Deltoides posterior")
        assertEquals(listOf(pierna.id, deltoides.id), groups.takeLast(2).map { it.id })

        val exercises = env.exercises.observeAll().first()
        assertEquals(58, exercises.size)
        val catalog = seeder.readCatalog()
        for (item in catalog.exercises) {
            val stored = exercises.single { it.id == item.id }
            assertEquals(item.name, stored.name)
            assertEquals(item.group, groups.single { it.id == stored.muscleGroupId }.name)
            assertArrayEquals(item.name, asset(item.image), images.resolve(stored.imagePath!!).readBytes())
        }
        // Comprobación independiente del JSON: filas del Excel revisadas a mano.
        fun imageOf(name: String) = catalog.exercises.single { it.name == name }.image
        assertEquals("image29.jpg", imageOf("Press de banca plano con barra"))
        assertEquals("image75.jpg", imageOf("Prensa de piernas"))
        assertEquals("image74.jpg", imageOf("Face pull en polea"))
        assertEquals("image114.jpg", imageOf("Aperturas inversas en poleas"))
        assertEquals(pierna.id, exercises.single { it.name == "Prensa de piernas" }.muscleGroupId)
        assertEquals(deltoides.id, exercises.single { it.name == "Pájaros con mancuernas" }.muscleGroupId)
    }

    @Test
    fun seedingAgainCreatesNothing() = runTest {
        seeder.seed()

        val second = seeder.seed()

        assertEquals(0, second.groupsCreated)
        assertEquals(0, second.exercisesCreated)
        assertEquals(58, env.exercises.observeAll(includeArchived = true).first().size)
        assertEquals(DefaultMuscleGroups.all.size + 2, env.groups.observeAll().first().size)
    }

    @Test
    fun existingInstallKeepsCustomExercisesPhotosAndGroups() = runTest {
        // El usuario ya tenía su "press de banca plano con barra" con foto propia y un grupo "pierna".
        val own = env.newExercise("  press de banca plano con barra ", PECHO, imagePath = "exercise_mine.jpg")
        images.importRaw("exercise_mine.jpg", byteArrayOf(1, 2, 3))
        env.db.muscleGroupDao().insert(MuscleGroupEntity("my-legs", "pierna", 20, false, 5L, 5L))

        val result = seeder.seed()

        assertEquals(1, result.groupsCreated) // solo "Deltoides posterior"
        assertEquals(57, result.exercisesCreated)
        val exercises = env.exercises.observeAll().first()
        assertEquals(58, exercises.size)
        assertEquals(1, exercises.count { it.name.trim().equals("press de banca plano con barra", ignoreCase = true) })
        assertEquals("exercise_mine.jpg", env.exercises.getById(own.id)!!.imagePath)
        assertArrayEquals(byteArrayOf(1, 2, 3), images.resolve("exercise_mine.jpg").readBytes())
        assertEquals(9, exercises.count { it.muscleGroupId == "my-legs" })
        assertNull(env.groups.observeAll().first().firstOrNull { it.name == "Pierna" && it.id != "my-legs" })
    }

    @Test
    fun editedCatalogExerciseIsNeverOverwritten() = runTest {
        seeder.seed()
        val item = seeder.readCatalog().exercises.first()
        val seeded = env.exercises.getById(item.id)!!
        env.exercises.upsert(seeded.copy(name = "Mi press", imagePath = "exercise_custom.jpg", isArchived = true))

        seeder.seed()

        val after = env.exercises.getById(item.id)!!
        assertEquals("Mi press", after.name)
        assertEquals("exercise_custom.jpg", after.imagePath)
        assertTrue(after.isArchived)
        assertEquals(58, env.exercises.observeAll(includeArchived = true).first().size)
    }

    @Test
    fun deletedCatalogGroupIsNotRecreated() = runTest {
        val deltoidesId = seeder.readCatalog().groups.single { it.name == "Deltoides posterior" }.id
        env.db.tombstoneDao().upsert(DeletedEntityEntity(TombstoneType.MUSCLE_GROUP, deltoidesId, 1L))

        val result = seeder.seed()

        assertEquals(54, result.exercisesCreated)
        assertNull(env.groups.getById(deltoidesId))
    }
}
