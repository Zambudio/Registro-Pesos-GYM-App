package com.zambudio.registropesos.feature.exercises.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.ExerciseEditRoute
import com.zambudio.registropesos.core.navigation.ExercisesRoute
import com.zambudio.registropesos.core.navigation.MuscleGroupsRoute
import com.zambudio.registropesos.feature.exercises.ExerciseEditorRoute
import com.zambudio.registropesos.feature.exercises.ExercisesListRoute
import com.zambudio.registropesos.feature.exercises.musclegroups.MuscleGroupsListRoute

fun NavGraphBuilder.exercisesGraph(navController: NavHostController) {
    composable<ExercisesRoute> {
        ExercisesListRoute(
            onOpenExercise = { id -> navController.navigate(ExerciseEditRoute(exerciseId = id)) },
            onAddExercise = { groupId -> navController.navigate(ExerciseEditRoute(muscleGroupId = groupId)) },
            onManageGroups = { navController.navigate(MuscleGroupsRoute) },
        )
    }
    // Los argumentos de ExerciseEditRoute los lee el ViewModel con savedStateHandle.toRoute().
    composable<ExerciseEditRoute> {
        ExerciseEditorRoute(onClose = dropUnlessResumed { navController.popBackStack() })
    }
    composable<MuscleGroupsRoute> {
        MuscleGroupsListRoute(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
