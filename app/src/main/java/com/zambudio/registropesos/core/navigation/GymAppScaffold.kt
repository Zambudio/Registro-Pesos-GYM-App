package com.zambudio.registropesos.core.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zambudio.registropesos.core.theme.GymTheme

/**
 * Raíz de la UI: barra inferior con las 5 pestañas (solo visible en las pantallas raíz)
 * y el NavHost. Cada pantalla gestiona su propio TopAppBar e insets superiores.
 */
@Composable
fun GymAppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val showBottomBar = TopLevelDestination.entries.any { top -> destination?.hasRoute(top.routeClass) == true }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                GymBottomBar(
                    selected = TopLevelDestination.entries.firstOrNull { top ->
                        destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true
                    },
                    onSelect = { top ->
                        navController.navigate(top.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
        )
    }
}

/** Barra inferior: superficie de tarjeta con un filo superior y el indicador en naranja suave. */
@Composable
internal fun GymBottomBar(selected: TopLevelDestination?, onSelect: (TopLevelDestination) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        HorizontalDivider(color = colors.outlineVariant)
        NavigationBar(containerColor = colors.surfaceContainerLow, tonalElevation = 0.dp) {
            TopLevelDestination.entries.forEach { top ->
                val isSelected = top == selected
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(top) },
                    icon = { Icon(if (isSelected) top.selectedIcon else top.icon, contentDescription = null) },
                    label = {
                        Text(
                            stringResource(top.labelRes),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.onPrimaryContainer,
                        selectedTextColor = colors.primary,
                        indicatorColor = colors.primaryContainer,
                        unselectedIconColor = colors.onSurfaceVariant,
                        unselectedTextColor = colors.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Preview
@Composable
private fun GymBottomBarPreview() {
    GymTheme { GymBottomBar(selected = TopLevelDestination.ROUTINES, onSelect = {}) }
}
