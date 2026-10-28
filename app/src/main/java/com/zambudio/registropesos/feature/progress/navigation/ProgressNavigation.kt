package com.zambudio.registropesos.feature.progress.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.ProgressRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.progressGraph(navController: NavHostController) {
    composable<ProgressRoute> { PlaceholderScreen("ProgressRoute") }
}
