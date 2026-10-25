package com.example.pdftoolkit.ui.screens.navigations

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.pdftoolkit.ui.screens.finetunescreen.FineTuneScreen
import com.example.pdftoolkit.ui.history.HistoryScreen
import com.example.pdftoolkit.ui.screens.settingscreen.SettingsScreen
import com.example.pdftoolkit.ui.screens.files.FilesScreen
import com.example.pdftoolkit.ui.screens.home.HomeScreen
import com.example.pdftoolkit.ui.screens.scanscreen.ScannerScreen
import com.example.pdftoolkit.ui.screens.batchreviewscreen.BatchReviewScreen

import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.screens.compresspdfscreen.CompressPdfScreen
import com.example.pdftoolkit.ui.screens.mergepdfscreen.MergePdfScreen


@Composable
fun AppNavigation(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                contentPadding = contentPadding,
                onChooseFiles = { /* TODO: open file picker */ },
                onCameraScan = { navController.navigateToScan() },   // Home ka "Camera Scan" button
                onToolClick = { titleRes ->
                    val route = when (titleRes) {
                        R.string.tool_merge_pdf, R.string.tool_batch_merge -> Screen.MergePdf.route
                        R.string.tool_compress -> Screen.CompressPdf.route
                        else -> null   // baaki tools ki screens abhi nahi bani
                    }
                    if (route != null) navController.navigate(route) { launchSingleTop = true }
                },
                onViewAllClick = { navController.navigateToTopLevel(Screen.Files) }
            )
        }
        composable(Screen.Files.route) {
            FilesScreen(contentPadding = contentPadding)
        }
        composable(Screen.Scan.route) {
            // Bottom nav bar is route pe MainScreen mein hide hota hai
            ScannerScreen(
                contentPadding = contentPadding,
                onClose = { navController.popBackStack() },
                onDone = { navController.navigate(Screen.BatchReview.route) { launchSingleTop = true } },
                onFineTuneClick = { navController.navigate(Screen.FineTune.route) { launchSingleTop = true } }
            )
        }
        composable(Screen.CompressPdf.route) {
            // Bottom nav bar is route pe bhi hide hota hai
            CompressPdfScreen(
                contentPadding = contentPadding,
                onBack = { navController.popBackStack() },
                onChangeFile = { /* TODO: file picker */ },
                onHelp = { /* TODO: presets guide bottom sheet */ },
                onPreview = { options -> /* TODO: compressed sample preview */ },
                onCompress = { options ->
                    // TODO: options.preset / imageDpi / toggles ke saath PDF compress karo
                    navController.navigateToTopLevel(Screen.Files)
                }
            )
        }
        composable(Screen.MergePdf.route) {
            // Bottom nav bar is route pe bhi hide hota hai
            MergePdfScreen(
                contentPadding = contentPadding,
                onBack = { navController.popBackStack() },
                onAddFromDevice = { /* TODO: file picker (ActivityResultContracts.OpenMultipleDocuments) */ },
                onAddFromDrive = { /* TODO: Drive picker */ },
                onPreview = { options -> /* TODO: merged PDF ka preview */ },
                onMerge = { options ->
                    // TODO: options.files ko order mein merge karo (page ranges ke saath)
                    navController.navigateToTopLevel(Screen.Files)
                }
            )
        }
        composable(Screen.BatchReview.route) {
            // Bottom nav bar is route pe bhi hide hota hai
            BatchReviewScreen(
                contentPadding = contentPadding,
                onAddMore = { navController.popBackStack() },           // wapas Scanner
                onPageClick = { navController.navigate(Screen.FineTune.route) { launchSingleTop = true } },
                onRetake = { navController.popBackStack() },            // TODO: sirf us page ko retake
                onSavePdf = { options ->
                    // TODO: options se PDF generate karo (fileName, pageSize, quality, OCR, pages)
                    navController.navigateToTopLevel(Screen.Files)
                }
            )
        }
        composable(Screen.FineTune.route) {
            // Bottom nav bar is route pe bhi hide hota hai
            FineTuneScreen(
                contentPadding = contentPadding,
                onClose = { navController.popBackStack() },
                onApplyToPage = { /* TODO: sirf current page pe apply */ navController.popBackStack() },
                onApplyToAll = { /* TODO: saare pages pe apply */ navController.popBackStack() }
            )
        }
        composable(Screen.History.route) { HistoryScreen(contentPadding) }
        composable(Screen.Settings.route) { SettingsScreen(contentPadding) }
    }
}

/** Bottom-nav tabs ke liye: back stack stack nahi hota, state save/restore hoti hai. */
fun NavController.navigateToTopLevel(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Scanner normal screen ki tarah khulta hai, back/X dabane pe pichli screen pe wapas jata hai. */
fun NavController.navigateToScan() {
    navigate(Screen.Scan.route) { launchSingleTop = true }
}