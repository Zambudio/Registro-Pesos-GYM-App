package com.zambudio.registropesos.feature.exercises.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.ExercisesRoute
import com.zambudio.registropesos.core.navigation.ExerciseEditRoute
import com.zambudio.registropesos.core.navigation.MuscleGroupsRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.exercisesGraph(navController: NavHostController) {
    composable<ExercisesRoute> { PlaceholderScreen("ExercisesRoute") }
    composable<ExerciseEditRoute> { PlaceholderScreen("ExerciseEditRoute") }
    composable<MuscleGroupsRoute> { PlaceholderScreen("MuscleGroupsRoute") }
}
