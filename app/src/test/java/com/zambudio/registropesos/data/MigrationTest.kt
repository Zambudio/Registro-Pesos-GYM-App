package com.zambudio.registropesos.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.core.db.Migrations
import com.zambudio.registropesos.core.db.SeedCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Infraestructura de migraciones (política ADR: nunca destructiva).
 * - Cada versión 1..VERSION tiene su esquema exportado en app/schemas.
 * - Cada Migration(n, n+1) de [Migrations.ALL_MIGRATIONS] se valida contra el esquema exportado.
 * - Una BD creada con datos sobrevive a reabrirla con la app actual sin perder filas ni re-sembrar.
 * Al añadir la v2 basta con añadir la Migration: [allMigrationsValidateAgainstExportedSchemas]
 * la cubre automáticamente.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private val schemaDir = File("schemas/${AppDatabase::class.java.name}")

    private fun dbPath(name: String): String =
        ApplicationProvider.getApplicationContext<Context>().getDatabasePath(name).absolutePath

    @Test
    fun everyVersionHasExportedSchema() {
        for (version in 1..AppDatabase.VERSION) {
            assertTrue("Falta el esquema $version.json", File(schemaDir, "$version.json").exists())
        }
    }

    @Test
    fun migrationsCoverEveryVersionStep() {
        val steps = Migrations.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        for (version in 1 until AppDatabase.VERSION) {
            assertTrue("Falta Migration($version, ${version + 1})", (version to version + 1) in steps)
        }
    }

    @Test
    fun allMigrationsValidateAgainstExportedSchemas() {
        // Ruta absoluta: con nombre relativo, el driver de Room 2.8 rechaza la ruta en Windows/Robolectric.
        val name = dbPath("migration-steps.db")
        helper.createDatabase(name, 1).close()
        for (migration in Migrations.ALL_MIGRATIONS.sortedBy { it.startVersion }) {
            helper.runMigrationsAndValidate(name, migration.endVersion, true, migration).close()
        }
    }

    @Test
    fun existingDataSurvivesReopeningWithCurrentApp() = runTest {
        val name = dbPath("upgrade.db")
        helper.createDatabase(name, 1).apply {
            DefaultMuscleGroups.all.forEach {
                execSQL(
                    "INSERT INTO muscle_groups (id, name, sort_order, is_default, created_at, updated_at) " +
                        "VALUES ('${it.id}', '${it.name}', ${it.sortOrder}, 1, 0, 0)",
                )
            }
            execSQL(
                "INSERT INTO exercises (id, muscle_group_id, name, notes, image_path, is_archived, created_at, " +
                    "updated_at) VALUES ('ex-1', '${DefaultMuscleGroups.all.first().id}', 'Press banca', NULL, " +
                    "NULL, 0, 1, 1)",
            )
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addCallback(SeedCallback)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val groups = db.muscleGroupDao().observeAll().first()
            val exercises = db.exerciseDao().observeAll(true).first()
            assertEquals(DefaultMuscleGroups.all.size, groups.size)
            assertEquals(listOf("Press banca"), exercises.map { it.name })
        } finally {
            db.close()
        }
    }
}
