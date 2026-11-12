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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme
import java.util.Locale

/* ----------------------------- Models & Enums ----------------------------- */

enum class DocKind(@StringRes val badge: Int, val accent: Color) {
    PDF(R.string.pdf_badge, AppColors.Primary),
    SCAN(R.string.scan_badge, AppColors.Teal)
}

enum class DocTag(
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

enum class FileFilter(@StringRes val label: Int, val icon: ImageVector) {
    ALL(R.string.files_filter_all, Icons.Outlined.Check),
    PDFS(R.string.files_filter_pdfs, Icons.Outlined.PictureAsPdf),
    IMAGES(R.string.files_filter_images, Icons.Outlined.Image),
    PROCESSED(R.string.files_filter_processed, Icons.Outlined.AutoFixHigh),
    STARRED(R.string.files_filter_starred, Icons.Outlined.StarBorder)
}

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

@Composable
fun FilesScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: FilesViewModel = viewModel(),
    onImportClick: () -> Unit = {},
    onDocClick: (String) -> Unit = {},
    onDocOptionsClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        isListView = uiState.isListView,
                        onViewChange = { viewModel.toggleViewMode(it) },
                        modifier = Modifier
                            .padding(horizontal = ScreenPadding)
                            .padding(bottom = 6.dp)
                    )
                }
                item {
                    FilterChips(
                        selected = uiState.selectedFilter,
                        uiState = uiState,
                        onSelected = { viewModel.onFilterSelected(it) },
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                item {
                    StorageBanner(
                        storagePath = uiState.storagePath,
                        freeStorage = uiState.freeStorageFormatted,
                        usedMb = uiState.totalOutputsSizeMb,
                        modifier = Modifier
                            .padding(horizontal = ScreenPadding)
                            .padding(bottom = 6.dp)
                    )
                }
                item {
                    SelectSortBar(
                        itemCount = uiState.filteredFiles.size,
                        currentSort = uiState.selectedSort,
                        isSelectionMode = uiState.isSelectionMode,
                        onToggleSelection = { viewModel.toggleSelectionMode() },
                        onSortSelected = { viewModel.onSortSelected(it) },
                        modifier = Modifier
                            .padding(horizontal = ScreenPadding)
                            .padding(top = 4.dp, bottom = 6.dp)
                    )
                }

                if (uiState.filteredFiles.isEmpty()) {
                    item {
                        EmptyFilesCard(
                            onImportClick = onImportClick,
                            modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 24.dp)
                        )
                    }
                } else if (uiState.isListView) {
                    items(uiState.filteredFiles, key = { it.id }) { file ->
                        DocumentCard(
                            file = file,
                            isSelectionMode = uiState.isSelectionMode,
                            isSelected = uiState.selectedIds.contains(file.id),
                            onSelectToggle = { viewModel.toggleFileSelection(file.id) },
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleFileSelection(file.id)
                                } else {
                                    onDocClick(file.path)
                                }
                            },
                            onShare = { viewModel.shareFile(context, file) },
                            onToggleStar = { viewModel.toggleStar(file.id) },
                            onDelete = { viewModel.deleteFile(file) },
                            modifier = Modifier.padding(horizontal = ScreenPadding)
                        )
                    }
                } else {
                    // Grid View: Render pairs of items in rows
                    val chunks = uiState.filteredFiles.chunked(2)
                    items(chunks, key = { chunk -> chunk.first().id }) { chunk ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ScreenPadding),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            chunk.forEach { file ->
                                DocumentGridCard(
                                    file = file,
                                    onClick = { onDocClick(file.path) },
                                    onShare = { viewModel.shareFile(context, file) },
                                    onToggleStar = { viewModel.toggleStar(file.id) },
                                    onDelete = { viewModel.deleteFile(file) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (chunk.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
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
    query: String,
    onQueryChange: (String) -> Unit,
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
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.files_search_hint),
                        style = AppTextStyle.Body14Regular,
                        color = AppColors.TextHint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = AppTextStyle.Body14Regular.copy(color = AppColors.TextPrimary),
                    cursorBrush = SolidColor(AppColors.Primary),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
    uiState: FilesUiState,
    onSelected: (FileFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(FileFilter.entries) { filter ->
            val isSelected = filter == selected
            val count = when (filter) {
                FileFilter.ALL -> null
                FileFilter.PDFS -> uiState.pdfsCount
                FileFilter.IMAGES -> uiState.scansCount
                FileFilter.PROCESSED -> uiState.processedCount
                FileFilter.STARRED -> uiState.starredCount
            }
            val label = if (count != null && count > 0) {
                "${stringResource(filter.label)} ($count)"
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
private fun StorageBanner(
    storagePath: String,
    freeStorage: String,
    usedMb: Float,
    modifier: Modifier = Modifier
) {
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
                    text = storagePath,
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(R.string.files_storage_free, freeStorage),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.Primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceSoft)
        ) {
            val usedWeight = usedMb.coerceAtLeast(10f)
            Box(
                modifier = Modifier
                    .weight(usedWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(AppColors.Primary)
            )
            Spacer(Modifier.weight(200f))
        }
    }
}

/* ----------------------------- Select / sort bar ----------------------------- */

@Composable
private fun SelectSortBar(
    itemCount: Int,
    currentSort: FileSort,
    isSelectionMode: Boolean,
    onToggleSelection: () -> Unit,
    onSortSelected: (FileSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelectionMode) AppColors.PrimaryTint else AppColors.SurfaceSoft)
                    .clickable(onClick = onToggleSelection)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Checklist,
                    null,
                    tint = if (isSelectionMode) AppColors.Primary else AppColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isSelectionMode) "Cancel Select" else stringResource(R.string.files_select),
                    style = AppTextStyle.Label12Medium,
                    color = if (isSelectionMode) AppColors.Primary else AppColors.TextPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.files_items_displayed, itemCount),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showSortMenu = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.SwapVert, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                val sortLabel = when (currentSort) {
                    FileSort.MODIFIED_NEWEST -> "Newest"
                    FileSort.MODIFIED_OLDEST -> "Oldest"
                    FileSort.NAME_AZ -> "Name (A-Z)"
                    FileSort.NAME_ZA -> "Name (Z-A)"
                    FileSort.SIZE_LARGEST -> "Size (Largest)"
                    FileSort.SIZE_SMALLEST -> "Size (Smallest)"
                }
                Text(
                    text = sortLabel,
                    style = AppTextStyle.Label12Medium,
                    color = AppColors.TextSecondary
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Newest First") },
                    onClick = { onSortSelected(FileSort.MODIFIED_NEWEST); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Oldest First") },
                    onClick = { onSortSelected(FileSort.MODIFIED_OLDEST); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Name (A to Z)") },
                    onClick = { onSortSelected(FileSort.NAME_AZ); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Name (Z to A)") },
                    onClick = { onSortSelected(FileSort.NAME_ZA); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Size (Largest)") },
                    onClick = { onSortSelected(FileSort.SIZE_LARGEST); showSortMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Size (Smallest)") },
                    onClick = { onSortSelected(FileSort.SIZE_SMALLEST); showSortMenu = false }
                )
            }
        }
    }
}

/* ----------------------------- Empty state card ----------------------------- */

@Composable
private fun EmptyFilesCard(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AppColors.PrimaryTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.FolderOpen, null, tint = AppColors.Primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No Documents Found",
            style = AppTextStyle.Title16Medium,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Processed and imported documents will appear here.",
            style = AppTextStyle.Label12Regular,
            color = AppColors.TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(AppColors.Primary)
                .clickable(onClick = onImportClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import File", style = AppTextStyle.Button14SemiBold, color = Color.White)
        }
    }
}

/* ----------------------------- Document card (List Mode) ----------------------------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentCard(
    file: AppFileItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val docKind = if (file.isScan) DocKind.SCAN else DocKind.PDF
    val thumbIcon = if (file.isScan) Icons.Outlined.Image else Icons.Outlined.Description
    val tagEnum = mapStringToTag(file.tag)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) AppColors.PrimaryTint20 else AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(thumbIcon, null, tint = docKind.accent, modifier = Modifier.size(24.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(RoundedCornerShape(topStart = 4.dp))
                    .background(docKind.accent)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(docKind.badge).uppercase(Locale.getDefault()),
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
                if (file.isStarred) {
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
                MetaText(file.size)
                Bullet()
                MetaText(stringResource(R.string.doc_pages_format, file.pages))
                Bullet()
                tagEnum?.let { tag ->
                    TagChip(tag)
                    Bullet()
                }
                MetaText(file.date)
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.cd_document_options, file.name),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open Document") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onClick() }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Outlined.Share, null, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text(if (file.isStarred) "Remove from Starred" else "Add to Starred") },
                    leadingIcon = {
                        Icon(
                            if (file.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            null,
                            tint = if (file.isStarred) AppColors.Star else AppColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = { showMenu = false; onToggleStar() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = AppColors.Error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = AppColors.Error, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

/* ----------------------------- Document card (Grid Mode) ----------------------------- */

@Composable
private fun DocumentGridCard(
    file: AppFileItem,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onToggleStar: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val docKind = if (file.isScan) DocKind.SCAN else DocKind.PDF
    val thumbIcon = if (file.isScan) Icons.Outlined.Image else Icons.Outlined.Description

    Column(
        modifier = modifier
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(thumbIcon, null, tint = docKind.accent, modifier = Modifier.size(20.dp))
            }

            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = null,
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open Document") },
                        onClick = { showMenu = false; onClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = { showMenu = false; onShare() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (file.isStarred) "Unstar" else "Star") },
                        onClick = { showMenu = false; onToggleStar() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = AppColors.Error) },
                        onClick = { showMenu = false; onDelete() }
                    )
                }
            }
        }

        Text(
            text = file.name,
            style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetaText(file.size)
            MetaText(file.date)
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

private fun mapStringToTag(tagString: String?): DocTag? {
    return when (tagString) {
        "COMPRESSED" -> DocTag.COMPRESSED
        "MERGED" -> DocTag.MERGED
        "FROM_IMAGES" -> DocTag.FROM_IMAGES
        "SPLIT" -> DocTag.SPLIT
        else -> null
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
fun FilesScreenPreview() {
    PdfToolkitTheme { FilesScreen(contentPadding = PaddingValues(0.dp)) }
}
