package com.zambudio.registropesos.feature.history.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.HistoryRoute
import com.zambudio.registropesos.core.navigation.SessionDetailRoute
import com.zambudio.registropesos.core.navigation.dropUnlessResumedWith
import com.zambudio.registropesos.feature.history.detail.SessionDetailRoute as SessionDetailDestination
import com.zambudio.registropesos.feature.history.list.HistoryRoute as HistoryDestination

fun NavGraphBuilder.historyGraph(navController: NavHostController) {
    composable<HistoryRoute> {
        HistoryDestination(
            onBack = dropUnlessResumed { navController.popBackStack() },
            onOpenSession = dropUnlessResumedWith { id: String -> navController.navigate(SessionDetailRoute(id)) },
        )
    }
    composable<SessionDetailRoute> {
        // onBack también lo dispara el evento "borrada": la pantalla protege solo el botón.
        SessionDetailDestination(onBack = { navController.popBackStack() })
    }
}
