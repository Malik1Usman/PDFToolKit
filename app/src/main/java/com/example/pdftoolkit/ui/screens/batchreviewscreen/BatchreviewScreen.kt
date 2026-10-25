package com.example.pdftoolkit.ui.screens.batchreviewscreen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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

enum class PageFilterTag(
    @StringRes val label: Int,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color
) {
    MAGIC(R.string.fine_preset_magic, Icons.Outlined.AutoFixHigh, AppColors.PrimaryTint, AppColors.Primary),
    BW(R.string.fine_preset_bw, Icons.Outlined.Contrast, AppColors.SurfaceSoft, AppColors.TextPrimary)
}

data class ScannedPage(
    val id: Int,
    val label: String,
    val filter: PageFilterTag,
    val selected: Boolean = true,
    val rotation: Float = 0f
)

/** Export ke liye final choices; Save PDF dabane par callback mein milti hain. */
data class PdfExportOptions(
    val fileName: String,
    val pageSize: PageSizeOption,
    val quality: QualityOption,
    val searchableOcr: Boolean,
    val pages: List<ScannedPage>
)

enum class PageSizeOption(@StringRes val label: Int) {
    A4(R.string.batch_size_a4),
    LETTER(R.string.batch_size_letter),
    LEGAL(R.string.batch_size_legal),
    ORIGINAL(R.string.batch_size_original)
}

enum class QualityOption(@StringRes val label: Int, val dpi: Int) {
    COMPACT(R.string.batch_quality_compact, 150),
    BALANCED(R.string.batch_quality_balanced, 300),
    MAXIMUM(R.string.batch_quality_max, 600)
}

private val sampleScannedPages = listOf(
    ScannedPage(1, "Cover / Contract", PageFilterTag.MAGIC),
    ScannedPage(2, "Terms & Signatures", PageFilterTag.BW)
)

private val ScreenPadding = 16.dp
private val CardGap = 12.dp

/* ----------------------------- Screen ----------------------------- */

/**
 * "Batch Scanned Pages" review screen. Scanner ke "Done" se khulti hai.
 * Bottom nav bar yahan hide hota hai (MainScreen).
 *
 * [pagePreview] slot: yahan har page ka real scanned bitmap dikhao.
 */
@Composable
fun BatchReviewScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    initialPages: List<ScannedPage> = sampleScannedPages,
    initialFileName: String = "Contract_Scan_2024-11-14.pdf",
    onAddMore: () -> Unit = {},
    onPageClick: (pageId: Int) -> Unit = {},
    onRetake: (pageId: Int) -> Unit = {},
    onAutoCropAll: () -> Unit = {},
    onSavePdf: (PdfExportOptions) -> Unit = {},
    pagePreview: @Composable BoxScope.(ScannedPage) -> Unit = { SamplePagePreview(it) }
) {
    val pages = remember { mutableStateListOf<ScannedPage>().apply { addAll(initialPages) } }
    var fileName by remember { mutableStateOf(initialFileName) }
    var pageSize by remember { mutableStateOf(PageSizeOption.A4) }
    var quality by remember { mutableStateOf(QualityOption.BALANCED) }
    var ocr by remember { mutableStateOf(true) }

    // Drag-to-reorder state
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { CardGap.toPx() }

    val selectedCount = pages.count { it.selected }

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
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            BatchHeader(
                pageCount = pages.size,
                onAdd = onAddMore,
                onClearAll = { pages.clear() }
            )

            BatchInfoCard(
                selectedCount = selectedCount,
                allSelected = pages.isNotEmpty() && selectedCount == pages.size,
                onAutoCropAll = onAutoCropAll,
                onRotateAll = {
                    for (i in pages.indices) pages[i] = pages[i].copy(rotation = (pages[i].rotation + 90f) % 360f)
                },
                onSelectAll = {
                    val target = selectedCount != pages.size
                    for (i in pages.indices) pages[i] = pages[i].copy(selected = target)
                },
                modifier = Modifier.padding(top = 16.dp)
            )

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(CardGap)
            ) {
                if (pages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.batch_empty),
                        style = AppTextStyle.Body14Regular,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }

                pages.forEachIndexed { index, page ->
                    key(page.id) {
                        val isDragging = draggingId == page.id
                        PageCard(
                            page = page,
                            number = index + 1,
                            isDragging = isDragging,
                            dragOffset = { dragOffset },
                            onHeightKnown = { cardHeightPx = it },
                            onDragStart = {
                                draggingId = page.id
                                dragOffset = 0f
                            },
                            onDrag = { dy ->
                                dragOffset += dy
                                val step = cardHeightPx + gapPx
                                val idx = pages.indexOfFirst { it.id == draggingId }
                                if (step > 0f && idx >= 0) {
                                    if (dragOffset > step / 2 && idx < pages.lastIndex) {
                                        Collections.swap(pages, idx, idx + 1)
                                        dragOffset -= step
                                    } else if (dragOffset < -step / 2 && idx > 0) {
                                        Collections.swap(pages, idx, idx - 1)
                                        dragOffset += step
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                            },
                            onToggleSelected = { pages[index] = page.copy(selected = !page.selected) },
                            onRotate = { pages[index] = page.copy(rotation = (page.rotation + 90f) % 360f) },
                            onDelete = { pages.remove(page) },
                            onOpen = { onPageClick(page.id) },
                            onRetake = { onRetake(page.id) },
                            pagePreview = pagePreview
                        )
                    }
                }

                ScanNextTile(nextPage = pages.size + 1, onClick = onAddMore)
            }

            ExportSettingsCard(
                fileName = fileName,
                onFileNameChange = { fileName = it },
                pageSize = pageSize,
                onPageSizeChange = { pageSize = it },
                quality = quality,
                onQualityChange = { quality = it },
                ocr = ocr,
                onOcrChange = { ocr = it },
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        // Primary action bar (pinned)
        ActionBar(
            selectedCount = selectedCount,
            onAddMore = onAddMore,
            onSave = {
                onSavePdf(
                    PdfExportOptions(
                        fileName = fileName,
                        pageSize = pageSize,
                        quality = quality,
                        searchableOcr = ocr,
                        pages = pages.filter { it.selected }
                    )
                )
            },
            modifier = Modifier
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp)
        )
    }
}

/* ----------------------------- Header ----------------------------- */

@Composable
private fun BatchHeader(pageCount: Int, onAdd: () -> Unit, onClearAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.batch_title),
                    style = AppTextStyle.Title18Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.PrimaryButton)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = pageCount.toString(),
                        style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.BadgeCountText
                    )
                }
            }
            Text(
                text = stringResource(R.string.batch_subtitle),
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceSoft)
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AddAPhoto, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.batch_add),
                    style = AppTextStyle.Label12SemiBold,
                    color = AppColors.Primary
                )
            }
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.batch_clear_all),
                    style = AppTextStyle.Label12SemiBold,
                    color = AppColors.Error,
                    maxLines = 1
                )
            }
        }
    }
}

/* ----------------------------- Batch info card ----------------------------- */

@Composable
private fun BatchInfoCard(
    selectedCount: Int,
    allSelected: Boolean,
    onAutoCropAll: () -> Unit,
    onRotateAll: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val estimatedMb = String.format(Locale.getDefault(), "%.1f", selectedCount * 0.7f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.SurfaceTrack),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Layers, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.batch_estimated).uppercase(Locale.getDefault()),
                        style = AppTextStyle.Caption11Medium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.55.sp
                        ),
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.batch_estimate_value, selectedCount, estimatedMb),
                        style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.TealLight)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Bolt, null, tint = AppColors.TealOnLight, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.batch_ready),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TealOnLight
                )
            }
        }

        // Quick action chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(
                listOf(
                    Triple(Icons.Outlined.Crop, R.string.batch_chip_autocrop, onAutoCropAll),
                    Triple(Icons.Outlined.RotateRight, R.string.batch_chip_rotate, onRotateAll),
                    Triple(Icons.Outlined.SelectAll, R.string.batch_chip_select_all, onSelectAll)
                )
            ) { (icon, label, action) ->
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .shadow(1.dp, CircleShape)
                        .clip(CircleShape)
                        .background(if (label == R.string.batch_chip_select_all && allSelected) AppColors.SurfaceSoft else AppColors.Surface)
                        .clickable(onClick = action)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(label),
                        style = AppTextStyle.Label12Medium,
                        color = AppColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ----------------------------- Page card ----------------------------- */

@Composable
private fun PageCard(
    page: ScannedPage,
    number: Int,
    isDragging: Boolean,
    dragOffset: () -> Float,
    onHeightKnown: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleSelected: () -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit,
    onRetake: () -> Unit,
    pagePreview: @Composable BoxScope.(ScannedPage) -> Unit
) {
    Column(
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.DragIndicator,
                    contentDescription = stringResource(R.string.cd_drag_handle),
                    tint = AppColors.TextHint,
                    modifier = Modifier
                        .size(width = 20.dp, height = 32.dp)
                        .pointerInput(page.id) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, amount ->
                                    change.consume()
                                    onDrag(amount.y)
                                }
                            )
                        }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.batch_page_title, number),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "• ${page.label}",
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(page.filter.bg)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(page.filter.icon, null, tint = page.filter.fg, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(page.filter.label),
                    style = AppTextStyle.Caption11Medium,
                    color = page.filter.fg,
                    maxLines = 1
                )
            }
        }

        // Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSofter)
                .clickable(onClick = onOpen)
        ) {
            pagePreview(page)

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.ScanOverlay80)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Straighten, null, tint = AppColors.OverlayChipText, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.batch_page_dim),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.OverlayChipText
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(28.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (page.selected) AppColors.ScanOverlay75 else AppColors.WhiteOverlay80)
                    .toggleable(value = page.selected, role = Role.Checkbox, onValueChange = { onToggleSelected() }),
                contentAlignment = Alignment.Center
            ) {
                if (page.selected) {
                    Icon(
                        Icons.Outlined.Check,
                        contentDescription = stringResource(R.string.cd_page_selected),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // Per-card actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                CardTextAction(Icons.Outlined.Crop, R.string.batch_edge_adjust, onOpen)
                IconButton(onClick = onRotate, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Outlined.RotateRight,
                        contentDescription = stringResource(R.string.cd_rotate_page),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                CardTextAction(Icons.Outlined.CameraAlt, R.string.batch_retake, onRetake)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.cd_delete_page),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
private fun CardTextAction(icon: ImageVector, @StringRes label: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(label),
            style = AppTextStyle.Label12Medium,
            color = AppColors.TextSecondary,
            maxLines = 1
        )
    }
}

/** Placeholder: kagaz ka preview. Real app mein scanned bitmap dikhao (rotation lagao). */
@Composable
private fun BoxScope.SamplePagePreview(page: ScannedPage) {
    Box(modifier = Modifier.matchParentSize().background(Color(0xFFC9CFD6)))
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxHeight(0.92f)
            .aspectRatio(0.72f)
            .rotate(page.rotation)
            .background(Color(0xFFF7F7F5), RoundedCornerShape(2.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(14) { i ->
                Box(
                    Modifier
                        .fillMaxWidth(if (i % 5 == 4) 0.5f else 1f)
                        .height(3.dp)
                        .background(Color(0xFF9AA0AC), RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

/* ----------------------------- Scan next tile ----------------------------- */

@Composable
private fun ScanNextTile(nextPage: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AppColors.PrimaryTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.AddAPhoto, null, tint = AppColors.Primary, modifier = Modifier.size(22.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.batch_scan_next),
                style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                color = AppColors.Primary
            )
            Text(
                text = stringResource(R.string.batch_scan_next_desc, nextPage),
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Export settings ----------------------------- */

@Composable
private fun ExportSettingsCard(
    fileName: String,
    onFileNameChange: (String) -> Unit,
    pageSize: PageSizeOption,
    onPageSizeChange: (PageSizeOption) -> Unit,
    quality: QualityOption,
    onQualityChange: (QualityOption) -> Unit,
    ocr: Boolean,
    onOcrChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Tune, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.batch_export_title),
                style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                color = AppColors.TextPrimary
            )
        }

        // File name
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.batch_filename_label),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceTint)
                    .padding(start = 14.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = fileName,
                    onValueChange = onFileNameChange,
                    singleLine = true,
                    textStyle = AppTextStyle.Body14Regular.copy(color = AppColors.TextPrimary),
                    cursorBrush = SolidColor(AppColors.Primary),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_filename),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Page size + quality
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField(
                label = R.string.batch_page_size,
                valueText = stringResource(pageSize.label),
                options = PageSizeOption.values().map { stringResource(it.label) },
                onSelected = { onPageSizeChange(PageSizeOption.values()[it]) },
                modifier = Modifier.weight(1f)
            )
            DropdownField(
                label = R.string.batch_quality,
                valueText = stringResource(quality.label),
                options = QualityOption.values().map { stringResource(it.label) },
                onSelected = { onQualityChange(QualityOption.values()[it]) },
                modifier = Modifier.weight(1f)
            )
        }

        // OCR toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceTint)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.PrimaryTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.DocumentScanner, null, tint = AppColors.Primary, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.batch_ocr_title),
                        style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                        color = AppColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.batch_ocr_desc),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            OcrSwitch(checked = ocr, onCheckedChange = onOcrChange)
        }
    }
}

@Composable
private fun DropdownField(
    @StringRes label: Int,
    valueText: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(label),
            style = AppTextStyle.Caption11Medium,
            color = AppColors.TextSecondary
        )
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceTint)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueText,
                    style = AppTextStyle.Body14Regular,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Outlined.KeyboardArrowDown, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, text ->
                    DropdownMenuItem(
                        text = { Text(text, style = AppTextStyle.Body14Regular, color = AppColors.TextPrimary) },
                        onClick = {
                            onSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OcrSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) AppColors.Primary else AppColors.SwitchOff)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/* ----------------------------- Action bar ----------------------------- */

@Composable
private fun ActionBar(
    selectedCount: Int,
    onAddMore: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceSoft)
                .clickable(onClick = onAddMore),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = AppColors.Primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.batch_add_more),
                style = AppTextStyle.Button14SemiBold,
                color = AppColors.Primary,
                maxLines = 1
            )
        }

        val canSave = selectedCount > 0
        Row(
            modifier = Modifier
                .weight(2f)
                .height(48.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(if (canSave) AppColors.Primary else AppColors.SwitchOff)
                .then(if (canSave) Modifier.clickable(onClick = onSave) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.batch_save_pdf, selectedCount),
                style = AppTextStyle.Button14SemiBold,
                color = Color.White,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1300)
@Composable
private fun BatchReviewScreenPreview() {
    PdfToolkitTheme { BatchReviewScreen(contentPadding = PaddingValues(0.dp)) }
}