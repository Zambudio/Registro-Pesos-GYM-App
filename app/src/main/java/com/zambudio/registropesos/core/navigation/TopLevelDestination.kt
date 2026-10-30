package com.zambudio.registropesos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.zambudio.registropesos.R
import kotlin.reflect.KClass

/** Pestañas de la barra inferior: icono contorneado sin seleccionar y relleno al seleccionarla. */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @param:StringRes val labelRes: Int,
) {
    TRAIN(TrainRoute, TrainRoute::class, Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter, R.string.nav_train),
    ROUTINES(
        RoutinesRoute,
        RoutinesRoute::class,
        Icons.AutoMirrored.Outlined.ListAlt,
        Icons.AutoMirrored.Filled.ListAlt,
        R.string.nav_routines,
    ),
    EXERCISES(
        ExercisesRoute,
        ExercisesRoute::class,
        Icons.Outlined.GridView,
        Icons.Filled.GridView,
        R.string.nav_exercises,
    ),
    PROGRESS(
        ProgressRoute,
        ProgressRoute::class,
        Icons.AutoMirrored.Outlined.TrendingUp,
        Icons.AutoMirrored.Filled.TrendingUp,
        R.string.nav_progress,
    ),
    MORE(MoreRoute, MoreRoute::class, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz, R.string.nav_more),
}
