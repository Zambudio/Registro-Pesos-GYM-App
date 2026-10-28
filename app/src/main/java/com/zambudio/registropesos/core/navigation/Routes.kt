package com.zambudio.registropesos.core.navigation

import kotlinx.serialization.Serializable

// Todas las rutas tipadas de la app. Cada feature registra las suyas en su
// NavGraphBuilder.<feature>Graph() (feature/<x>/navigation/).

// --- Pestañas principales ---
@Serializable data object TrainRoute

@Serializable data object RoutinesRoute

@Serializable data object ExercisesRoute

@Serializable data object ProgressRoute

@Serializable data object MoreRoute

// --- Ejercicios y grupos musculares (feature/exercises) ---
@Serializable data class ExerciseEditRoute(val exerciseId: String? = null, val muscleGroupId: String? = null)

@Serializable data object MuscleGroupsRoute

// --- Rutinas (feature/routines) ---
@Serializable data class RoutineEditRoute(val routineId: String? = null)

// --- Entrenar (feature/workout) ---
@Serializable data class RoutineDetailRoute(val routineId: String)

@Serializable data class WorkoutRoute(val sessionId: String)

@Serializable data class WorkoutSummaryRoute(val sessionId: String)

// --- Historial (feature/history) ---
@Serializable data object HistoryRoute

@Serializable data class SessionDetailRoute(val sessionId: String)

// --- Copias de seguridad y ajustes (feature/backup, feature/settings) ---
@Serializable data object BackupRoute

@Serializable data object AboutRoute
