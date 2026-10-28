package com.zambudio.registropesos.feature.routines.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.ExerciseEditRoute
import com.zambudio.registropesos.core.navigation.RoutineEditRoute
import com.zambudio.registropesos.core.navigation.RoutinesRoute
import com.zambudio.registropesos.core.navigation.dropUnlessResumedWith
import com.zambudio.registropesos.feature.routines.edit.RoutineEditRoute as RoutineEditScreenRoute
import com.zambudio.registropesos.feature.routines.list.RoutinesRoute as RoutinesScreenRoute

fun NavGraphBuilder.routinesGraph(navController: NavHostController) {
    composable<RoutinesRoute> {
        RoutinesScreenRoute(
            onOpenRoutine = dropUnlessResumedWith { id: String ->
                navController.navigate(RoutineEditRoute(routineId = id))
            },
            onCreateRoutine = dropUnlessResumed { navController.navigate(RoutineEditRoute()) },
        )
    }
    composable<RoutineEditRoute> {
        // Los argumentos los lee el ViewModel con savedStateHandle.toRoute<RoutineEditRoute>().
        RoutineEditScreenRoute(
            onClose = { navController.navigateUp() },
            onDeleted = {
                // Si se llegó desde el detalle de la rutina (Entrenar), ese destino ya no es válido:
                // se vuelve a la lista si está en la pila; si no, simplemente atrás.
                if (!navController.popBackStack<RoutinesRoute>(inclusive = false)) navController.navigateUp()
            },
            onCreateExercise = dropUnlessResumedWith { groupId: String ->
                navController.navigate(ExerciseEditRoute(muscleGroupId = groupId))
            },
        )
    }
}
