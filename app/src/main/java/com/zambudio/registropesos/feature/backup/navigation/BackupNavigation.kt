package com.zambudio.registropesos.feature.backup.navigation

import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.BackupRoute
import com.zambudio.registropesos.feature.backup.BackupRoute as BackupRouteScreen

fun NavGraphBuilder.backupGraph(navController: NavHostController) {
    composable<BackupRoute> {
        BackupRouteScreen(onBack = dropUnlessResumed { navController.popBackStack() })
    }
}
