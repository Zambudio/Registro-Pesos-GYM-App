package com.zambudio.registropesos.core.di

import com.zambudio.registropesos.data.image.AndroidImageStore
import com.zambudio.registropesos.data.repository.BackupRepositoryImpl
import com.zambudio.registropesos.data.repository.ExerciseRepositoryImpl
import com.zambudio.registropesos.data.repository.MuscleGroupRepositoryImpl
import com.zambudio.registropesos.data.repository.ProgressRepositoryImpl
import com.zambudio.registropesos.data.repository.RoutineRepositoryImpl
import com.zambudio.registropesos.data.repository.SettingsRepositoryImpl
import com.zambudio.registropesos.data.repository.WorkoutRepositoryImpl
import com.zambudio.registropesos.domain.repository.BackupRepository
import com.zambudio.registropesos.domain.repository.ExerciseRepository
import com.zambudio.registropesos.domain.repository.ImageStore
import com.zambudio.registropesos.domain.repository.MuscleGroupRepository
import com.zambudio.registropesos.domain.repository.ProgressRepository
import com.zambudio.registropesos.domain.repository.RoutineRepository
import com.zambudio.registropesos.domain.repository.SettingsRepository
import com.zambudio.registropesos.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMuscleGroupRepository(impl: MuscleGroupRepositoryImpl): MuscleGroupRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: BackupRepositoryImpl): BackupRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindImageStore(impl: AndroidImageStore): ImageStore
}
