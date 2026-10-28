package com.zambudio.registropesos.feature.backup.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.zambudio.registropesos.core.navigation.BackupRoute
import com.zambudio.registropesos.core.ui.PlaceholderScreen

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.backupGraph(navController: NavHostController) {
    composable<BackupRoute> { PlaceholderScreen("BackupRoute") }
}
