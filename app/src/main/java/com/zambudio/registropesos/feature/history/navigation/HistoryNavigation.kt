package com.zambudio.registropesos.feature.history.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.HistoryRoute
import com.zambudio.registropesos.core.navigation.SessionDetailRoute
import com.zambudio.registropesos.feature.history.detail.SessionDetailRoute as SessionDetailDestination
import com.zambudio.registropesos.feature.history.list.HistoryRoute as HistoryDestination

fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable<HistoryRoute> {
        HistoryDestination(
            onBack = { navController.popBackStack() },
            onOpenSession = { navController.navigate(SessionDetailRoute(it)) },
        )
    }
    composable<SessionDetailRoute> {
        SessionDetailDestination(onBack = { navController.popBackStack() })
    }
}
