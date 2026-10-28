package com.zambudio.registropesos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.zambudio.registropesos.R
import kotlin.reflect.KClass

enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
) {
    TRAIN(TrainRoute, TrainRoute::class, Icons.Filled.FitnessCenter, R.string.nav_train),
    ROUTINES(RoutinesRoute, RoutinesRoute::class, Icons.AutoMirrored.Filled.List, R.string.nav_routines),
    EXERCISES(ExercisesRoute, ExercisesRoute::class, Icons.Filled.GridView, R.string.nav_exercises),
    PROGRESS(ProgressRoute, ProgressRoute::class, Icons.AutoMirrored.Filled.ShowChart, R.string.nav_progress),
    MORE(MoreRoute, MoreRoute::class, Icons.Filled.MoreHoriz, R.string.nav_more),
}
