package com.example.pdftoolkit.reorder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderPdfScreen(
    onBack: () -> Unit,
    viewModel: ReorderPdfViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                title = { Text("Reorder PDF Pages") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.hasFile) {
                        IconButton(onClick = launchPicker, enabled = !state.isSaving) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose another PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.hasFile) {
                BottomActionBar(
                    canAct = state.isModified && !state.isSaving,
                    onReset = viewModel::resetOrder,
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
                    Text("Opening PDF…", style = MaterialTheme.typography.bodyMedium)
                }

                !state.hasFile -> EmptyState(onPick = launchPicker)

                else -> Column(Modifier.fillMaxSize()) {
                    FileInfoCard(
                        name = state.fileName.orEmpty(),
                        pages = state.pageCount,
                        modified = state.isModified,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    ReorderGrid(
                        docId = state.docId,
                        order = state.order,
                        onMove = viewModel::movePage,
                        loadThumbnail = viewModel::thumbnail,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (state.isSaving) {
        SavingDialog(
            progress = state.progress,
            label = state.progressLabel,
            onCancel = viewModel::cancelSave
        )
    }

    state.result?.let { result ->
        SuccessDialog(
            displayName = result.displayName,
            onOpen = { openFile(context, result.uri) },
            onShare = { shareFile(context, result.uri) },
            onDone = viewModel::dismissResult
        )
    }
}

// ───────────────────────── Empty state / info / bottom bar ─────────────────────────

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
                    Icons.Outlined.PictureAsPdf,
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
            "Choose a PDF, then long-press and drag pages to put them in the order you want.",
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

@Composable
private fun FileInfoCard(name: String, pages: Int, modified: Boolean, modifier: Modifier = Modifier) {
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
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (modified) "$pages pages • order changed" else "$pages pages • long-press a page and drag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Outlined.SwapVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BottomActionBar(canAct: Boolean, onReset: () -> Unit, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onReset, enabled = canAct, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
            Button(
                onClick = onSave,
                enabled = canAct,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Reordered PDF")
            }
        }
    }
}

// ───────────────────────── Drag & drop grid ─────────────────────────

@Composable
private fun ReorderGrid(
    docId: Int,
    order: List<Int>,
    onMove: (from: Int, to: Int) -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val currentOrder by rememberUpdatedState(order)
    val currentOnMove by rememberUpdatedState(onMove)

    var draggingKey by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = draggingKey == null,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        gridState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.contains(start) }
                            ?.let {
                                draggingKey = it.key as Int
                                dragOffset = Offset.Zero
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val key = draggingKey ?: return@detectDragGesturesAfterLongPress
                        dragOffset += amount

                        val items = gridState.layoutInfo.visibleItemsInfo
                        val dragged = items.firstOrNull { it.key == key }
                            ?: return@detectDragGesturesAfterLongPress

                        // Layout not yet updated after the last move → wait for next frame.
                        if (dragged.index == currentOrder.indexOf(key)) {
                            val center = Offset(
                                dragged.offset.x + dragged.size.width / 2f + dragOffset.x,
                                dragged.offset.y + dragged.size.height / 2f + dragOffset.y
                            )
                            val target = items.firstOrNull { it.key != key && it.contains(center) }
                            if (target != null) {
                                currentOnMove(dragged.index, target.index)
                                // keep the card under the finger after its layout slot changed
                                dragOffset += Offset(
                                    (dragged.offset.x - target.offset.x).toFloat(),
                                    (dragged.offset.y - target.offset.y).toFloat()
                                )
                            }
                        }

                        // Auto-scroll near top / bottom edge
                        val viewportH = gridState.layoutInfo.viewportSize.height
                        val edge = 140f
                        val y = change.position.y
                        val delta = when {
                            y < edge -> -(edge - y) / edge * 28f
                            y > viewportH - edge -> (y - (viewportH - edge)) / edge * 28f
                            else -> 0f
                        }
                        if (delta != 0f) {
                            scope.launch {
                                val consumed = gridState.scrollBy(delta)
                                dragOffset += Offset(0f, consumed)
                            }
                        }
                    },
                    onDragEnd = { draggingKey = null; dragOffset = Offset.Zero },
                    onDragCancel = { draggingKey = null; dragOffset = Offset.Zero }
                )
            }
    ) {
        itemsIndexed(order, key = { _, originalIndex -> originalIndex }) { position, originalIndex ->
            val isDragging = originalIndex == draggingKey
            PageCard(
                docId = docId,
                originalIndex = originalIndex,
                position = position,
                isDragging = isDragging,
                loadThumbnail = loadThumbnail,
                modifier = if (isDragging) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            scaleX = 1.06f
                            scaleY = 1.06f
                        }
                } else {
                    Modifier.animateItem()   // Compose Foundation 1.7+. On older versions use animateItemPlacement()
                }
            )
        }
    }
}

private fun LazyGridItemInfo.contains(p: Offset): Boolean =
    p.x >= offset.x && p.x <= offset.x + size.width &&
            p.y >= offset.y && p.y <= offset.y + size.height

@Composable
private fun PageCard(
    docId: Int,
    originalIndex: Int,
    position: Int,
    isDragging: Boolean,
    loadThumbnail: suspend (Int) -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val thumb by produceState<Bitmap?>(initialValue = null, docId, originalIndex) {
        value = loadThumbnail(originalIndex)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (isDragging) 2.dp else 1.dp,
            if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 10.dp else 1.dp),
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
                        contentDescription = "Page ${originalIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Page ${position + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (originalIndex != position) {
                    Text(
                        "  (was ${originalIndex + 1})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ───────────────────────── Dialogs ─────────────────────────

@Composable
internal fun SavingDialog(
    progress: Float,
    label: String,
    onCancel: () -> Unit,
    title: String = "Creating PDF"
) {
    Dialog(onDismissRequest = {}) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(50))
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall)
                    Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
            }
        }
    }
}

@Composable
internal fun SuccessDialog(
    displayName: String,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit,
    title: String = "PDF saved"
) {
    Dialog(onDismissRequest = onDone) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Open")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

// ───────────────────────── Intents ─────────────────────────

internal fun openFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
    }
}

internal fun shareFile(context: Context, uri: Uri, mimeType: String = "application/pdf") {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}