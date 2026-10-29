package com.zambudio.registropesos.feature.exercises.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.ExerciseEditRoute
import com.zambudio.registropesos.core.navigation.ExercisesRoute
import com.zambudio.registropesos.core.navigation.MuscleGroupsRoute
import com.zambudio.registropesos.core.navigation.dropUnlessResumedWith
import com.zambudio.registropesos.feature.exercises.ExerciseEditorRoute
import com.zambudio.registropesos.feature.exercises.ExercisesListRoute
import com.zambudio.registropesos.feature.exercises.musclegroups.MuscleGroupsListRoute

fun NavGraphBuilder.exercisesGraph(navController: NavHostController) {
    composable<ExercisesRoute> {
        ExercisesListRoute(
            onOpenExercise = dropUnlessResumedWith { id: String ->
                navController.navigate(ExerciseEditRoute(exerciseId = id))
            },
            onAddExercise = dropUnlessResumedWith { groupId: String ->
                navController.navigate(ExerciseEditRoute(muscleGroupId = groupId))
            },
            onManageGroups = dropUnlessResumed { navController.navigate(MuscleGroupsRoute) },
        )
    }
    // Los argumentos de ExerciseEditRoute los lee el ViewModel con savedStateHandle.toRoute().
    composable<ExerciseEditRoute> {
        // Cierre por evento (tras guardar/archivar): sin dropUnlessResumed para no perderlo en STARTED;
        // popBackStack<Ruta>(inclusive) es idempotente, un segundo cierre no saca otra pantalla.
        ExerciseEditorRoute(onClose = { navController.popBackStack<ExerciseEditRoute>(inclusive = true) })
    }
    composable<MuscleGroupsRoute> {
        MuscleGroupsListRoute(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
