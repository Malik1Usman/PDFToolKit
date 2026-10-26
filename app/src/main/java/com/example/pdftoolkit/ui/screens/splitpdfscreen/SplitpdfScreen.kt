package com.example.pdftoolkit.ui.screens.splitpdfscreen


import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material.icons.outlined.Visibility
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

enum class SplitMode(@StringRes val label: Int, val icon: ImageVector) {
    EXTRACT(R.string.split_mode_extract, Icons.Outlined.CallSplit),
    RANGE(R.string.split_mode_range, Icons.Outlined.ContentCut),
    EVERY(R.string.split_mode_every, Icons.Outlined.Layers)
}

/** Split / Preview dabane par ye options milte hain. */
data class SplitOptions(
    val mode: SplitMode,
    val selectedPages: List<Int>,        // sorted, 1-based
    val ranges: List<IntRange>,          // selectedPages ke consecutive groups
    val mergeIntoOnePdf: Boolean,
    val saveAsZip: Boolean,
    val preserveBookmarks: Boolean,
    val outputName: String
)

private enum class QuickFilter(@StringRes val label: Int) {
    SELECT_ALL(R.string.split_chip_select_all),
    DESELECT(R.string.split_chip_deselect),
    EVEN(R.string.split_chip_even),
    ODD(R.string.split_chip_odd),
    FIRST_FIVE(R.string.split_chip_first5)
}

private const val COLLAPSED_PAGE_COUNT = 8
private val ScreenPadding = 16.dp

/* ----------------------------- Page range helpers ----------------------------- */

/** "1-4, 8, 12-16" -> {1,2,3,4,8,12..16}. Galat expression pe null. */
private fun parsePageSet(text: String, max: Int): Set<Int>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return emptySet()
    val result = sortedSetOf<Int>()
    for (part in trimmed.split(",")) {
        val p = part.trim()
        if (p.isEmpty()) continue
        val bounds = p.split("-").map { it.trim() }
        when (bounds.size) {
            1 -> {
                val n = bounds[0].toIntOrNull() ?: return null
                if (n !in 1..max) return null
                result.add(n)
            }
            2 -> {
                val a = bounds[0].toIntOrNull() ?: return null
                val b = bounds[1].toIntOrNull() ?: return null
                if (a < 1 || b > max || a > b) return null
                for (i in a..b) result.add(i)
            }
            else -> return null
        }
    }
    return result
}

private fun toRanges(pages: Set<Int>): List<IntRange> {
    val sorted = pages.sorted()
    if (sorted.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var prev = start
    for (n in sorted.drop(1)) {
        if (n == prev + 1) {
            prev = n
        } else {
            ranges.add(start..prev)
            start = n
            prev = n
        }
    }
    ranges.add(start..prev)
    return ranges
}

/** {1,2,3,4,8,12..16} -> "1-4, 8, 12-16" */
private fun formatPageSet(pages: Set<Int>): String =
    toRanges(pages).joinToString(", ") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" }

/* ----------------------------- Screen ----------------------------- */

/**
 * Split PDF workflow (Step 2: Configure slices).
 * Bottom nav bar yahan hide hota hai (routesWithoutBottomBar).
 *
 * [pageThumbnail] slot: yahan har page ka real thumbnail (bitmap) dikhao.
 */
@Composable
fun SplitPdfScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    sourceName: String = "Comprehensive_Quarterly_Report_Q3.pdf",
    sourceSizeMb: Float = 18.6f,
    pageCount: Int = 32,
    initialOutputName: String = "Quarterly_Report_Extract_Pages.pdf",
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
    onChangeFile: () -> Unit = {},
    onPreview: (SplitOptions) -> Unit = {},
    onSplit: (SplitOptions) -> Unit = {},
    pageThumbnail: @Composable BoxScope.(page: Int, selected: Boolean) -> Unit = { page, selected ->
        SkeletonThumbnail(page, selected)
    }
) {
    val allPages = remember(pageCount) { (1..pageCount).toSet() }
    val initialSelection = remember(pageCount) {
        (setOf(1, 2, 3, 4, 8) + (12..16).toSet()).filter { it <= pageCount }.toSet()
    }

    var mode by remember { mutableStateOf(SplitMode.EXTRACT) }
    var selected by remember { mutableStateOf(initialSelection) }
    var expression by remember { mutableStateOf(formatPageSet(initialSelection)) }
    var expressionError by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(true) }
    var showAllPages by remember { mutableStateOf(false) }
    var outputName by remember { mutableStateOf(initialOutputName) }
    var mergeIntoOne by remember { mutableStateOf(true) }
    var saveAsZip by remember { mutableStateOf(false) }
    var preserveBookmarks by remember { mutableStateOf(true) }

    fun applySelection(newSet: Set<Int>) {
        selected = newSet
        expression = formatPageSet(newSet)
        expressionError = false
    }

    val canSplit = selected.isNotEmpty() && !expressionError

    fun options() = SplitOptions(
        mode = mode,
        selectedPages = selected.sorted(),
        ranges = toRanges(selected),
        mergeIntoOnePdf = mergeIntoOne,
        saveAsZip = saveAsZip,
        preserveBookmarks = preserveBookmarks,
        outputName = outputName.trim()
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 32.dp)
    ) {
        Header(onBack = onBack, onHelp = onHelp)

        SourceCard(
            name = sourceName,
            sizeMb = sourceSizeMb,
            pageCount = pageCount,
            onChange = onChangeFile,
            modifier = Modifier.padding(top = 16.dp)
        )

        SplitMethodSelector(
            selected = mode,
            onSelected = {
                mode = it
                if (it == SplitMode.EVERY) applySelection(allPages)
            },
            modifier = Modifier.padding(top = 16.dp)
        )

        RangeCard(
            expression = expression,
            hasError = expressionError,
            readOnly = mode == SplitMode.EVERY,
            selectedCount = selected.size,
            pageCount = pageCount,
            selected = selected,
            allPages = allPages,
            onExpressionChange = { text ->
                expression = text
                val parsed = parsePageSet(text, pageCount)
                if (parsed == null) {
                    expressionError = true
                } else {
                    expressionError = false
                    selected = parsed
                }
            },
            onClear = { applySelection(emptySet()) },
            onQuickFilter = { filter ->
                applySelection(
                    when (filter) {
                        QuickFilter.SELECT_ALL -> allPages
                        QuickFilter.DESELECT -> emptySet()
                        QuickFilter.EVEN -> allPages.filter { it % 2 == 0 }.toSet()
                        QuickFilter.ODD -> allPages.filter { it % 2 == 1 }.toSet()
                        QuickFilter.FIRST_FIVE -> (1..minOf(5, pageCount)).toSet()
                    }
                )
            },
            modifier = Modifier.padding(top = 16.dp)
        )

        MatrixHeader(
            gridView = gridView,
            onGridView = { gridView = true },
            onListView = { gridView = false },
            modifier = Modifier.padding(top = 16.dp)
        )

        val togglePage: (Int) -> Unit = { page ->
            if (mode != SplitMode.EVERY) {
                applySelection(if (page in selected) selected - page else selected + page)
            }
        }

        if (gridView) {
            PageGrid(
                pageCount = pageCount,
                selected = selected,
                showAll = showAllPages,
                onToggle = togglePage,
                onShowAll = { showAllPages = true },
                onShowFewer = { showAllPages = false },
                thumbnail = pageThumbnail,
                modifier = Modifier.padding(top = 10.dp)
            )
        } else {
            PageList(
                pageCount = pageCount,
                selected = selected,
                showAll = showAllPages,
                onToggle = togglePage,
                onShowAll = { showAllPages = true },
                onShowFewer = { showAllPages = false },
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        OutputPreferences(
            outputName = outputName,
            onOutputNameChange = { outputName = it },
            selectedCount = selected.size,
            mergeIntoOne = mergeIntoOne,
            onMergeChange = { on ->
                mergeIntoOne = on
                saveAsZip = !on          // dono me se ek hamesha ON
            },
            saveAsZip = saveAsZip,
            onZipChange = { on ->
                saveAsZip = on
                mergeIntoOne = !on
            },
            preserveBookmarks = preserveBookmarks,
            onBookmarksChange = { preserveBookmarks = it },
            modifier = Modifier.padding(top = 20.dp)
        )

        ActionGroup(
            selectedCount = selected.size,
            canSplit = canSplit,
            onPreview = { onPreview(options()) },
            onSplit = { onSplit(options()) },
            modifier = Modifier.padding(top = 20.dp)
        )
    }
}

/* ----------------------------- Header ----------------------------- */

@Composable
private fun Header(onBack: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp).offset(x = (-8).dp)) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.offset(x = (-8).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.split_title),
                        style = AppTextStyle.Title18Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.TextPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.TealLight)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AppColors.Teal))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.split_ready),
                            style = AppTextStyle.Caption11Medium,
                            color = AppColors.TealOnLight
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.split_subtitle),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onHelp, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.cd_split_help),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ----------------------------- Source card ----------------------------- */

@Composable
private fun SourceCard(
    name: String,
    sizeMb: Float,
    pageCount: Int,
    onChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceSofter),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Description, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTextStyle.Body14Medium,
                    color = AppColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.compress_size_mb, String.format(Locale.getDefault(), "%.1f", sizeMb)),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.bullet_separator),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.split_pages_count, pageCount),
                        style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                        color = AppColors.Primary
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSoft)
                .clickable(onClick = onChange)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.split_change),
                style = AppTextStyle.Label12Medium,
                color = AppColors.Primary
            )
        }
    }
}

/* ----------------------------- Split method ----------------------------- */

@Composable
private fun SplitMethodSelector(
    selected: SplitMode,
    onSelected: (SplitMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.split_method_label).uppercase(Locale.getDefault()),
            style = AppTextStyle.Label12Medium,
            color = AppColors.TextSecondary,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceSoft)
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SplitMode.values().forEach { mode ->
                val isSelected = mode == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(8.dp)) else Modifier)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) AppColors.Surface else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(mode) })
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        mode.icon,
                        null,
                        tint = if (isSelected) AppColors.Primary else AppColors.TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(mode.label),
                        style = AppTextStyle.Caption11Medium.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        color = if (isSelected) AppColors.Primary else AppColors.TextSecondary,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/* ----------------------------- Range card ----------------------------- */

@Composable
private fun RangeCard(
    expression: String,
    hasError: Boolean,
    readOnly: Boolean,
    selectedCount: Int,
    pageCount: Int,
    selected: Set<Int>,
    allPages: Set<Int>,
    onExpressionChange: (String) -> Unit,
    onClear: () -> Unit,
    onQuickFilter: (QuickFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val evenSet = remember(pageCount) { allPages.filter { it % 2 == 0 }.toSet() }
    val oddSet = remember(pageCount) { allPages.filter { it % 2 == 1 }.toSet() }
    val firstFive = remember(pageCount) { (1..minOf(5, pageCount)).toSet() }

    val activeFilter: QuickFilter? = when {
        selected.isEmpty() -> QuickFilter.DESELECT
        selected == allPages -> QuickFilter.SELECT_ALL
        selected == evenSet -> QuickFilter.EVEN
        selected == oddSet -> QuickFilter.ODD
        selected == firstFive -> QuickFilter.FIRST_FIVE
        else -> null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.split_range_label),
                style = AppTextStyle.Label12Medium,
                color = AppColors.TextPrimary
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.ProGlow)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.split_selected_count, selectedCount, pageCount),
                    style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Primary
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceTint)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Pin, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (expression.isEmpty()) {
                        Text(
                            text = stringResource(R.string.split_range_hint),
                            style = AppTextStyle.Body14Medium,
                            color = AppColors.TextHint
                        )
                    }
                    BasicTextField(
                        value = expression,
                        onValueChange = onExpressionChange,
                        readOnly = readOnly,
                        singleLine = true,
                        textStyle = AppTextStyle.Body14Medium.copy(
                            color = if (hasError) AppColors.Error else AppColors.TextPrimary
                        ),
                        cursorBrush = SolidColor(AppColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (expression.isNotEmpty() && !readOnly) {
                    Icon(
                        imageVector = Icons.Outlined.Cancel,
                        contentDescription = stringResource(R.string.cd_clear_range),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear)
                    )
                }
            }
            if (hasError) {
                Text(
                    text = stringResource(R.string.split_range_error, pageCount),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Error,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(QuickFilter.values().toList()) { filter ->
                val isActive = filter == activeFilter
                val label = when (filter) {
                    QuickFilter.EVEN -> stringResource(filter.label, evenSet.size)
                    QuickFilter.ODD -> stringResource(filter.label, oddSet.size)
                    else -> stringResource(filter.label)
                }
                Row(
                    modifier = Modifier
                        .then(if (isActive) Modifier.shadow(1.dp, CircleShape) else Modifier)
                        .clip(CircleShape)
                        .background(if (isActive) AppColors.Primary else AppColors.SurfaceSoft)
                        .clickable(enabled = !readOnly) { onQuickFilter(filter) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isActive) {
                        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = label,
                        style = AppTextStyle.Label12Medium,
                        color = if (isActive) Color.White else AppColors.TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* ----------------------------- Matrix header ----------------------------- */

@Composable
private fun MatrixHeader(
    gridView: Boolean,
    onGridView: () -> Unit,
    onListView: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.split_matrix_title),
                style = AppTextStyle.Body14Medium,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.split_matrix_hint),
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSoft)
                .padding(2.dp)
        ) {
            ViewToggle(Icons.Outlined.GridView, R.string.cd_grid_view, gridView, onGridView)
            ViewToggle(Icons.Outlined.ViewList, R.string.cd_list_view, !gridView, onListView)
        }
    }
}

@Composable
private fun ViewToggle(icon: ImageVector, @StringRes description: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .then(if (selected) Modifier.shadow(1.dp, RoundedCornerShape(4.dp)) else Modifier)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) AppColors.Surface else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = stringResource(description),
            tint = if (selected) AppColors.Primary else AppColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

/* ----------------------------- Page grid ----------------------------- */

@Composable
private fun PageGrid(
    pageCount: Int,
    selected: Set<Int>,
    showAll: Boolean,
    onToggle: (Int) -> Unit,
    onShowAll: () -> Unit,
    onShowFewer: () -> Unit,
    thumbnail: @Composable BoxScope.(page: Int, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val collapsed = !showAll && pageCount > COLLAPSED_PAGE_COUNT
    val visibleCount = if (collapsed) COLLAPSED_PAGE_COUNT else pageCount
    // null = overflow card
    val cells: List<Int?> = buildList {
        for (p in 1..visibleCount) add(p)
        if (collapsed) add(null)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(3).forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowCells.forEach { cell ->
                    if (cell == null) {
                        OverflowCard(
                            hiddenCount = pageCount - COLLAPSED_PAGE_COUNT,
                            firstHidden = COLLAPSED_PAGE_COUNT + 1,
                            lastHidden = pageCount,
                            onClick = onShowAll,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    } else {
                        PageCard(
                            page = cell,
                            checked = cell in selected,
                            onToggle = { onToggle(cell) },
                            thumbnail = thumbnail,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(3 - rowCells.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (showAll && pageCount > COLLAPSED_PAGE_COUNT) {
            Text(
                text = stringResource(R.string.split_show_fewer),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowFewer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PageCard(
    page: Int,
    checked: Boolean,
    onToggle: () -> Unit,
    thumbnail: @Composable BoxScope.(page: Int, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .alpha(if (checked) 1f else 0.7f)
            .shadow(1.dp, shape)
            .clip(shape)
            .background(AppColors.Surface)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(8.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.SurfaceTint)
            ) {
                thumbnail(page, checked)
                if (checked) {
                    Box(Modifier.matchParentSize().background(AppColors.PrimaryTint))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.split_page_label, page),
                    style = AppTextStyle.Caption11Medium.copy(
                        fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (checked) AppColors.Primary else AppColors.TextSecondary,
                    maxLines = 1
                )
                if (checked) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = AppColors.Primary, modifier = Modifier.size(13.dp))
                }
            }
        }

        // Selection indicator (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 2.dp, y = (-2).dp)
                .size(20.dp)
                .then(if (checked) Modifier.shadow(1.dp, CircleShape) else Modifier)
                .clip(CircleShape)
                .background(if (checked) AppColors.Primary else AppColors.SurfaceSofter),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
            } else {
                Box(Modifier.size(12.dp).clip(CircleShape).background(AppColors.SurfaceSoft))
            }
        }
    }
}

@Composable
private fun OverflowCard(
    hiddenCount: Int,
    firstHidden: Int,
    lastHidden: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceSofter)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(1.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.MoreHoriz, null, tint = AppColors.TextPrimary, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.split_more_pages, hiddenCount),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = stringResource(R.string.split_tap_inspect),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
        Text(
            text = stringResource(R.string.split_pages_range, firstHidden, lastHidden),
            style = AppTextStyle.Caption11Medium,
            color = AppColors.Primary,
            modifier = Modifier.padding(vertical = 5.dp),
            maxLines = 1
        )
    }
}

/** Default placeholder thumbnail: text-line skeleton. Real app mein page bitmap dikhao. */
@Composable
private fun BoxScope.SkeletonThumbnail(page: Int, checked: Boolean) {
    val titleColor = if (checked) AppColors.Primary.copy(alpha = 0.3f) else AppColors.TextSecondary.copy(alpha = 0.3f)
    val lineColor = AppColors.TextSecondary.copy(alpha = if (checked) 0.2f else 0.15f)
    Column(
        modifier = Modifier.matchParentSize().padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.35f + (page % 4) * 0.1f)
                .height(6.dp)
                .background(titleColor, RoundedCornerShape(4.dp))
        )
        repeat(9) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i % 4 == 3) 0.6f else 1f)
                    .height(4.dp)
                    .background(lineColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

/* ----------------------------- Page list (alternate view) ----------------------------- */

@Composable
private fun PageList(
    pageCount: Int,
    selected: Set<Int>,
    showAll: Boolean,
    onToggle: (Int) -> Unit,
    onShowAll: () -> Unit,
    onShowFewer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val collapsed = !showAll && pageCount > COLLAPSED_PAGE_COUNT
    val visibleCount = if (collapsed) COLLAPSED_PAGE_COUNT else pageCount

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (page in 1..visibleCount) {
            val checked = page in selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(1.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (checked) AppColors.SurfaceTint else AppColors.Surface)
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(page) })
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.split_page_label, page),
                    style = AppTextStyle.Body14Medium,
                    color = if (checked) AppColors.Primary else AppColors.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (checked) AppColors.Primary else AppColors.SurfaceSofter),
                    contentAlignment = Alignment.Center
                ) {
                    if (checked) {
                        Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        if (collapsed) {
            Text(
                text = stringResource(R.string.split_more_pages, pageCount - COLLAPSED_PAGE_COUNT),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowAll)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        } else if (showAll && pageCount > COLLAPSED_PAGE_COUNT) {
            Text(
                text = stringResource(R.string.split_show_fewer),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.Primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onShowFewer)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

/* ----------------------------- Output preferences ----------------------------- */

@Composable
private fun OutputPreferences(
    outputName: String,
    onOutputNameChange: (String) -> Unit,
    selectedCount: Int,
    mergeIntoOne: Boolean,
    onMergeChange: (Boolean) -> Unit,
    saveAsZip: Boolean,
    onZipChange: (Boolean) -> Unit,
    preserveBookmarks: Boolean,
    onBookmarksChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.split_output_prefs).uppercase(Locale.getDefault()),
            style = AppTextStyle.Label12Medium.copy(letterSpacing = 0.6.sp),
            color = AppColors.TextSecondary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceTint)
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.split_target_filename),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary
                    )
                    BasicTextField(
                        value = outputName,
                        onValueChange = onOutputNameChange,
                        readOnly = !editing,
                        singleLine = true,
                        textStyle = AppTextStyle.Body14Medium.copy(color = AppColors.TextPrimary),
                        cursorBrush = SolidColor(AppColors.Primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = { editing = !editing }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (editing) Icons.Outlined.Check else Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_split_name),
                    tint = AppColors.Primary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ToggleRow(
                title = stringResource(R.string.split_merge_title),
                description = stringResource(R.string.split_merge_desc, selectedCount),
                checked = mergeIntoOne,
                onCheckedChange = onMergeChange
            )
            ToggleRow(
                title = stringResource(R.string.split_zip_title),
                description = stringResource(R.string.split_zip_desc, selectedCount),
                checked = saveAsZip,
                onCheckedChange = onZipChange
            )
            ToggleRow(
                title = stringResource(R.string.split_bookmarks_title),
                description = stringResource(R.string.split_bookmarks_desc),
                checked = preserveBookmarks,
                onCheckedChange = onBookmarksChange
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = AppTextStyle.Body14Medium, color = AppColors.TextPrimary)
            Text(text = description, style = AppTextStyle.Label12Regular, color = AppColors.TextSecondary)
        }
        Spacer(Modifier.width(12.dp))
        SplitSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SplitSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .then(if (checked) Modifier.shadow(1.dp, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(if (checked) AppColors.Primary else AppColors.SurfaceTrack)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(20.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(if (checked) Color.White else AppColors.SwitchOff)
        )
    }
}

/* ----------------------------- Action group ----------------------------- */

@Composable
private fun ActionGroup(
    selectedCount: Int,
    canSplit: Boolean,
    onPreview: () -> Unit,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceSoft)
                .clickable(enabled = canSplit, onClick = onPreview)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.split_preview),
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
                .background(if (canSplit) AppColors.Primary else AppColors.SwitchOff)
                .then(if (canSplit) Modifier.clickable(onClick = onSplit) else Modifier)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.split_action, selectedCount),
                style = AppTextStyle.Button14SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
private fun SplitPdfScreenPreview() {
    PdfToolkitTheme { SplitPdfScreen(contentPadding = PaddingValues(0.dp)) }
}