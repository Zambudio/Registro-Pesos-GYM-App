package com.zambudio.registropesos.feature.history.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.HistoryRoute
import com.zambudio.registropesos.core.navigation.SessionDetailRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable<HistoryRoute> { PlaceholderScreen("HistoryRoute") }
    composable<SessionDetailRoute> { PlaceholderScreen("SessionDetailRoute") }
}
