package com.zambudio.registropesos.data.local.dao

// Proyecciones de consultas agregadas. Los alias SQL coinciden con los nombres de propiedad.

data class GroupExerciseCountRow(
    val groupId: String,
    val count: Int,
)

data class SessionSummaryRow(
    val id: String,
    val routineName: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val exerciseCount: Int,
    val volumeKg: Double,
)

data class ExerciseSessionPointRow(
    val sessionId: String,
    val startedAt: Long,
    val maxWeightKg: Double,
    val volumeKg: Double,
    val bestE1rmKg: Double,
)

data class SessionVolumeRow(
    val sessionId: String,
    val startedAt: Long,
    val volumeKg: Double,
)

data class FlatSetRow(
    val exerciseId: String,
    val sessionId: String,
    val weightKg: Double,
    val reps: Int,
    val startedAt: Long,
)
