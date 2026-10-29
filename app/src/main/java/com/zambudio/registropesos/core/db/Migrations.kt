package com.zambudio.registropesos.core.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Política de migraciones explícitas: cada subida de versión de [AppDatabase] añade aquí su
 * `Migration(n, n + 1)` y un test con MigrationTestHelper. Nunca migración destructiva
 * (el builder no la activa): actualizar la app no puede perder registros.
 */
object Migrations {

    /**
     * v2 (ADR-018): rutinas por grupos y ejercicios elegidos durante la sesión.
     * - Nueva tabla workout_session_groups (grupos previstos de cada sesión).
     * - workout_sessions.routine_comment_snapshot (comentario de la rutina al empezar).
     * - Una sesión en curso de la v1 recibe sus grupos (los de sus ejercicios, en orden) y el
     *   comentario actual de su rutina, para reanudarla igual que una sesión nueva.
     * Las columnas workout_sets.is_done/completed_at se conservan sin uso: SQLite de API 26 no
     * tiene DROP COLUMN y reconstruir la tabla no aporta nada. No se borra ni se toca ninguna fila.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `routine_comment_snapshot` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `workout_session_groups` (`id` TEXT NOT NULL, " +
                    "`session_id` TEXT NOT NULL, `muscle_group_id` TEXT, " +
                    "`muscle_group_name_snapshot` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, " +
                    "`created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                    "FOREIGN KEY(`session_id`) REFERENCES `workout_sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`muscle_group_id`) REFERENCES `muscle_groups`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_session_groups_session_id` " +
                    "ON `workout_session_groups` (`session_id`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_workout_session_groups_muscle_group_id` " +
                    "ON `workout_session_groups` (`muscle_group_id`)",
            )
            db.execSQL(
                """
                UPDATE workout_sessions
                SET routine_comment_snapshot = (
                    SELECT NULLIF(TRIM(r.subtitle), '') FROM routines r WHERE r.id = workout_sessions.routine_id
                )
                WHERE status = 'IN_PROGRESS'
                """.trimIndent(),
            )
            // Un UUID v4 por grupo generado en SQL (randomblob se evalúa en cada fila).
            db.execSQL(
                """
                INSERT INTO workout_session_groups
                    (id, session_id, muscle_group_id, muscle_group_name_snapshot, sort_order, created_at, updated_at)
                SELECT
                    lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' ||
                        substr(lower(hex(randomblob(2))), 2) || '-a' || substr(lower(hex(randomblob(2))), 2) ||
                        '-' || lower(hex(randomblob(6))),
                    s.id, g.id, g.name, MIN(ws.exercise_order), s.updated_at, s.updated_at
                FROM workout_sessions s
                JOIN workout_sets ws ON ws.session_id = s.id
                JOIN exercises e ON e.id = ws.exercise_id
                JOIN muscle_groups g ON g.id = e.muscle_group_id
                WHERE s.status = 'IN_PROGRESS'
                GROUP BY s.id, g.id
                """.trimIndent(),
            )
        }
    }

    val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
}
