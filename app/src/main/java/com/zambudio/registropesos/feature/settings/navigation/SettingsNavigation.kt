package com.zambudio.registropesos.feature.settings.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.AboutRoute
import com.zambudio.registropesos.core.navigation.BackupRoute
import com.zambudio.registropesos.core.navigation.HistoryRoute
import com.zambudio.registropesos.core.navigation.MoreRoute
import com.zambudio.registropesos.core.navigation.MuscleGroupsRoute
import com.zambudio.registropesos.feature.settings.AboutRoute as AboutRouteScreen
import com.zambudio.registropesos.feature.settings.MoreRoute as MoreRouteScreen

fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<MoreRoute> {
        MoreRouteScreen(
            onOpenBackup = dropUnlessResumed { navController.navigate(BackupRoute) },
            onOpenMuscleGroups = dropUnlessResumed { navController.navigate(MuscleGroupsRoute) },
            onOpenHistory = dropUnlessResumed { navController.navigate(HistoryRoute) },
            onOpenAbout = dropUnlessResumed { navController.navigate(AboutRoute) },
        )
    }
    composable<AboutRoute> {
        AboutRouteScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
