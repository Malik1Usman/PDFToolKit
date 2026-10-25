package com.example.pdftoolkit.ui.screens.mergepdfscreen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme
import java.util.Collections
import java.util.Locale

/* ----------------------------- Models ----------------------------- */

enum class MergeDocTag(@StringRes val label: Int, val icon: ImageVector, val color: Color) {
    PDF(R.string.merge_tag_pdf, Icons.Outlined.FileCopy, AppColors.Primary),
    SIGN(R.string.merge_tag_sign, Icons.Outlined.Verified, AppColors.Teal)
}

data class MergeFile(
    val id: Int,
    val name: String,
    val sizeMb: Float,
    val totalPages: Int,
    val pageRange: IntRange? = null,       // null = saare pages
    val tag: MergeDocTag = MergeDocTag.PDF,
    val modifiedMillis: Long = 0L
) {
    val usedPages: Int get() = pageRange?.let { it.last - it.first + 1 } ?: totalPages
}

/** Merge / Preview dabane par ye options milte hain. */
data class MergeOptions(
    val outputName: String,                 // bina ".pdf" ke
    val files: List<MergeFile>,             // merge order mein
    val removeBlankPages: Boolean,
    val normalizeToA4: Boolean,
    val generateBookmarks: Boolean
)

private val sampleMergeFiles = listOf(
    MergeFile(1, "Annual_Financial_Report_2024.pdf", 18.2f, 28, null, MergeDocTag.PDF, 3L),
    MergeFile(2, "Executive_Summary_Signed.pdf", 2.1f, 12, 1..4, MergeDocTag.SIGN, 2L),
    MergeFile(3, "Appendix_Disclosures_Q3.pdf", 4.2f, 20, 1..14, MergeDocTag.PDF, 1L)
)

private val ScreenPadding = 16.dp
private val ItemGap = 10.dp

/* ----------------------------- Screen ----------------------------- */

/**
 * Merge PDF workflow (Step 1: Arrange & Configure).
 * Bottom nav bar yahan hide hota hai (MainScreen / routesWithoutBottomBar).
 */
@Composable
fun MergePdfScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    initialFiles: List<MergeFile> = sampleMergeFiles,
    initialOutputName: String = "Merged_Financial_Report_2024",
    onBack: () -> Unit = {},
    onAddFromDevice: () -> Unit = {},
    onAddFromDrive: () -> Unit = {},
    onPreview: (MergeOptions) -> Unit = {},
    onMerge: (MergeOptions) -> Unit = {}
) {
    val files = remember { mutableStateListOf<MergeFile>().apply { addAll(initialFiles) } }
    var outputName by remember { mutableStateOf(initialOutputName) }
    var removeBlank by remember { mutableStateOf(true) }
    var normalizeA4 by remember { mutableStateOf(false) }
    var bookmarks by remember { mutableStateOf(true) }
    var editingFile by remember { mutableStateOf<MergeFile?>(null) }

    // Drag-to-reorder
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { ItemGap.toPx() }

    val totalPages = files.sumOf { it.usedPages }
    val totalMb = files.sumOf { it.sizeMb.toDouble() }.toFloat()
    val canMerge = files.size >= 2

    fun currentOptions() = MergeOptions(
        outputName = outputName.trim(),
        files = files.toList(),
        removeBlankPages = removeBlank,
        normalizeToA4 = normalizeA4,
        generateBookmarks = bookmarks
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(top = contentPadding.calculateTopPadding())
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 12.dp, bottom = 16.dp)
        ) {
            StepHeader(ready = canMerge, onBack = onBack, onAddFiles = onAddFromDevice)

            SummaryBanner(
                fileCount = files.size,
                totalPages = totalPages,
                totalMb = totalMb,
                onSortAz = {
                    val sorted = files.sortedBy { it.name.lowercase(Locale.getDefault()) }
                    files.clear(); files.addAll(sorted)
                },
                onSortDate = {
                    val sorted = files.sortedByDescending { it.modifiedMillis }
                    files.clear(); files.addAll(sorted)
                },
                onClearAll = { files.clear() },
                modifier = Modifier.padding(top = 16.dp)
            )

            // Section header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.merge_order_header).uppercase(Locale.getDefault()),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.7.sp),
                    color = AppColors.TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.TouchApp, null, tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.merge_hold_drag),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                }
            }

            // Sequence stack
            Column(verticalArrangement = Arrangement.spacedBy(ItemGap)) {
                files.forEachIndexed { index, file ->
                    key(file.id) {
                        val isDragging = draggingId == file.id
                        FileCard(
                            file = file,
                            order = index + 1,
                            isDragging = isDragging,
                            dragOffset = { dragOffset },
                            onHeightKnown = { itemHeightPx = it },
                            onDragStart = {
                                draggingId = file.id
                                dragOffset = 0f
                            },
                            onDrag = { dy ->
                                dragOffset += dy
                                val step = itemHeightPx + gapPx
                                val idx = files.indexOfFirst { it.id == draggingId }
                                if (step > 0f && idx >= 0) {
                                    if (dragOffset > step / 2 && idx < files.lastIndex) {
                                        Collections.swap(files, idx, idx + 1)
                                        dragOffset -= step
                                    } else if (dragOffset < -step / 2 && idx > 0) {
                                        Collections.swap(files, idx, idx - 1)
                                        dragOffset += step
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                            },
                            onSelectPages = { editingFile = file },
                            onRemove = { files.remove(file) }
                        )
                    }
                }
            }

            AddMoreSurface(
                onAddFromDevice = onAddFromDevice,
                onAddFromDrive = onAddFromDrive,
                modifier = Modifier.padding(top = 16.dp)
            )

            MergeOptionsCard(
                outputName = outputName,
                onOutputNameChange = { outputName = it },
                removeBlank = removeBlank,
                onRemoveBlankChange = { removeBlank = it },
                normalizeA4 = normalizeA4,
                onNormalizeChange = { normalizeA4 = it },
                bookmarks = bookmarks,
                onBookmarksChange = { bookmarks = it },
                modifier = Modifier.padding(top = 20.dp)
            )
        }

        PersistentActionBar(
            fileCount = files.size,
            totalPages = totalPages,
            canMerge = canMerge,
            onPreview = { onPreview(currentOptions()) },
            onMerge = { onMerge(currentOptions()) },
            modifier = Modifier
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp)
        )
    }

    editingFile?.let { file ->
        PageRangeDialog(
            file = file,
            onDismiss = { editingFile = null },
            onApply = { range ->
                val idx = files.indexOfFirst { it.id == file.id }
                if (idx >= 0) files[idx] = files[idx].copy(pageRange = range)
                editingFile = null
            }
        )
    }
}

/* ----------------------------- Step header ----------------------------- */

@Composable
private fun StepHeader(ready: Boolean, onBack: () -> Unit, onAddFiles: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).offset(x = (-8).dp)) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.offset(x = (-8).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.merge_title),
                        style = AppTextStyle.Title18Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.TextPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (ready) AppColors.TealLight else AppColors.PrimaryTint)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(if (ready) R.string.merge_ready else R.string.merge_not_ready),
                            style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (ready) AppColors.TealOnLight else AppColors.Primary
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.merge_step),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onAddFiles, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Outlined.AddBox,
                contentDescription = stringResource(R.string.cd_add_files),
                tint = AppColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ----------------------------- Summary banner ----------------------------- */

@Composable
private fun SummaryBanner(
    fileCount: Int,
    totalPages: Int,
    totalMb: Float,
    onSortAz: () -> Unit,
    onSortDate: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemsText = LocalContext.current.resources.getQuantityString(R.plurals.merge_items, fileCount, fileCount)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Metric(Icons.Outlined.Description, R.string.merge_stat_files, itemsText, Modifier.weight(1f))
            Metric(
                Icons.Outlined.AutoStories,
                R.string.merge_stat_pages,
                stringResource(R.string.merge_pages_total, totalPages),
                Modifier.weight(1f)
            )
            Metric(
                Icons.Outlined.DonutLarge,
                R.string.merge_stat_output,
                stringResource(R.string.merge_output_size, String.format(Locale.getDefault(), "%.1f", totalMb)),
                Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RibbonChip(Icons.Outlined.SortByAlpha, R.string.merge_sort_az, onSortAz)
                RibbonChip(Icons.Outlined.CalendarToday, R.string.merge_sort_date, onSortDate)
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.DeleteSweep, null, tint = AppColors.Error, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.merge_clear_all),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Error
                )
            }
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, @StringRes label: Int, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = AppColors.Primary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(label),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }
        Text(
            text = value,
            style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.TextPrimary,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun RibbonChip(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(AppColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(label),
            style = AppTextStyle.Caption11Medium,
            color = AppColors.TextPrimary
        )
    }
}

/* ----------------------------- File card ----------------------------- */

@Composable
private fun FileCard(
    file: MergeFile,
    order: Int,
    isDragging: Boolean,
    dragOffset: () -> Float,
    onHeightKnown: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSelectPages: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeightKnown(it.height.toFloat()) }
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffset() else 0f
                val scale = if (isDragging) 1.02f else 1f
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (isDragging) 8.dp else 1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(width = 20.dp, height = 40.dp)
                .pointerInput(file.id) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.DragIndicator,
                contentDescription = stringResource(R.string.cd_reorder_file, file.name),
                tint = AppColors.TextHint,
                modifier = Modifier.size(20.dp)
            )
        }

        // Document badge + type tag (top-right pe bahar nikalta hai)
        Box(modifier = Modifier.size(width = 44.dp, height = 48.dp)) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(file.tag.icon, null, tint = file.tag.color, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(file.tag.color)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(file.tag.label),
                    style = AppTextStyle.Badge9SemiBold.copy(letterSpacing = (-0.225).sp),
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.PrimaryTint)
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = order.toString(),
                        style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.Primary
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = file.name,
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.merge_file_size, String.format(Locale.getDefault(), "%.1f", file.sizeMb)),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.bullet_separator),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
                val range = file.pageRange
                Text(
                    text = if (range == null) {
                        stringResource(R.string.merge_all_pages, file.totalPages)
                    } else {
                        stringResource(R.string.merge_page_range, range.first, range.last, file.usedPages)
                    },
                    style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                    color = if (range == null) AppColors.Primary else AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSelectPages),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ViewColumn, null, tint = AppColors.Primary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(R.string.merge_select_pages),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Primary
                )
            }
        }

        IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = stringResource(R.string.cd_remove_file, file.name),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/* ----------------------------- Add more surface ----------------------------- */

@Composable
private fun AddMoreSurface(
    onAddFromDevice: () -> Unit,
    onAddFromDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAddFromDevice),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 40.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Add, null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(R.string.merge_add_docs_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.merge_add_docs_desc),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceButton(Icons.Outlined.PhoneAndroid, R.string.merge_source_device, onAddFromDevice)
            SourceButton(Icons.Outlined.CloudQueue, R.string.merge_source_drive, onAddFromDrive)
        }
    }
}

@Composable
private fun SourceButton(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .shadow(1.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextPrimary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(label),
            style = AppTextStyle.Caption11Medium,
            color = AppColors.TextPrimary,
            maxLines = 1
        )
    }
}

/* ----------------------------- Merge options ----------------------------- */

@Composable
private fun MergeOptionsCard(
    outputName: String,
    onOutputNameChange: (String) -> Unit,
    removeBlank: Boolean,
    onRemoveBlankChange: (Boolean) -> Unit,
    normalizeA4: Boolean,
    onNormalizeChange: (Boolean) -> Unit,
    bookmarks: Boolean,
    onBookmarksChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.merge_options_title),
                style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.TextPrimary
            )
            Icon(Icons.Outlined.Tune, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
        }

        // Output file name
        Column(
            modifier = Modifier.padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.merge_output_name_label),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.SurfaceTint)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.DriveFileRenameOutline,
                    null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = outputName,
                    onValueChange = onOutputNameChange,
                    singleLine = true,
                    textStyle = AppTextStyle.Body14Regular.copy(color = AppColors.TextPrimary),
                    cursorBrush = SolidColor(AppColors.Primary),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.merge_pdf_ext),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextHint,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        // Switch rows
        Column(modifier = Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            SwitchRow(
                icon = Icons.Outlined.FindInPage,
                title = R.string.merge_remove_blank,
                description = R.string.merge_remove_blank_desc,
                checked = removeBlank,
                onCheckedChange = onRemoveBlankChange
            )
            SwitchRow(
                icon = Icons.Outlined.AspectRatio,
                title = R.string.merge_normalize,
                description = R.string.merge_normalize_desc,
                checked = normalizeA4,
                onCheckedChange = onNormalizeChange
            )
            SwitchRow(
                icon = Icons.Outlined.Bookmarks,
                title = R.string.merge_bookmarks,
                description = R.string.merge_bookmarks_desc,
                checked = bookmarks,
                onCheckedChange = onBookmarksChange
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(title),
                    style = AppTextStyle.Body14Medium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(description),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        MergeSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MergeSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) AppColors.Primary else AppColors.SurfaceSofter)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/* ----------------------------- Persistent bottom action ----------------------------- */

@Composable
private fun PersistentActionBar(
    fileCount: Int,
    totalPages: Int,
    canMerge: Boolean,
    onPreview: () -> Unit,
    onMerge: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.BottomBarBg)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceSoft)
                .clickable(enabled = fileCount > 0, onClick = onPreview)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(R.string.merge_preview),
                style = AppTextStyle.Button14SemiBold,
                color = AppColors.TextPrimary
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(if (canMerge) AppColors.PrimaryButton else AppColors.SwitchOff)
                .then(if (canMerge) Modifier.clickable(onClick = onMerge) else Modifier)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (canMerge) {
                    stringResource(R.string.merge_action, fileCount, totalPages)
                } else {
                    stringResource(R.string.merge_need_two)
                },
                style = AppTextStyle.Button14SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (canMerge) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/* ----------------------------- Page range dialog ----------------------------- */

@Composable
private fun PageRangeDialog(
    file: MergeFile,
    onDismiss: () -> Unit,
    onApply: (IntRange?) -> Unit
) {
    var from by remember { mutableStateOf((file.pageRange?.first ?: 1).toString()) }
    var to by remember { mutableStateOf((file.pageRange?.last ?: file.totalPages).toString()) }
    val f = from.toIntOrNull()
    val t = to.toIntOrNull()
    val valid = f != null && t != null && f >= 1 && t <= file.totalPages && f <= t

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                text = stringResource(R.string.merge_dialog_title),
                style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.merge_dialog_hint, file.name, file.totalPages),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.merge_dialog_from)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.merge_dialog_to)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!valid) {
                    Text(
                        text = stringResource(R.string.merge_dialog_error, file.totalPages),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.Error
                    )
                }
                TextButton(onClick = { onApply(null) }) {
                    Text(stringResource(R.string.merge_dialog_all), color = AppColors.Primary)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val from1 = f!!
                    val to1 = t!!
                    onApply(if (from1 == 1 && to1 == file.totalPages) null else from1..to1)
                }
            ) {
                Text(stringResource(R.string.merge_dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.merge_dialog_cancel), color = AppColors.TextSecondary)
            }
        }
    )
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun MergePdfScreenPreview() {
    PdfToolkitTheme { MergePdfScreen(contentPadding = PaddingValues(0.dp)) }
}