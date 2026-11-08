package com.example.pdftoolkit.delete

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.reorder.SavingDialog
import com.example.pdftoolkit.reorder.SuccessDialog
import com.example.pdftoolkit.reorder.openFile
import com.example.pdftoolkit.reorder.shareFile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletePagesScreen(
    onBack: () -> Unit,
    viewModel: DeletePagesViewModel = viewModel()
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
                title = { Text("Delete PDF Pages") },
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
                DeleteBottomBar(
                    selectedCount = state.selected.size,
                    allSelected = state.allSelected,
                    enabled = state.canDelete,
                    onDelete = viewModel::requestDelete
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

                !state.hasFile -> DeleteEmptyState(onPick = launchPicker)

                else -> Column(Modifier.fillMaxSize()) {
                    FileInfoCard(
                        name = state.fileName.orEmpty(),
                        pages = state.pageCount,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    SelectionHeader(
                        selectedCount = state.selected.size,
                        total = state.pageCount,
                        onSelectAll = viewModel::selectAll,
                        onClear = viewModel::clearSelection
                    )
                    if (state.allSelected) {
                        WarningBanner(
                            "At least one page must remain. Deselect a page to continue.",
                            Modifier.padding(horizontal = 16.dp)
                        )
                    }
                    PageGrid(
                        state = state,
                        onToggle = viewModel::toggleSelect,
                        loadThumbnail = viewModel::thumbnail,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (state.showConfirm) {
        val n = state.selected.size
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text("Delete $n page${if (n == 1) "" else "s"}?") },
            text = {
                Text(
                    "A new PDF with ${state.remaining} remaining page${if (state.remaining == 1) "" else "s"} " +
                        "will be created. Your original file will not be changed."
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text("Cancel") }
            }
        )
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
            displayName = "${result.displayName}\n${result.deleted} deleted • ${result.remaining} pages remaining",
            onOpen = { openFile(context, result.uri) },
            onShare = { shareFile(context, result.uri) },
            onDone = viewModel::dismissResult
        )
    }
}

// ───────────────────────── Empty state / info / header ─────────────────────────

@Composable
private fun DeleteEmptyState(onPick: () -> Unit) {
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
                    Icons.Outlined.Delete,
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
            "Choose a PDF, tap the pages you want to remove, and save a new copy without them.",
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
private fun FileInfoCard(name: String, pages: Int, modifier: Modifier = Modifier) {
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
                    "$pages pages",
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
            if (selectedCount == 0) "Tap pages to select" else "$selectedCount of $total selected",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (selectedCount == 0) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onSelectAll, enabled = selectedCount < total) { Text("Select all") }
        TextButton(onClick = onClear, enabled = selectedCount > 0) { Text("Clear") }
    }
}

@Composable
private fun WarningBanner(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

// ───────────────────────── Grid ─────────────────────────

@Composable
private fun PageGrid(
    state: DeleteUiState,
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
            DeletePageCard(
                docId = state.docId,
                index = index,
                selected = index in state.selected,
                onClick = { onToggle(index) },
                loadThumbnail = loadThumbnail
            )
        }
    }
}

@Composable
private fun DeletePageCard(
    docId: Int,
    index: Int,
    selected: Boolean,
    onClick: () -> Unit,
    loadThumbnail: suspend (Int) -> Bitmap?
) {
    val thumb by produceState<Bitmap?>(initialValue = null, docId, index) {
        value = loadThumbnail(index)
    }
    val shape = RoundedCornerShape(12.dp)
    val error = MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) error else MaterialTheme.colorScheme.outlineVariant
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
                if (selected) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(error.copy(alpha = 0.28f))
                    )
                }
                Icon(
                    imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected for deletion" else "Not selected",
                    tint = if (selected) error else Color.Gray,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(22.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (selected) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Page ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── Bottom bar ─────────────────────────

@Composable
private fun DeleteBottomBar(
    selectedCount: Int,
    allSelected: Boolean,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Button(
            onClick = onDelete,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    selectedCount == 0 -> "Select pages to delete"
                    allSelected -> "Keep at least one page"
                    else -> "Delete $selectedCount page${if (selectedCount == 1) "" else "s"}"
                }
            )
        }
    }
}