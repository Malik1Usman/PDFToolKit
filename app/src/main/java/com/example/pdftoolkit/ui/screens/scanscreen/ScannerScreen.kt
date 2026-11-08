package com.example.pdftoolkit.ui.screens.scanscreen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.scan.CaptureMode
import com.example.pdftoolkit.core.scan.FlashSetting
import com.example.pdftoolkit.core.scan.Quad
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import java.util.Locale

/* ----------------------------- Models ----------------------------- */

private enum class EnhanceFilter(@get:StringRes val label: Int, val icon: ImageVector) {
    MAGIC(R.string.scanner_filter_magic, Icons.Outlined.AutoFixHigh),
    BW(R.string.scanner_filter_bw, Icons.Outlined.Contrast),
    GRAY(R.string.scanner_filter_gray, Icons.Outlined.FilterBAndW),
    WHITEBOARD(R.string.scanner_filter_whiteboard, Icons.Outlined.WbSunny),
    ORIGINAL(R.string.scanner_filter_original, Icons.Outlined.Image)
}

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

/**
 * Full scanner screen with CameraX live camera preview, real-time edge detection,
 * photo capture, and document importing.
 */
@Composable
fun ScannerScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ScanSessionViewModel = viewModel(),
    onClose: () -> Unit = {},
    onDone: (pages: Int) -> Unit = {},
    onFineTuneClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val liveQuad by viewModel.liveQuad.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val flash by viewModel.flash.collectAsState()
    val autoEdge by viewModel.autoEdge.collectAsState()
    val autoCapture by viewModel.autoCapture.collectAsState()
    val capturing by viewModel.capturing.collectAsState()
    val pagesCount = viewModel.pages.size

    var gridOn by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(EnhanceFilter.MAGIC) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan documents", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importUris(uris)
        }
    }

    val cameraController = remember { CameraController() }

    // Auto-capture events from ViewModel
    LaunchedEffect(viewModel) {
        viewModel.captureEvents.collect {
            if (hasCameraPermission && !capturing) {
                viewModel.requestCaptureStarted()
                cameraController.takePicture(
                    context,
                    onSaved = { file -> viewModel.onPhotoCaptured(file) },
                    onFailed = { msg -> viewModel.onCaptureFailed(msg) }
                )
            }
        }
    }

    // Toast messages
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
            .padding(horizontal = ScreenPadding)
    ) {
        Viewfinder(
            autoEdge = autoEdge,
            gridOn = gridOn,
            liveQuad = liveQuad,
            flash = flash,
            onClose = onClose,
            onToggleAutoEdge = { viewModel.setAutoEdge(!autoEdge) },
            onToggleGrid = { gridOn = !gridOn },
            onToggleFlash = { viewModel.cycleFlash() },
            preview = {
                if (hasCameraPermission) {
                    CameraPreviewHost(
                        controller = cameraController,
                        flash = flash,
                        analysisEnabled = autoEdge,
                        onFrame = { frame -> viewModel.onFrame(frame) },
                        onCameraError = { msg -> viewModel.onCaptureFailed(msg) },
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    CameraPermissionRequiredView(
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
            }
        )

        CaptureModeTabs(
            selected = mode,
            onSelected = { viewModel.setMode(it) },
            modifier = Modifier.padding(top = 12.dp)
        )

        CaptureDeck(
            pages = pagesCount,
            autoCapture = autoCapture,
            filter = filter,
            onToggleAuto = { viewModel.autoCapture.value = !autoCapture },
            onFilterSelected = { filter = it },
            onImport = { importLauncher.launch("image/*") },
            onFineTune = onFineTuneClick,
            onCapture = {
                if (hasCameraPermission) {
                    if (!capturing) {
                        viewModel.requestCaptureStarted()
                        cameraController.takePicture(
                            context,
                            onSaved = { file -> viewModel.onPhotoCaptured(file) },
                            onFailed = { msg -> viewModel.onCaptureFailed(msg) }
                        )
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onDone = { onDone(pagesCount) },
            modifier = Modifier.padding(top = 12.dp)
        )

        QuickSaveBar(
            onSavePdf = { onDone(pagesCount) },
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/* ----------------------------- Viewfinder ----------------------------- */

@Composable
private fun Viewfinder(
    autoEdge: Boolean,
    gridOn: Boolean,
    liveQuad: Quad?,
    flash: FlashSetting,
    onClose: () -> Unit,
    onToggleAutoEdge: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleFlash: () -> Unit,
    preview: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.ViewfinderBg)
    ) {
        preview()

        // Vignette / scrim
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xBF000000),
                        0.5f to Color.Transparent,
                        1f to Color(0xD9000000)
                    )
                )
        )

        if (gridOn) GridOverlay(Modifier.matchParentSize())

        if (autoEdge) {
            DocumentQuad(
                modifier = Modifier.matchParentSize(),
                liveQuad = liveQuad
            )
        }

        ViewfinderTopBar(
            autoEdge = autoEdge,
            gridOn = gridOn,
            flash = flash,
            onClose = onClose,
            onToggleAutoEdge = onToggleAutoEdge,
            onToggleGrid = onToggleGrid,
            onToggleFlash = onToggleFlash,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 14.dp, end = 14.dp, top = 18.dp)
        )

        HintBubble(
            isDetected = liveQuad != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        )
    }
}

@Composable
private fun BoxScope.CameraPermissionRequiredView(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .matchParentSize()
            .background(Color(0xFF1E293B))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = null,
            tint = AppColors.Primary,
            modifier = Modifier.size(52.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.camera_permission_subtitle),
            style = AppTextStyle.Label12Regular,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Primary)
                .clickable(onClick = onRequestPermission)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(R.string.camera_permission_button),
                style = AppTextStyle.Button14SemiBold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val line = Color.White.copy(alpha = 0.25f)
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(line, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(line, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Detected document ka quad: live detected quad or default frame. */
@Composable
private fun DocumentQuad(
    modifier: Modifier = Modifier,
    liveQuad: Quad? = null
) {
    if (liveQuad != null) {
        Canvas(modifier = modifier) {
            val w = size.width
            val h = size.height
            val pts = liveQuad.points().map { Offset(it.x * w, it.y * h) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                lineTo(pts[1].x, pts[1].y)
                lineTo(pts[2].x, pts[2].y)
                lineTo(pts[3].x, pts[3].y)
                close()
            }
            drawPath(path, Color(0x332563EB))
            drawPath(path, AppColors.ScanCyan, style = Stroke(width = 3.dp.toPx()))
            pts.forEach { p ->
                drawCircle(Color.White, 8.dp.toPx(), p)
                drawCircle(AppColors.PrimaryButton, 4.dp.toPx(), p)
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(256.27f / 283.16f)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(8.dp),
                    clip = false,
                    ambientColor = AppColors.ScanGlow,
                    spotColor = AppColors.ScanGlow
                )
                .background(Color(0x1A2563EB), RoundedCornerShape(8.dp))
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val stroke = 3.dp.toPx()
                    val len = 24.dp.toPx()
                    val off = 4.dp.toPx()
                    val cyan = AppColors.ScanCyan

                    fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                        drawLine(cyan, Offset(x, y), Offset(x + dx * len, y), stroke, StrokeCap.Round)
                        drawLine(cyan, Offset(x, y), Offset(x, y + dy * len), stroke, StrokeCap.Round)
                    }
                    corner(-off, -off, 1f, 1f)
                    corner(w + off, -off, -1f, 1f)
                    corner(-off, h + off, 1f, -1f)
                    corner(w + off, h + off, -1f, -1f)

                    // Laser beam
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0x0089F5E7), cyan, Color(0x0089F5E7))
                        ),
                        topLeft = Offset.Zero,
                        size = Size(w, 2.dp.toPx()),
                        alpha = 0.9f
                    )

                    // Crosshair
                    val center = Offset(w / 2f, h / 2f)
                    val arm = 13.5.dp.toPx()
                    val cross = Color.White.copy(alpha = 0.4f)
                    drawLine(cross, Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 1.5.dp.toPx())
                    drawLine(cross, Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 1.5.dp.toPx())

                    // Midpoints (teal)
                    listOf(Offset(0f, h / 2f), Offset(w, h / 2f), Offset(w / 2f, 0f), Offset(w / 2f, h)).forEach {
                        drawCircle(Color.White, 8.dp.toPx(), it)
                        drawCircle(AppColors.Teal, 4.dp.toPx(), it)
                    }

                    // Corner handles (blue)
                    listOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h)).forEach {
                        drawCircle(Color.White, 10.dp.toPx(), it)
                        drawCircle(AppColors.PrimaryButton, 5.dp.toPx(), it)
                    }
                }
        )
    }
}

@Composable
private fun ViewfinderTopBar(
    autoEdge: Boolean,
    gridOn: Boolean,
    flash: FlashSetting,
    onClose: () -> Unit,
    onToggleAutoEdge: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cancel
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppColors.ScanOverlay60)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_cancel_scan),
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }

        // Center controls: flash | auto-edge | grid
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(AppColors.ScanOverlay70)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFlash),
                contentAlignment = Alignment.Center
            ) {
                val flashIcon = when (flash) {
                    FlashSetting.AUTO -> Icons.Outlined.FlashAuto
                    FlashSetting.ON -> Icons.Outlined.FlashOn
                    FlashSetting.OFF -> Icons.Outlined.FlashOff
                }
                Icon(
                    flashIcon,
                    stringResource(R.string.cd_flash),
                    tint = if (flash != FlashSetting.OFF) AppColors.ScanCyan else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            VerticalDivider()
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(if (autoEdge) AppColors.TealLight20 else Color.Transparent)
                    .clickable(onClick = onToggleAutoEdge)
                    .padding(start = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (autoEdge) AppColors.ScanCyan else Color.White.copy(alpha = 0.5f))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scanner_auto_edge),
                    style = AppTextStyle.Caption11Medium,
                    color = if (autoEdge) AppColors.ScanCyan else Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            VerticalDivider()
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleGrid),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.GridOn,
                    stringResource(R.string.cd_grid_toggle),
                    tint = if (gridOn) AppColors.ScanCyan else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Resolution pill
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(AppColors.ScanOverlay60)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.scanner_resolution_hd),
                style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.IndigoSoft
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.scanner_resolution_dpi),
                style = AppTextStyle.Caption11Medium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(16.dp)
            .background(Color.White.copy(alpha = 0.15f))
    )
}

@Composable
private fun HintBubble(
    isDetected: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(AppColors.ScanOverlay80)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isDetected) AppColors.Teal else Color.White.copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(if (isDetected) R.string.scanner_hint_detected else R.string.scanner_hint_searching),
            style = AppTextStyle.Caption11Medium,
            color = Color.White
        )
    }
}

/* ----------------------------- Mode tabs ----------------------------- */

@Composable
private fun CaptureModeTabs(
    selected: CaptureMode,
    onSelected: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .background(AppColors.Surface)
            .padding(4.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(CaptureMode.values().toList()) { mode ->
                val isSelected = mode == selected
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AppColors.Primary else Color.Transparent)
                        .clickable { onSelected(mode) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelected) {
                        Icon(Icons.Outlined.Description, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = stringResource(mode.label),
                        style = if (isSelected) AppTextStyle.Label12SemiBold else AppTextStyle.Label12Medium,
                        color = if (isSelected) Color.White else AppColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ----------------------------- Capture deck ----------------------------- */

@Composable
private fun CaptureDeck(
    pages: Int,
    autoCapture: Boolean,
    filter: EnhanceFilter,
    onToggleAuto: () -> Unit,
    onFilterSelected: (EnhanceFilter) -> Unit,
    onImport: () -> Unit,
    onFineTune: () -> Unit,
    onCapture: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-page tray info
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = pages > 0, onClick = onDone)
                    .padding(4.dp)
            ) {
                PageStack(pages)
                Spacer(Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = LocalContext.current.resources
                                .getQuantityString(R.plurals.scanner_pages_scanned, pages, pages),
                            style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                            color = AppColors.TextPrimary
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Teal))
                    }
                    Text(
                        text = stringResource(R.string.scanner_tray_hint),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.SurfaceSofter)
                    .clickable(onClick = onToggleAuto)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Autorenew, null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(if (autoCapture) R.string.scanner_auto_on else R.string.scanner_auto_off),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextPrimary
                )
            }
        }

        // Shutter bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ImportButton(onClick = onImport)
            ShutterButton(onClick = onCapture)
            DoneButton(pages = pages, onClick = onDone)
        }

        // Enhancement filter strip
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.scanner_filter_title).uppercase(Locale.getDefault()),
                    style = AppTextStyle.Caption11Medium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.55.sp
                    ),
                    color = AppColors.TextSecondary80
                )
                Text(
                    text = stringResource(R.string.scanner_fine_tune),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onFineTune)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(EnhanceFilter.values().toList()) { item ->
                    FilterPreset(
                        item = item,
                        selected = item == filter,
                        onClick = { onFilterSelected(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PageStack(pages: Int) {
    Box(modifier = Modifier.size(width = 42.dp, height = 48.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 36.dp, height = 44.dp)
                .rotate(-6f)
                .shadow(1.dp, RoundedCornerShape(6.dp))
                .background(AppColors.SurfaceTrack, RoundedCornerShape(6.dp))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(y = (-2).dp)
                .size(width = 36.dp, height = 44.dp)
                .shadow(3.dp, RoundedCornerShape(6.dp))
                .background(AppColors.IndigoSoft, RoundedCornerShape(6.dp)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Outlined.Image, null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
            Text(
                text = stringResource(R.string.scanner_page_short, pages),
                style = AppTextStyle.Badge10Regular.copy(fontWeight = FontWeight.Bold),
                color = AppColors.Primary
            )
        }
    }
}

@Composable
private fun ImportButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceSoft)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Image, null, tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
        Text(
            text = stringResource(R.string.scanner_import),
            style = AppTextStyle.Badge10Regular.copy(lineHeight = 15.sp),
            color = AppColors.TextSecondary
        )
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
        // Auto-trigger glow ripple
        Box(
            Modifier
                .requiredSize(96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0x333B82F6), Color.Transparent)))
        )
        // Outer ring
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceTrack)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            // Main button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(AppColors.PrimaryButton)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.CameraAlt,
                    contentDescription = stringResource(R.string.cd_capture_page),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun DoneButton(pages: Int, onClick: () -> Unit) {
    val enabled = pages > 0
    Column(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) AppColors.TealLight20 else AppColors.SurfaceSoft)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (enabled) Icons.Outlined.CheckCircle else Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = if (enabled) AppColors.Teal else AppColors.TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.scanner_done, pages),
            style = AppTextStyle.Badge10Regular.copy(lineHeight = 15.sp),
            color = if (enabled) AppColors.Teal else AppColors.TextSecondary
        )
    }
}

@Composable
private fun FilterPreset(
    item: EnhanceFilter,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (selected) AppColors.SurfaceTrack else AppColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) AppColors.Primary else AppColors.TextSecondary,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(item.label),
            style = if (selected) AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold) else AppTextStyle.Caption11Medium,
            color = if (selected) AppColors.Primary else AppColors.TextPrimary
        )
    }
}

@Composable
private fun QuickSaveBar(
    onSavePdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onSavePdf)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.TealLight20),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        tint = AppColors.Teal,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.scanner_batch_title),
                        style = AppTextStyle.Body14SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.scanner_batch_desc),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Primary)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.scanner_save_pdf),
                    style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}
