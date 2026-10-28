package com.zambudio.registropesos.feature.workout.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.HistoryRoute
import com.zambudio.registropesos.core.navigation.RoutineDetailRoute
import com.zambudio.registropesos.core.navigation.RoutineEditRoute
import com.zambudio.registropesos.core.navigation.TrainRoute
import com.zambudio.registropesos.core.navigation.WorkoutRoute
import com.zambudio.registropesos.core.navigation.WorkoutSummaryRoute
import com.zambudio.registropesos.feature.workout.routinedetail.RoutineDetailRoute as RoutineDetailDestination
import com.zambudio.registropesos.feature.workout.session.WorkoutRoute as WorkoutDestination
import com.zambudio.registropesos.feature.workout.summary.WorkoutSummaryRoute as WorkoutSummaryDestination
import com.zambudio.registropesos.feature.workout.train.TrainRoute as TrainDestination

fun NavGraphBuilder.workoutGraph(navController: NavHostController) {
    composable<TrainRoute> {
        TrainDestination(
            onOpenRoutine = { navController.navigate(RoutineDetailRoute(it)) },
            onResumeSession = { navController.navigate(WorkoutRoute(it)) },
            onOpenHistory = { navController.navigate(HistoryRoute) },
            onCreateRoutine = { navController.navigate(RoutineEditRoute()) },
        )
    }
    composable<RoutineDetailRoute> {
        RoutineDetailDestination(
            onBack = { navController.popBackStack() },
            onEditRoutine = { navController.navigate(RoutineEditRoute(it)) },
            // Atrás desde la sesión vuelve a Entrenar, no al detalle de la rutina.
            onOpenWorkout = { sessionId ->
                navController.navigate(WorkoutRoute(sessionId)) {
                    popUpTo<RoutineDetailRoute> { inclusive = true }
                }
            },
        )
    }
    composable<WorkoutRoute> {
        WorkoutDestination(
            onBack = { navController.popBackStack() },
            onFinished = { sessionId ->
                navController.navigate(WorkoutSummaryRoute(sessionId)) {
                    popUpTo<WorkoutRoute> { inclusive = true }
                }
            },
            onDiscarded = { navController.popToTrain() },
        )
    }
    composable<WorkoutSummaryRoute> {
        WorkoutSummaryDestination(onDone = { navController.popToTrain() })
    }
}

/** Vuelve a la pestaña Entrenar; si no estaba en la pila (no debería), la abre como raíz. */
private fun NavHostController.popToTrain() {
    if (!popBackStack<TrainRoute>(inclusive = false)) {
        navigate(TrainRoute) {
            popUpTo(graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }
}
