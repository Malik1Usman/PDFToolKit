package com.example.pdftoolkit.ui.screens.main

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pdftoolkit.ui.screens.components.AppBottomBar
import com.example.pdftoolkit.ui.screens.components.AppTopBar
import com.example.pdftoolkit.ui.screens.navigations.AppNavigation
import com.example.pdftoolkit.ui.screens.navigations.Screen
import com.example.pdftoolkit.ui.screens.navigations.allScreens
import com.example.pdftoolkit.ui.screens.navigations.navigateToScan
import com.example.pdftoolkit.ui.screens.navigations.navigateToTopLevel
import com.example.pdftoolkit.ui.theme.AppColors

/**
 * Main container: Top bar + Bottom nav bar sirf baseline navbar screens (Home, Files, History, Settings) par hi dikhte hain.
 * Sub-screens / tool screens (Watermark, Set Password, Extract Text, Compress, Merge, Split, etc.) par bottom bar hide ho jata hai.
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentScreen = allScreens.firstOrNull { it.route == currentRoute } ?: Screen.Home

    val isTopLevelRoute = currentRoute in setOf(
        Screen.Home.route,
        Screen.Files.route,
        Screen.History.route,
        Screen.Settings.route
    )

    Scaffold(
        containerColor = AppColors.Background,
        topBar = {
            if (isTopLevelRoute) {
                AppTopBar(subtitle = stringResource(currentScreen.titleRes))
            }
        },
        bottomBar = {
            if (isTopLevelRoute) {
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
        },
        content = { innerPadding: PaddingValues ->
            AppNavigation(
                navController = navController,
                contentPadding = if (isTopLevelRoute) innerPadding else PaddingValues(0.dp)
            )
        }
    )
}
