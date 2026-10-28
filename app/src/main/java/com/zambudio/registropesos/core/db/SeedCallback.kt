package com.zambudio.registropesos.core.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zambudio.registropesos.data.DefaultMuscleGroups

/** Siembra los grupos musculares por defecto al crear la BD (solo la primera vez). */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        DefaultMuscleGroups.all.forEach { seed ->
            db.execSQL(
                "INSERT OR IGNORE INTO muscle_groups " +
                    "(id, name, sort_order, is_default, created_at, updated_at) VALUES (?, ?, ?, 1, ?, ?)",
                arrayOf<Any>(
                    seed.id,
                    seed.name,
                    seed.sortOrder,
                    DefaultMuscleGroups.SEED_TIMESTAMP,
                    DefaultMuscleGroups.SEED_TIMESTAMP,
                ),
            )
        }
    }
}
