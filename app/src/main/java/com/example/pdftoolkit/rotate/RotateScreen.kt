package com.example.pdftoolkit.rotate

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pdftoolkit.reorder.SavingDialog
import com.example.pdftoolkit.reorder.SuccessDialog
import com.example.pdftoolkit.reorder.openFile
import com.example.pdftoolkit.reorder.shareFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RotateScreen(
    onBack: () -> Unit,
    viewModel: RotateViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }
    val launchPicker = { pickFile.launch(arrayOf("application/pdf", "image/*")) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rotate PDF or Image") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasFile) {
                        IconButton(onClick = launchPicker, enabled = !state.isSaving) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose another file")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.hasFile) {
                RotateBottomBar(
                    state = state,
                    onRotate = viewModel::rotate,
                    onReset = viewModel::resetRotation,
                    onSave = viewModel::save
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when {
                state.isLoading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Opening file…", style = MaterialTheme.typography.bodyMedium)
                }

                !state.hasFile -> RotateEmptyState(onPick = launchPicker)

                state.kind == RotateKind.PDF -> Column(Modifier.fillMaxSize()) {
                    InfoCard(
                        name = state.fileName.orEmpty(),
                        subtitle = "${state.pageCount} pages • ${state.rotatedPageCount} rotated",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    SelectionHeader(
                        selectedCount = state.selected.size,
                        total = state.pageCount,
                        onSelectAll = viewModel::selectAll,
                        onClear = viewModel::clearSelection
                    )
                    PageGrid(
                        state = state,
                        onToggle = viewModel::toggleSelect,
                        loadThumbnail = viewModel::thumbnail,
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> Column(Modifier.fillMaxSize()) {
                    InfoCard(
                        name = state.fileName.orEmpty(),
                        subtitle = if (state.imageRotation == 0) "Image • original orientation"
                        else "Image • rotated ${state.imageRotation}° clockwise",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    ImagePreview(
                        bitmap = state.image,
                        rotation = state.imageRotation,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (state.isSaving) {
        SavingDialog(
            progress = state.progress,
            label = state.progressLabel,
            onCancel = viewModel::cancelSave,
            title = if (state.kind == RotateKind.PDF) "Creating PDF" else "Saving image"
        )
    }

    state.result?.let { result ->
        SuccessDialog(
            displayName = result.displayName,
            onOpen = { openFile(context, result.uri, result.mimeType) },
            onShare = { shareFile(context, result.uri, result.mimeType) },
            onDone = viewModel::dismissResult,
            title = if (result.mimeType == "application/pdf") "PDF saved" else "Image saved"
        )
    }
}

// ───────────────────────── Empty state / info ─────────────────────────

@Composable
private fun RotateEmptyState(onPick: () -> Unit) {
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
                    Icons.Outlined.RotateRight,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Select a PDF or image", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Rotate single pages, selected pages or the whole PDF. Images can be rotated and exported too.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Choose File")
        }
    }
}

@Composable
private fun InfoCard(name: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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

@Composable
private fun SelectionHeader(selectedCount: Int, total: Int, onSelectAll: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selectedCount == 0) "Tap pages to rotate only those" else "$selectedCount of $total selected",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll, enabled = selectedCount < total) { Text("Select all") }
        TextButton(onClick = onClear, enabled = selectedCount > 0) { Text("Clear") }
    }
}

// ───────────────────────── PDF grid ─────────────────────────

@Composable
private fun PageGrid(
    state: RotateUiState,
    onToggle: (Int) -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items((0 until state.pageCount).toList(), key = { it }) { index ->
            RotatePageCard(
                docId = state.docId,
                index = index,
                rotation = state.rotations.getOrElse(index) { 0 },
                selected = index in state.selected,
                onClick = { onToggle(index) },
                loadThumbnail = loadThumbnail
            )
        }
    }
}

@Composable
private fun RotatePageCard(
    docId: Int,
    index: Int,
    rotation: Int,
    selected: Boolean,
    onClick: () -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?
) {
    val thumb by produceState<Bitmap?>(initialValue = null, docId, index) {
        value = loadThumbnail(index)
    }
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
                    val shown = remember(bmp, rotation) { bmp.rotatedCopy(rotation) }
                    Image(
                        bitmap = remember(shown) { shown.asImageBitmap() },
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Page ${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (rotation != 0) {
                    Text(
                        "  ${rotation}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ───────────────────────── Image preview ─────────────────────────

@Composable
private fun ImagePreview(bitmap: Bitmap?, rotation: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                val shown = remember(bitmap, rotation) { bitmap.rotatedCopy(rotation) }
                Image(
                    bitmap = remember(shown) { shown.asImageBitmap() },
                    contentDescription = "Image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ───────────────────────── Bottom bar ─────────────────────────

@Composable
private fun RotateBottomBar(
    state: RotateUiState,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit
) {
    val isPdf = state.kind == RotateKind.PDF
    val target = when {
        !isPdf -> "Rotate image"
        state.selected.isEmpty() -> "Rotate: all pages"
        else -> "Rotate: ${state.selected.size} selected page${if (state.selected.size == 1) "" else "s"}"
    }

    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(target, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RotateButton("90°", Icons.Outlined.RotateRight, Modifier.weight(1f)) { onRotate(90) }
                RotateButton("180°", Icons.Outlined.RotateRight, Modifier.weight(1f)) { onRotate(180) }
                RotateButton("270°", Icons.Outlined.RotateLeft, Modifier.weight(1f)) { onRotate(270) }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = state.isModified && !state.isSaving,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset")
                }
                Button(
                    onClick = onSave,
                    enabled = state.isModified && !state.isSaving,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isPdf) "Save Rotated PDF" else "Save Rotated Image")
                }
            }
        }
    }
}

@Composable
private fun RotateButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label)
    }
}