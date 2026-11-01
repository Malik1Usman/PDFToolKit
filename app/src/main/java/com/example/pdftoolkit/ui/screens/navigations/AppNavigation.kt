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


import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.core.pdf.OutputActions
import com.example.pdftoolkit.ui.screens.components.ToolStateHost
import com.example.pdftoolkit.ui.screens.compresspdfscreen.CompressViewModel
import com.example.pdftoolkit.ui.screens.mergepdfscreen.MergeViewModel
import com.example.pdftoolkit.ui.screens.splitpdfscreen.SplitPdfScreen
import com.example.pdftoolkit.ui.screens.splitpdfscreen.SplitViewModel

private val PDF_MIME = arrayOf("application/pdf")

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
                onCameraScan = { navController.navigateToScan() },
                onToolClick = { titleRes ->
                    val route = when (titleRes) {
                        R.string.tool_merge_pdf, R.string.tool_batch_merge -> Screen.MergePdf.route
                        R.string.tool_compress -> Screen.CompressPdf.route
                        R.string.tool_split_pdf -> Screen.SplitPdf.route
                        else -> null   // baaki tools agli slices mein
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
            ScannerScreen(
                contentPadding = contentPadding,
                onClose = { navController.popBackStack() },
                onDone = { navController.navigate(Screen.BatchReview.route) { launchSingleTop = true } },
                onFineTuneClick = { navController.navigate(Screen.FineTune.route) { launchSingleTop = true } }
            )
        }

        /* ----------------------------- MERGE ----------------------------- */
        composable(Screen.MergePdf.route) {
            val vm: MergeViewModel = viewModel()
            val state by vm.state.collectAsState()
            val context = LocalContext.current

            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                if (uris.isNotEmpty()) vm.addFiles(uris)
                else if (vm.files.isEmpty()) navController.popBackStack()
            }
            var asked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!asked && vm.files.isEmpty()) {
                    asked = true
                    picker.launch(PDF_MIME)        // tool khulte hi file picker
                }
            }

            Box(Modifier.fillMaxSize()) {
                MergePdfScreen(
                    files = vm.files,
                    contentPadding = contentPadding,
                    onBack = { navController.popBackStack() },
                    onAddFromDevice = { picker.launch(PDF_MIME) },
                    onAddFromDrive = { picker.launch(PDF_MIME) },   // system picker mein Drive bhi aata hai
                    onPreview = { options -> vm.merge(options) },   // abhi: output banao + Open dialog
                    onMerge = { options -> vm.merge(options) }
                )
                ToolStateHost(
                    state = state,
                    onCancel = vm::cancel,
                    onDismiss = vm::dismissState,
                    onOpen = { OutputActions.open(context, it) },
                    onShare = { OutputActions.share(context, it) },
                    onDone = {
                        vm.dismissState()
                        navController.popBackStack()
                    }
                )
            }
        }

        /* ----------------------------- COMPRESS ----------------------------- */
        composable(Screen.CompressPdf.route) {
            val vm: CompressViewModel = viewModel()
            val state by vm.state.collectAsState()
            val source by vm.source.collectAsState()
            val context = LocalContext.current

            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.setSource(uri)
                else if (vm.source.value == null) navController.popBackStack()
            }
            var asked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!asked && vm.source.value == null) {
                    asked = true
                    picker.launch(PDF_MIME)
                }
            }

            Box(Modifier.fillMaxSize()) {
                source?.let { src ->
                    key(src.uri) {
                        CompressPdfScreen(
                            contentPadding = contentPadding,
                            sourceName = src.displayName,
                            sourceSizeMb = (src.sizeBytes / 1_048_576f).coerceAtLeast(0.01f),
                            pageCount = src.pageCount,
                            hasHighResScans = false,
                            onBack = { navController.popBackStack() },
                            onChangeFile = { picker.launch(PDF_MIME) },
                            onPreview = { options -> vm.compress(options) },
                            onCompress = { options -> vm.compress(options) }
                        )
                    }
                }
                ToolStateHost(
                    state = state,
                    onCancel = vm::cancel,
                    onDismiss = {
                        vm.dismissState()
                        if (vm.source.value == null) navController.popBackStack()   // unreadable file
                    },
                    onOpen = { OutputActions.open(context, it) },
                    onShare = { OutputActions.share(context, it) },
                    onDone = {
                        vm.dismissState()
                        navController.popBackStack()
                    }
                )
            }
        }

        /* ----------------------------- SPLIT ----------------------------- */
        composable(Screen.SplitPdf.route) {
            val vm: SplitViewModel = viewModel()
            val state by vm.state.collectAsState()
            val source by vm.source.collectAsState()
            val context = LocalContext.current

            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) vm.setSource(uri)
                else if (vm.source.value == null) navController.popBackStack()
            }
            var asked by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                if (!asked && vm.source.value == null) {
                    asked = true
                    picker.launch(PDF_MIME)
                }
            }

            Box(Modifier.fillMaxSize()) {
                source?.let { src ->
                    key(src.uri) {
                        SplitPdfScreen(
                            contentPadding = contentPadding,
                            sourceName = src.displayName,
                            sourceSizeMb = (src.sizeBytes / 1_048_576f).coerceAtLeast(0.01f),
                            pageCount = src.pageCount,
                            initialOutputName = src.displayName.removeSuffix(".pdf") + "_Extract_Pages.pdf",
                            onBack = { navController.popBackStack() },
                            onChangeFile = { picker.launch(PDF_MIME) },
                            onPreview = { options -> vm.split(options) },
                            onSplit = { options -> vm.split(options) }
                        )
                    }
                }
                ToolStateHost(
                    state = state,
                    onCancel = vm::cancel,
                    onDismiss = {
                        vm.dismissState()
                        if (vm.source.value == null) navController.popBackStack()
                    },
                    onOpen = { OutputActions.open(context, it) },
                    onShare = { OutputActions.share(context, it) },
                    onDone = {
                        vm.dismissState()
                        navController.popBackStack()
                    }
                )
            }
        }

        /* ----------------------------- SCANNER FLOW (agli slice mein real) ----------------------------- */
        composable(Screen.BatchReview.route) {
            BatchReviewScreen(
                contentPadding = contentPadding,
                onAddMore = { navController.popBackStack() },
                onPageClick = { navController.navigate(Screen.FineTune.route) { launchSingleTop = true } },
                onRetake = { navController.popBackStack() },
                onSavePdf = { options ->
                    // TODO (slice: Scanner): options se PDF generate karo
                    navController.navigateToTopLevel(Screen.Files)
                }
            )
        }
        composable(Screen.FineTune.route) {
            FineTuneScreen(
                contentPadding = contentPadding,
                onClose = { navController.popBackStack() },
                onApplyToPage = { navController.popBackStack() },
                onApplyToAll = { navController.popBackStack() }
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