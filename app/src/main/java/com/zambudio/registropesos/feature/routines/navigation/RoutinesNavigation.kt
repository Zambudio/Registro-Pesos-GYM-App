package com.zambudio.registropesos.feature.routines.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.RoutinesRoute
import com.zambudio.registropesos.core.navigation.RoutineEditRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.routinesGraph(navController: NavHostController) {
    composable<RoutinesRoute> { PlaceholderScreen("RoutinesRoute") }
    composable<RoutineEditRoute> { PlaceholderScreen("RoutineEditRoute") }
}
