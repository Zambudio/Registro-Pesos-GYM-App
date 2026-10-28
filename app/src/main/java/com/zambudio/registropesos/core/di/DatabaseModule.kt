package com.zambudio.registropesos.core.di

import android.content.Context
import com.zambudio.registropesos.core.db.AppDatabase
import com.zambudio.registropesos.data.local.dao.ExerciseDao
import com.zambudio.registropesos.data.local.dao.MuscleGroupDao
import com.zambudio.registropesos.data.local.dao.ProgressDao
import com.zambudio.registropesos.data.local.dao.RoutineDao
import com.zambudio.registropesos.data.local.dao.TombstoneDao
import com.zambudio.registropesos.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.build(context)

    @Provides
    fun provideMuscleGroupDao(db: AppDatabase): MuscleGroupDao = db.muscleGroupDao()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideRoutineDao(db: AppDatabase): RoutineDao = db.routineDao()

    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideProgressDao(db: AppDatabase): ProgressDao = db.progressDao()

    @Provides
    fun provideTombstoneDao(db: AppDatabase): TombstoneDao = db.tombstoneDao()
}
