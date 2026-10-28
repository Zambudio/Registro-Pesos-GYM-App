package com.zambudio.registropesos.feature.settings.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.MoreRoute
import com.zambudio.registropesos.core.navigation.AboutRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<MoreRoute> { PlaceholderScreen("MoreRoute") }
    composable<AboutRoute> { PlaceholderScreen("AboutRoute") }
}
