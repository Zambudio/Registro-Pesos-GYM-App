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
import org.junit.Assert.assertNull
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

    /**
     * 1 -> 2 con datos como los de la v0.1.0: rutina con ejercicios preseleccionados, una sesión
     * terminada (con "Hecha") y otra en curso. No se pierde ninguna fila; la sesión en curso recibe
     * sus grupos (en el orden de sus ejercicios) y el comentario de su rutina.
     */
    @Test
    fun migration1To2KeepsDataAndPreparesSessionInProgress() = runTest {
        val name = dbPath("v1-data.db")
        val pecho = DefaultMuscleGroups.all[0]
        val triceps = DefaultMuscleGroups.all[4]
        helper.createDatabase(name, 1).apply {
            listOf(pecho, triceps).forEach {
                execSQL(
                    "INSERT INTO muscle_groups VALUES (?, ?, ?, 1, 0, 0)",
                    arrayOf<Any>(it.id, it.name, it.sortOrder),
                )
            }
            execSQL(
                "INSERT INTO exercises VALUES " +
                    "('press', '${pecho.id}', 'Press banca', NULL, 'exercise_press.jpg', 0, 1, 1)",
            )
            execSQL("INSERT INTO exercises VALUES ('polea', '${triceps.id}', 'Polea', NULL, NULL, 0, 1, 1)")
            execSQL("INSERT INTO routines VALUES ('r1', 'Día 1', '  4 pecho, 2 tríceps ', 0, 1, 1)")
            execSQL("INSERT INTO routine_blocks VALUES ('b1', 'r1', '${pecho.id}', 0, 1, 1)")
            execSQL("INSERT INTO routine_blocks VALUES ('b2', 'r1', '${triceps.id}', 1, 1, 1)")
            execSQL("INSERT INTO routine_exercises VALUES ('re1', 'b1', 'press', 0, 4, 1, 1)")
            execSQL("INSERT INTO routine_exercises VALUES ('re2', 'b2', 'polea', 0, 3, 1, 1)")
            execSQL(
                "INSERT INTO workout_sessions VALUES " +
                    "('done', 'r1', 'Día 1', 10, 20, 'FINISHED', NULL, 10, 20)",
            )
            execSQL(
                "INSERT INTO workout_sessions VALUES " +
                    "('live', 'r1', 'Día 1', 30, NULL, 'IN_PROGRESS', NULL, 30, 35)",
            )
            execSQL(
                "INSERT INTO workout_sets VALUES " +
                    "('d1', 'done', 'press', 'Press banca', 'Pecho', 0, 0, 80.0, 8, 1, 15, 10, 15)",
            )
            // Sesión en curso: Polea va primero en esta sesión, Press después.
            execSQL(
                "INSERT INTO workout_sets VALUES " +
                    "('l1', 'live', 'polea', 'Polea', 'Tríceps', 0, 0, 30.0, 12, 1, 33, 30, 33)",
            )
            execSQL(
                "INSERT INTO workout_sets VALUES " +
                    "('l2', 'live', 'press', 'Press banca', 'Pecho', 1, 0, NULL, NULL, 0, NULL, 30, 30)",
            )
            close()
        }

        helper.runMigrationsAndValidate(name, 2, true, Migrations.MIGRATION_1_2).apply {
            fun count(table: String) = query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
            assertEquals(2, count("exercises"))
            assertEquals(1, count("routines"))
            assertEquals(2, count("routine_blocks"))
            assertEquals(2, count("routine_exercises"))
            assertEquals(2, count("workout_sessions"))
            assertEquals(3, count("workout_sets"))
            query("SELECT is_done, completed_at, weight_kg FROM workout_sets WHERE id = 'd1'").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(15L, it.getLong(1))
                assertEquals(80.0, it.getDouble(2), 0.0)
            }
            close()
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.workoutDao()
            val live = dao.getSession("live")!!
            assertEquals("4 pecho, 2 tríceps", live.routineCommentSnapshot)
            assertEquals(listOf("Tríceps", "Pecho"), dao.getSessionGroups("live").map { it.muscleGroupNameSnapshot })
            assertEquals(listOf(triceps.id, pecho.id), dao.getSessionGroups("live").map { it.muscleGroupId })
            assertNull(dao.getSession("done")!!.routineCommentSnapshot)
            assertTrue(dao.getSessionGroups("done").isEmpty())
            assertEquals(2, db.routineDao().getRoutineExercises("r1").size)
        } finally {
            db.close()
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
