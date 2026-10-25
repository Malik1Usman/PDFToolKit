package com.example.pdftoolkit.ui.screens.main

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pdftoolkit.ui.screens.components.AppBottomBar
import com.example.pdftoolkit.ui.screens.components.AppTopBar
import com.example.pdftoolkit.ui.screens.navigations.AppNavigation
import com.example.pdftoolkit.ui.screens.navigations.Screen
import com.example.pdftoolkit.ui.screens.navigations.allScreens
import com.example.pdftoolkit.ui.screens.navigations.bottomNavScreens
import com.example.pdftoolkit.ui.screens.navigations.navigateToScan
import com.example.pdftoolkit.ui.screens.navigations.navigateToTopLevel
import com.example.pdftoolkit.ui.screens.navigations.routesWithoutBottomBar
import com.example.pdftoolkit.ui.theme.AppColors

/**
 * Main container: Top bar + Bottom nav bar yahin hain.
 * Scan, Fine-Tune aur Batch Review screens pe bottom nav bar hide ho jata hai.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentScreen = allScreens.firstOrNull { it.route == currentRoute } ?: Screen.Home
    val hideBottomBar = currentRoute in routesWithoutBottomBar

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            AppTopBar(subtitle = stringResource(currentScreen.titleRes))
        },
        bottomBar = {
            if (!hideBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        if (screen == Screen.Scan) {
                            navController.navigateToScan()
                        } else {
                            navController.navigateToTopLevel(screen)
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            contentPadding = innerPadding
        )
    }
}