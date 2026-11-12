package com.example.pdftoolkit.ui.screens.navigations

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pdftoolkit.R

/** Har destination ka route, title aur icon yahin define hai. */
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

    /** Bottom bar ka part nahi: Scanner / Home tools se khulte hain. */
    object FineTune : Screen("scan_fine_tune", R.string.fine_top_subtitle, Icons.Outlined.DocumentScanner)
    object BatchReview : Screen("scan_batch_review", R.string.fine_top_subtitle, Icons.Outlined.DocumentScanner)
    object Crop : Screen("scan_crop", R.string.crop_title, Icons.Outlined.Crop)
    object MergePdf : Screen("merge_pdf", R.string.merge_title, Icons.Outlined.MergeType)
    object CompressPdf : Screen("compress_pdf", R.string.compress_title, Icons.Outlined.Compress)
    object SplitPdf : Screen("split_pdf", R.string.split_title, Icons.Outlined.CallSplit)
    object Protect : Screen("set_password", R.string.tool_set_password, Icons.Outlined.Lock)
    object Watermark : Screen("watermark", R.string.tool_watermark, Icons.Outlined.PictureInPictureAlt)
    object ExtractText : Screen("extract_text", R.string.tool_extract_text, Icons.Outlined.TextFields)
    object PageEdit : Screen("reorder_pdf", R.string.tool_rotate, Icons.Outlined.RotateRight)
}

/** Wo routes jin par bottom nav bar hide hota hai. */
val routesWithoutBottomBar = setOf(
    Screen.Scan.route,
    Screen.FineTune.route,
    Screen.BatchReview.route,
    Screen.Crop.route,
    Screen.MergePdf.route,
    Screen.CompressPdf.route,
    Screen.SplitPdf.route,
    Screen.Protect.route,
    Screen.Watermark.route,
    Screen.ExtractText.route,
    Screen.PageEdit.route,
    "pdf_to_word",
    "rotate",
    "delete_pages"
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
val allScreens: List<Screen> = bottomNavScreens + Screen.FineTune + Screen.BatchReview + Screen.Crop + Screen.Protect + Screen.Watermark + Screen.ExtractText + Screen.PageEdit