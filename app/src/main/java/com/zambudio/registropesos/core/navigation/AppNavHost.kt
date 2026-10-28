package com.zambudio.registropesos.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.zambudio.registropesos.feature.backup.navigation.backupGraph
import com.zambudio.registropesos.feature.exercises.navigation.exercisesGraph
import com.zambudio.registropesos.feature.history.navigation.historyGraph
import com.zambudio.registropesos.feature.progress.navigation.progressGraph
import com.zambudio.registropesos.feature.routines.navigation.routinesGraph
import com.zambudio.registropesos.feature.settings.navigation.settingsGraph
import com.zambudio.registropesos.feature.workout.navigation.workoutGraph

@Composable
fun AppNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = TrainRoute,
        modifier = modifier,
    ) {
        workoutGraph(navController)
        routinesGraph(navController)
        exercisesGraph(navController)
        progressGraph(navController)
        historyGraph(navController)
        backupGraph(navController)
        settingsGraph(navController)
    }
}
