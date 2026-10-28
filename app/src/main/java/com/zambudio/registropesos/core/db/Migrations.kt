package com.zambudio.registropesos.core.db

import androidx.room.migration.Migration

/**
 * Política de migraciones explícitas: cada subida de versión de [AppDatabase] añade aquí su
 * `Migration(n, n + 1)` y un test con MigrationTestHelper. Nunca migración destructiva
 * (el builder no la activa): actualizar la app no puede perder registros.
 */
object Migrations {
    val ALL_MIGRATIONS: Array<Migration> = emptyArray()
}
