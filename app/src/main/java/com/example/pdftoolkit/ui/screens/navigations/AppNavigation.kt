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
import com.example.pdftoolkit.ui.screens.scanscreen.ScanSessionViewModel
import com.example.pdftoolkit.ui.screens.batchreviewscreen.BatchReviewScreen
import com.example.pdftoolkit.ui.screens.cropscreen.CropEditorScreen

import com.example.pdftoolkit.ui.screens.extracttextscreen.ExtractTextScreen
import com.example.pdftoolkit.core.scan.Quad
import androidx.compose.ui.graphics.ImageBitmap

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.core.pdf.OutputActions
import com.example.pdftoolkit.delete.DeletePagesScreen
import com.example.pdftoolkit.extract.ExtractTextScreen
import com.example.pdftoolkit.password.SetPasswordScreen

import com.example.pdftoolkit.reorder.ReorderPdfScreen
import com.example.pdftoolkit.rotate.RotateScreen
import com.example.pdftoolkit.toword.PdfToWordScreen
import com.example.pdftoolkit.ui.screens.components.ToolStateHost
import com.example.pdftoolkit.ui.screens.compresspdfscreen.CompressViewModel
import com.example.pdftoolkit.ui.screens.mergepdfscreen.MergeViewModel
import com.example.pdftoolkit.ui.screens.splitpdfscreen.SplitPdfScreen
import com.example.pdftoolkit.ui.screens.splitpdfscreen.SplitViewModel
import com.example.pdftoolkit.watermark.WatermarkScreen

private val PDF_MIME = arrayOf("application/pdf")

@Composable
fun AppNavigation(
    navController: NavHostController,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val scanViewModel: ScanSessionViewModel = viewModel()
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
                        R.string.tool_set_password -> "set_password"
                        R.string.tool_watermark -> "watermark"
                        R.string.tool_extract_text -> "extract_text"
                        R.string.tool_rotate -> "rotate"
                        R.string.tool_delete_pages -> "delete_pages"
                        R.string.tool_reorder -> "reorder_pdf"
                        R.string.tool_to_word->"pdf_to_word"
                        else -> null
                    }
                    if (route != null) navController.navigate(route) { launchSingleTop = true }
                },
                onViewAllClick = { navController.navigateToTopLevel(Screen.Files) }
            )
        }

        composable("watermark") {
            WatermarkScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Files.route) {
            FilesScreen(contentPadding = contentPadding)
        }
        composable(Screen.Scan.route) {
            ScannerScreen(
                viewModel = scanViewModel,
                contentPadding = contentPadding,
                onClose = { navController.popBackStack() },
                onDone = { navController.navigate(Screen.BatchReview.route) { launchSingleTop = true } },
                onFineTuneClick = {
                    val lastId = scanViewModel.lastPageId()
                    if (lastId != null) {
                        scanViewModel.setEditingPage(lastId)
                        navController.navigate(Screen.FineTune.route) { launchSingleTop = true }
                    }
                }
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
        composable("pdf_to_word") {
            PdfToWordScreen(onBack = { navController.popBackStack() })
        }
        composable("extract_text") {
            ExtractTextScreen(onBack = { navController.popBackStack() })
        }
        composable("delete_pages") {
            DeletePagesScreen(onBack = { navController.popBackStack() })
        }
        composable("reorder_pdf") {
            ReorderPdfScreen(onBack = { navController.popBackStack() })
        }
        composable("rotate") {
            RotateScreen(onBack = { navController.popBackStack() })
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

        composable("set_password") {
            SetPasswordScreen(onBack = { navController.popBackStack() })
        }
//        composable(Screen.Watermark.route) {
//            val vm: com.example.pdftoolkit.ui.screens.watermarkscreen.WatermarkViewModel = viewModel()
//            WatermarkScreen(contentPadding = contentPadding, viewModel = vm, onBack = { navController.popBackStack() })
//        }
        composable(Screen.ExtractText.route) {
            val vm: com.example.pdftoolkit.ui.screens.extracttextscreen.ExtractTextViewModel = viewModel()
            ExtractTextScreen(contentPadding = contentPadding, viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable(Screen.PageEdit.route) {
            ReorderPdfScreen(onBack = { navController.popBackStack() })
        }

        /* ----------------------------- SCANNER FLOW ----------------------------- */
        composable(Screen.BatchReview.route) {
            BatchReviewScreen(
                viewModel = scanViewModel,
                contentPadding = contentPadding,
                onAddMore = { navController.popBackStack() },
                onPageClick = { pageId ->
                    scanViewModel.setEditingPage(pageId)
                    navController.navigate(Screen.Crop.route) { launchSingleTop = true }
                },
                onRetake = { pageId ->
                    scanViewModel.setRetake(pageId)
                    navController.popBackStack()
                },
                onAutoCropAll = { scanViewModel.autoCropAll() },
                onSavePdf = { options ->
                    scanViewModel.export(options)
                }
            )
        }
        composable(Screen.Crop.route) {
            var originalBmp by remember { mutableStateOf<ImageBitmap?>(null) }
            val pageId = scanViewModel.editingPageId
            val initialQuad = pageId?.let { scanViewModel.quadOf(it) } ?: Quad.fullFrame(0f)

            LaunchedEffect(pageId) {
                if (pageId != null) {
                    originalBmp = scanViewModel.loadOriginalBitmap(pageId)
                }
            }

            CropEditorScreen(
                contentPadding = contentPadding,
                image = originalBmp,
                initialQuad = initialQuad,
                onAutoDetect = {
                    if (pageId != null) scanViewModel.detectFor(pageId) else null
                },
                onBack = { navController.popBackStack() },
                onApply = { q ->
                    if (pageId != null) scanViewModel.updateQuad(pageId, q)
                    navController.popBackStack()
                },
                onApplyAndEnhance = { q ->
                    if (pageId != null) scanViewModel.updateQuad(pageId, q)
                    navController.navigate(Screen.FineTune.route) { launchSingleTop = true }
                }
            )
        }
        composable(Screen.FineTune.route) {
            FineTuneScreen(
                contentPadding = contentPadding,
                viewModel = scanViewModel,
                onClose = { navController.popBackStack() },
                onApply = { navController.popBackStack() }
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