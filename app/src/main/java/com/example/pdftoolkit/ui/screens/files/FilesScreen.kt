package com.example.pdftoolkit.ui.screens.files

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme

import java.util.Locale

/* ----------------------------- Models ----------------------------- */

private enum class DocKind(@StringRes val badge: Int, val accent: Color) {
    PDF(R.string.pdf_badge, AppColors.Primary),
    SCAN(R.string.scan_badge, AppColors.Teal)
}

private enum class DocTag(
    @StringRes val label: Int,
    val icon: ImageVector,
    val bg: Color,
    val fg: Color
) {
    COMPRESSED(R.string.tag_compressed, Icons.Outlined.Compress, AppColors.TealLight40, AppColors.Teal),
    MERGED(R.string.tag_merged, Icons.Outlined.MergeType, AppColors.PrimaryTint20, AppColors.Primary),
    FROM_IMAGES(R.string.tag_from_images, Icons.Outlined.Image, AppColors.Peach60, AppColors.OnPeach),
    SPLIT(R.string.tag_split_pdf, Icons.Outlined.ContentCut, AppColors.SurfaceSoft, AppColors.TextSecondary)
}

private data class FileItem(
    val id: Int,
    val name: String,
    val size: String,
    val pages: Int,
    val date: String,
    val kind: DocKind,
    val thumbIcon: ImageVector,
    val tag: DocTag? = null,
    val starred: Boolean = false,
    val originalSize: String? = null // size ke saath strikethrough dikhane ke liye
)

private enum class FileFilter(@StringRes val label: Int, val icon: ImageVector, val count: Int?) {
    ALL(R.string.files_filter_all, Icons.Outlined.Check, null),
    PDFS(R.string.files_filter_pdfs, Icons.Outlined.PictureAsPdf, 24),
    IMAGES(R.string.files_filter_images, Icons.Outlined.Image, 12),
    PROCESSED(R.string.files_filter_processed, Icons.Outlined.AutoFixHigh, 18),
    STARRED(R.string.files_filter_starred, Icons.Outlined.StarBorder, null)
}

/* ----------------------------- Sample data ----------------------------- */

private val sampleFiles = listOf(
    FileItem(1, "Annual_Corporate_Audit_2024.pdf", "5.8 MB", 42, "Oct 16", DocKind.PDF, Icons.Outlined.Description, DocTag.COMPRESSED, starred = true),
    FileItem(2, "Vendor_Contract_Merged_v2.pdf", "3.1 MB", 14, "Oct 15", DocKind.PDF, Icons.Outlined.MergeType, DocTag.MERGED),
    FileItem(3, "Medical_Reimbursement_Receipts.pdf", "1.2 MB", 8, "Oct 12", DocKind.SCAN, Icons.Outlined.Image, DocTag.FROM_IMAGES),
    FileItem(4, "Passport_Scan_Extract.pdf", "640 KB", 2, "Oct 10", DocKind.PDF, Icons.Outlined.CallSplit, DocTag.SPLIT),
    FileItem(5, "Client_Presentation_Compressed.pdf", "2.4 MB", 20, "Oct 8", DocKind.PDF, Icons.Outlined.Compress, originalSize = "8.1 MB")
)

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

@Composable
fun FilesScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onImportClick: () -> Unit = {},
    onDocClick: (String) -> Unit = {},
    onDocOptionsClick: (String) -> Unit = {}
) {
    var selectedFilter by remember { mutableStateOf(FileFilter.ALL) }
    var isListView by remember { mutableStateOf(true) } // TODO: grid layout (Figma mein nahi hai)

    val visibleFiles = remember(selectedFilter) {
        when (selectedFilter) {
            FileFilter.ALL -> sampleFiles
            FileFilter.PDFS -> sampleFiles.filter { it.kind == DocKind.PDF }
            FileFilter.IMAGES -> sampleFiles.filter { it.kind == DocKind.SCAN }
            FileFilter.PROCESSED -> sampleFiles.filter { it.tag != null }
            FileFilter.STARRED -> sampleFiles.filter { it.starred }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SearchAndViewBar(
                    isListView = isListView,
                    onViewChange = { isListView = it },
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 6.dp)
                )
            }
            item {
                FilterChips(
                    selected = selectedFilter,
                    onSelected = { selectedFilter = it },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            item {
                StorageBanner(
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 6.dp)
                )
            }
            item {
                SelectSortBar(
                    itemCount = visibleFiles.size,
                    modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 4.dp, bottom = 6.dp)
                )
            }
            items(visibleFiles, key = { it.id }) { file ->
                DocumentCard(
                    file = file,
                    onClick = { onDocClick(file.name) },
                    onOptionsClick = { onDocOptionsClick(file.name) },
                    modifier = Modifier.padding(horizontal = ScreenPadding)
                )
            }
        }

        ImportFab(
            onClick = onImportClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ScreenPadding, bottom = contentPadding.calculateBottomPadding() + 16.dp)
        )
    }
}

/* ----------------------------- Search + view toggle ----------------------------- */

@Composable
private fun SearchAndViewBar(
    isListView: Boolean,
    onViewChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceTint)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Search, null, tint = AppColors.TextHint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.files_search_hint),
                style = AppTextStyle.Body14Regular,
                color = AppColors.TextHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.Mic, stringResource(R.string.cd_voice_input), tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceTint)
                .padding(4.dp)
        ) {
            ViewToggleButton(Icons.Outlined.ViewList, R.string.cd_list_view, isListView) { onViewChange(true) }
            ViewToggleButton(Icons.Outlined.GridView, R.string.cd_grid_view, !isListView) { onViewChange(false) }
        }
    }
}

@Composable
private fun ViewToggleButton(
    icon: ImageVector,
    @StringRes description: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppColors.Surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = if (selected) AppColors.Primary else AppColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/* ----------------------------- Filter chips ----------------------------- */

@Composable
private fun FilterChips(
    selected: FileFilter,
    onSelected: (FileFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(FileFilter.values().toList()) { filter ->
            val isSelected = filter == selected
            val label = if (filter.count != null) {
                stringResource(filter.label, filter.count)
            } else {
                stringResource(filter.label)
            }
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .then(if (isSelected) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (isSelected) AppColors.Primary else AppColors.SurfaceSoft)
                    .clickable { onSelected(filter) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Outlined.Check else filter.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else AppColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = AppTextStyle.Label12Medium,
                    color = if (isSelected) Color.White else AppColors.TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/* ----------------------------- Storage banner ----------------------------- */

@Composable
private fun StorageBanner(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.FolderOpen, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.files_storage),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.width(4.dp))
                Text(text = "›", style = AppTextStyle.Caption11Medium, color = AppColors.TextHint)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.files_storage_path),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.files_storage_free, "42.6 GB"),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.Primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        // Segmented storage bar: used (blue), processed (teal), other (light), free (track)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceSoft)
        ) {
            Box(
                modifier = Modifier
                    .weight(93.52f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(AppColors.Primary)
            )
            Box(modifier = Modifier.weight(48.75f).fillMaxHeight().padding(start = 2.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(AppColors.Teal))
            }
            Box(modifier = Modifier.weight(28.72f).fillMaxHeight().padding(start = 2.dp)) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(AppColors.SurfaceTrack))
            }
            Spacer(Modifier.weight(163f))
        }
    }
}

/* ----------------------------- Select / sort bar ----------------------------- */

@Composable
private fun SelectSortBar(itemCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceSoft)
                    .clickable { /* TODO: selection mode */ }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Checklist, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.files_select),
                    style = AppTextStyle.Label12Medium,
                    color = AppColors.TextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.files_items_displayed, itemCount),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { /* TODO: sort dropdown */ }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.SwapVert, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.files_sort_modified_newest),
                style = AppTextStyle.Label12Medium,
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Document card ----------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentCard(
    file: FileItem,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail + type badge
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(file.thumbIcon, null, tint = file.kind.accent, modifier = Modifier.size(24.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(topStart = 4.dp))
                    .background(file.kind.accent)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(file.kind.badge).uppercase(Locale.getDefault()),
                    style = AppTextStyle.Badge9SemiBold,
                    color = Color.White,
                    maxLines = 1
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = file.name,
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (file.starred) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = stringResource(R.string.cd_starred),
                        tint = AppColors.Star,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (file.originalSize != null) {
                    // Compressed size (teal) + purani size strikethrough
                    Text(
                        text = file.size,
                        style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                        color = AppColors.Teal
                    )
                    Text(
                        text = file.originalSize,
                        style = AppTextStyle.Caption11Regular.copy(textDecoration = TextDecoration.LineThrough),
                        color = AppColors.TextHint
                    )
                } else {
                    MetaText(file.size)
                }
                Bullet()
                MetaText(stringResource(R.string.doc_pages_format, file.pages))
                Bullet()
                file.tag?.let { tag ->
                    TagChip(tag)
                    Bullet()
                }
                MetaText(file.date)
            }
        }

        IconButton(onClick = onOptionsClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.cd_document_options, file.name),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text = text, style = AppTextStyle.Label12Regular, color = AppColors.TextSecondary, maxLines = 1)
}

@Composable
private fun Bullet() {
    MetaText(stringResource(R.string.bullet_separator))
}

@Composable
private fun TagChip(tag: DocTag) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(tag.bg)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(tag.icon, null, tint = tag.fg, modifier = Modifier.size(10.dp))
        Text(
            text = stringResource(tag.label),
            style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
            color = tag.fg,
            maxLines = 1
        )
    }
}

/* ----------------------------- Extended FAB ----------------------------- */

@Composable
private fun ImportFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(56.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Primary)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.files_import),
            style = AppTextStyle.Fab14SemiBold,
            color = Color.White
        )
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun FilesScreenPreview() {
    PdfToolkitTheme { FilesScreen(contentPadding = PaddingValues(0.dp)) }
}
