package com.example.pdftoolkit.watermark

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.reorder.SavingDialog
import com.example.pdftoolkit.reorder.openFile
import com.example.pdftoolkit.reorder.shareFile
import com.example.pdftoolkit.watermarkscreen.PageSize
import com.example.pdftoolkit.watermarkscreen.WatermarkGeometry
import com.example.pdftoolkit.watermarkscreen.WatermarkPosition
import com.example.pdftoolkit.watermarkscreen.WatermarkRenderer
import kotlin.collections.getOrNull
import kotlin.math.min
import kotlin.math.roundToInt

private val SWATCHES = listOf(
    0xFF808080.toInt(), 0xFF000000.toInt(), 0xFFD32F2F.toInt(), 0xFF1976D2.toInt(),
    0xFF388E3C.toInt(), 0xFFF57C00.toInt(), 0xFF7B1FA2.toInt(), 0xFF00796B.toInt()
)
private val SUGGESTIONS = listOf("CONFIDENTIAL", "DRAFT", "COPY", "SAMPLE", "DO NOT COPY")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatermarkScreen(
    onBack: () -> Unit,
    viewModel: WatermarkViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPagePicker by remember { mutableStateOf(false) }

    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onPdfPicked)
    }
    val launchPicker = { pickPdf.launch(arrayOf("application/pdf")) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Watermark") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasFile && state.result == null && !state.isSaving) {
                        IconButton(onClick = launchPicker) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose another PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.hasFile && state.result == null) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Button(
                        onClick = viewModel::apply,
                        enabled = !state.isSaving,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Outlined.WaterDrop, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Watermark")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val result = state.result
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Opening PDF…", style = MaterialTheme.typography.bodyMedium)
                }

                result != null -> SuccessView(
                    result = result,
                    onOpen = { openFile(context, result.uri) },
                    onShare = { shareFile(context, result.uri) },
                    onEditAgain = viewModel::backToEditing,
                    onPickAnother = launchPicker
                )

                !state.hasFile -> EmptyState(onPick = launchPicker)

                else -> WatermarkForm(
                    state = state,
                    viewModel = viewModel,
                    onChoosePages = { showPagePicker = true }
                )
            }
        }
    }

    if (showPagePicker && state.hasFile) {
        PagePickerDialog(
            state = state,
            onToggle = viewModel::toggleSelect,
            onSelectAll = viewModel::selectAll,
            onClear = viewModel::clearSelection,
            loadThumbnail = { viewModel.thumbnail(it) },
            onDone = { showPagePicker = false }
        )
    }

    if (state.isSaving) {
        SavingDialog(
            progress = state.progress,
            label = state.progressLabel,
            onCancel = viewModel::cancelApply,
            title = "Adding watermark"
        )
    }
}

// ───────────────────────── Empty ─────────────────────────

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Select a PDF", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Stamp your own text on every page, or only on the pages you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose PDF")
        }
    }
}

// ───────────────────────── Form ─────────────────────────

@Composable
private fun WatermarkForm(
    state: WatermarkUiState,
    viewModel: WatermarkViewModel,
    onChoosePages: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FileCard(state.fileName.orEmpty(), "${state.pageCount} pages")

        PreviewCard(state = state, viewModel = viewModel)

        SectionCard("Watermark text") {
            OutlinedTextField(
                value = state.text,
                onValueChange = viewModel::setText,
                label = { Text("Text") },
                placeholder = { Text("e.g. CONFIDENTIAL") },
                singleLine = true,
                isError = state.textIssue != null,
                supportingText = {
                    Text(state.textIssue ?: "${state.text.length}/${WatermarkUiState.MAX_TEXT}")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUGGESTIONS.forEach { s ->
                    AssistChip(onClick = { viewModel.setText(s) }, label = { Text(s) })
                }
            }
        }

        SectionCard("Style") {
            LabeledSlider(
                label = "Font size",
                valueText = "${state.fontSize.roundToInt()} pt",
                value = state.fontSize,
                range = 12f..200f,
                onChange = viewModel::setFontSize
            )
            LabeledSlider(
                label = "Opacity",
                valueText = "${(state.opacity * 100).roundToInt()}%",
                value = state.opacity,
                range = 0.05f..1f,
                onChange = viewModel::setOpacity
            )
            LabeledSlider(
                label = "Rotation",
                valueText = "${state.angle.roundToInt()}°",
                value = state.angle,
                range = -180f..180f,
                onChange = viewModel::setAngle
            )
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SWATCHES.forEach { argb ->
                    val selected = argb == state.colorArgb
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setColor(argb) }
                    )
                }
            }
        }

        SectionCard("Position") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WatermarkPosition.values().forEach { pos ->
                    FilterChip(
                        selected = state.position == pos,
                        onClick = { viewModel.setPosition(pos) },
                        label = { Text(pos.label) }
                    )
                }
            }
            if (state.position == WatermarkPosition.DIAGONAL) {
                Text(
                    "Diagonal sets the rotation to 45°. You can still adjust it above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard("Apply to") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.scope == WatermarkScope.ALL,
                    onClick = { viewModel.setScope(WatermarkScope.ALL) },
                    label = { Text("All pages") }
                )
                FilterChip(
                    selected = state.scope == WatermarkScope.SELECTED,
                    onClick = { viewModel.setScope(WatermarkScope.SELECTED) },
                    label = { Text("Selected pages") }
                )
            }
            if (state.scope == WatermarkScope.SELECTED) {
                OutlinedButton(
                    onClick = onChoosePages,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.selected.isEmpty()) "Choose pages"
                        else "Choose pages (${state.selected.size} selected)"
                    )
                }
                state.pagesIssue?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Text(
            "A new watermarked copy is saved. Your original PDF is not changed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ───────────────────────── Preview ─────────────────────────

@Composable
private fun PreviewCard(state: WatermarkUiState, viewModel: WatermarkViewModel) {
    val index = state.previewIndex.coerceIn(0, state.pageCount - 1)
    val size = state.pageSizes.getOrNull(index) ?: PageSize(595f, 842f)

    val thumb by produceState<Bitmap?>(initialValue = null, state.docId, index) {
        value = viewModel.thumbnail(index, 900)
    }
    val wm = remember(state.text, state.fontSize, state.colorArgb) {
        if (state.text.isBlank()) null
        else WatermarkRenderer.render(state.text.trim(), state.fontSize, state.colorArgb, pxPerPt = 2f, maxWidthPx = 1600)
    }

    SectionCard("Preview • Page ${index + 1}") {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val aspect = size.width / size.height
            val previewW = min(maxWidth.value, 320f * aspect).dp
            Box(
                Modifier
                    .width(previewW)
                    .aspectRatio(aspect)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumb
                if (bmp != null) {
                    Image(
                        bitmap = remember(bmp) { bmp.asImageBitmap() },
                        contentDescription = "Page preview",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                if (wm != null) {
                    val image = remember(wm) { wm.bitmap.asImageBitmap() }
                    Canvas(Modifier.fillMaxSize()) {
                        val scale = this.size.width / size.width
                        val wPx = wm.widthPt * scale
                        val hPx = wm.heightPt * scale
                        val (vx, vy) = WatermarkGeometry.center(state.position, size.width, size.height, wm.heightPt)
                        val cx = vx * scale
                        val cy = (size.height - vy) * scale
                        rotate(degrees = -state.angle, pivot = Offset(cx, cy)) {
                            drawImage(
                                image = image,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(image.width, image.height),
                                dstOffset = IntOffset((cx - wPx / 2).roundToInt(), (cy - hPx / 2).roundToInt()),
                                dstSize = IntSize(wPx.roundToInt().coerceAtLeast(1), hPx.roundToInt().coerceAtLeast(1)),
                                alpha = state.opacity
                            )
                        }
                    }
                } else {
                    Text(
                        "Type watermark text to see the preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// ───────────────────────── Page picker ─────────────────────────

@Composable
private fun PagePickerDialog(
    state: WatermarkUiState,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?,
    onDone: () -> Unit
) {
    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Choose pages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${state.selected.size} of ${state.pageCount} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onSelectAll, enabled = state.selected.size < state.pageCount) { Text("All") }
                    TextButton(onClick = onClear, enabled = state.selected.isNotEmpty()) { Text("Clear") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items((0 until state.pageCount).toList(), key = { it }) { index ->
                        PickPageCard(
                            docId = state.docId,
                            index = index,
                            selected = index in state.selected,
                            onClick = { onToggle(index) },
                            loadThumbnail = loadThumbnail
                        )
                    }
                }
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) { Text("Done") }
                }
            }
        }
    }
}

@Composable
private fun PickPageCard(
    docId: Int,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?
) {
    val thumb by produceState<Bitmap?>(initialValue = null, docId, index) { value = loadThumbnail(index) }
    val shape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumb
                if (bmp != null) {
                    Image(
                        bitmap = remember(bmp) { bmp.asImageBitmap() },
                        contentDescription = "Page ${index + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Page ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ───────────────────────── Success ─────────────────────────

@Composable
private fun SuccessView(
    result: WatermarkResult,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onEditAgain: () -> Unit,
    onPickAnother: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text("Watermark added", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            result.displayName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            "Stamped on ${result.pagesStamped} page${if (result.pagesStamped == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpen, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Open")
        }
        OutlinedButton(onClick = onShare, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
        TextButton(onClick = onEditAgain) { Text("Change settings and apply again") }
        TextButton(onClick = onPickAnother) { Text("Choose another PDF") }
    }
}

// ───────────────────────── Small building blocks ─────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun FileCard(name: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}