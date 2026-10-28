package com.zambudio.registropesos.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zambudio.registropesos.data.local.dao.ExerciseDao
import com.zambudio.registropesos.data.local.dao.MuscleGroupDao
import com.zambudio.registropesos.data.local.dao.ProgressDao
import com.zambudio.registropesos.data.local.dao.RoutineDao
import com.zambudio.registropesos.data.local.dao.TombstoneDao
import com.zambudio.registropesos.data.local.dao.WorkoutDao
import com.zambudio.registropesos.data.local.entity.DeletedEntityEntity
import com.zambudio.registropesos.data.local.entity.ExerciseEntity
import com.zambudio.registropesos.data.local.entity.MuscleGroupEntity
import com.zambudio.registropesos.data.local.entity.RoutineBlockEntity
import com.zambudio.registropesos.data.local.entity.RoutineEntity
import com.zambudio.registropesos.data.local.entity.RoutineExerciseEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSessionEntity
import com.zambudio.registropesos.data.local.entity.WorkoutSetEntity

/**
 * BD Room. El esquema se exporta a app/schemas (versionado en git). Subir [VERSION] exige una
 * migración en [Migrations] y conservar el JSON de la versión anterior.
 */
@Database(
    entities = [
        MuscleGroupEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineBlockEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        DeletedEntityEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun muscleGroupDao(): MuscleGroupDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun progressDao(): ProgressDao
    abstract fun tombstoneDao(): TombstoneDao

    companion object {
        const val VERSION = 1
        const val FILE_NAME = "gym.db"

        // addMigrations es vararg; la copia del array (pequeño, una vez por proceso) es irrelevante.
        @Suppress("SpreadOperator")
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, FILE_NAME)
                .addCallback(SeedCallback)
                .addMigrations(*Migrations.ALL_MIGRATIONS)
                .build()
    }
}
