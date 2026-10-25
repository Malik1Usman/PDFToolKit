package com.example.pdftoolkit.ui.screens.navigations

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pdftoolkit.R


import androidx.compose.material.icons.outlined.MergeType


import androidx.compose.material.icons.outlined.Compress


/** Har bottom-nav destination ka route, title aur icon yahin define hai. */
sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    object Home : Screen("home", R.string.nav_home, Icons.Outlined.Home)
    object Files : Screen("files", R.string.nav_files, Icons.Outlined.FolderOpen)
    object Scan : Screen("scan", R.string.nav_scan, Icons.Outlined.DocumentScanner)
    object History : Screen("history", R.string.nav_history, Icons.Outlined.History)
    object Settings : Screen("settings", R.string.nav_settings, Icons.Outlined.Settings)

    /** Bottom bar ka part nahi hai; Scanner ke "Fine Tune" se khulta hai. */
    object FineTune : Screen("scan_fine_tune", R.string.fine_top_subtitle, Icons.Outlined.DocumentScanner)

    /** Home ke "Merge PDF" tool se khulta hai. Top bar mein subtitle "Home" hi rehta hai. */
    object MergePdf : Screen("merge_pdf", R.string.merge_title, Icons.Outlined.MergeType)

    /** Home ke "Compress" tool se khulta hai. Top bar mein subtitle "Home" hi rehta hai. */
    object CompressPdf : Screen("compress_pdf", R.string.compress_title, Icons.Outlined.Compress)

    /** Scanner ke "Done" ke baad pages review + PDF export. Bottom bar mein nahi hota. */
    object BatchReview : Screen("scan_batch_review", R.string.fine_top_subtitle, Icons.Outlined.DocumentScanner)
}

/** Wo routes jin par bottom nav bar hide hota hai. */
val routesWithoutBottomBar = setOf(
    Screen.Scan.route,
    Screen.FineTune.route,
    Screen.BatchReview.route,
    Screen.MergePdf.route,
    Screen.CompressPdf.route
)

/** Order = bottom bar ka order. Index 2 (Scan) center raised button hai. */
val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Files,
    Screen.Scan,
    Screen.History,
    Screen.Settings
)

/** Top bar ka subtitle nikalne ke liye saari screens. */
val allScreens: List<Screen> = bottomNavScreens + Screen.FineTune + Screen.BatchReview