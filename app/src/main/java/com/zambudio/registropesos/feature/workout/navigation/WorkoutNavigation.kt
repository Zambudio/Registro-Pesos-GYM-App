package com.zambudio.registropesos.feature.workout.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.TrainRoute
import com.zambudio.registropesos.core.navigation.RoutineDetailRoute
import com.zambudio.registropesos.core.navigation.WorkoutRoute
import com.zambudio.registropesos.core.navigation.WorkoutSummaryRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.workoutGraph(navController: NavHostController) {
    composable<TrainRoute> { PlaceholderScreen("TrainRoute") }
    composable<RoutineDetailRoute> { PlaceholderScreen("RoutineDetailRoute") }
    composable<WorkoutRoute> { PlaceholderScreen("WorkoutRoute") }
    composable<WorkoutSummaryRoute> { PlaceholderScreen("WorkoutSummaryRoute") }
}
