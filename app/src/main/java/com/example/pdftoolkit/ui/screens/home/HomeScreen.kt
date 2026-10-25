package com.example.pdftoolkit.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme

/* ----------------------------- Models ----------------------------- */

private data class FrequentTool(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    @StringRes val badge: Int? = null,
    val badgeBg: Color = AppColors.PrimaryTint,
    val badgeTextColor: Color = AppColors.Primary
)

private data class ToolItem(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color
)

private data class RecentDoc(
    val name: String,
    val size: String,
    val pages: Int,
    val time: String,
    val timeHighlight: Boolean,
    val icon: ImageVector,
    val iconBg: Color,
    val accent: Color
)

/* ----------------------------- Sample data ----------------------------- */

private val frequentTools = listOf(
    FrequentTool(R.string.tool_merge_pdf, R.string.tool_merge_pdf_desc, Icons.Outlined.MergeType, AppColors.PrimaryTint, AppColors.Primary, R.string.badge_popular),
    FrequentTool(R.string.tool_split_pdf, R.string.tool_split_pdf_desc, Icons.Outlined.CallSplit, AppColors.SurfaceSoft, AppColors.Primary),
    FrequentTool(R.string.tool_compress, R.string.tool_compress_desc, Icons.Outlined.Compress, AppColors.TealTint, AppColors.Teal, R.string.badge_compress_percent, AppColors.TealLight, AppColors.TealOnLight),
    FrequentTool(R.string.tool_photos_to_pdf, R.string.tool_photos_to_pdf_desc, Icons.Outlined.PhotoLibrary, AppColors.Peach, AppColors.OnPeach),
    FrequentTool(R.string.tool_pdf_to_image, R.string.tool_pdf_to_image_desc, Icons.Outlined.Image, AppColors.SurfaceSoft, AppColors.Primary)
)

private val organizeTools = listOf(
    ToolItem(R.string.tool_reorder, R.string.tool_reorder_desc, Icons.Outlined.SwapVert, AppColors.SurfaceSofter, AppColors.Primary),
    ToolItem(R.string.tool_rotate, R.string.tool_rotate_desc, Icons.Outlined.RotateRight, AppColors.SurfaceSofter, AppColors.Primary),
    ToolItem(R.string.tool_delete_pages, R.string.tool_delete_pages_desc, Icons.Outlined.DeleteOutline, AppColors.ErrorSoft, AppColors.Error),
    ToolItem(R.string.tool_batch_merge, R.string.tool_batch_merge_desc, Icons.Outlined.LibraryAdd, AppColors.SurfaceSofter, AppColors.Primary)
)

private val convertTools = listOf(
    ToolItem(R.string.tool_extract_text, R.string.tool_extract_text_desc, Icons.Outlined.TextFields, AppColors.TealTint, AppColors.Teal),
    ToolItem(R.string.tool_to_word, R.string.tool_to_word_desc, Icons.Outlined.Description, AppColors.SurfaceSofter, AppColors.Primary)
)

private val securityTools = listOf(
    ToolItem(R.string.tool_set_password, R.string.tool_set_password_desc, Icons.Outlined.Lock, AppColors.Peach, AppColors.OnPeach),
    ToolItem(R.string.tool_watermark, R.string.tool_watermark_desc, Icons.Outlined.PictureInPictureAlt, AppColors.SurfaceSofter, AppColors.Primary)
)

private val recentDocs = listOf(
    RecentDoc("Q3_Financial_Report_Final.pdf", "4.2 MB", 28, "10:45 AM", true, Icons.Outlined.Description, AppColors.PrimaryTint, AppColors.Primary),
    RecentDoc("Signed_Lease_Agreement.pdf", "1.8 MB", 6, "Yesterday", false, Icons.Outlined.Article, AppColors.TealTint, AppColors.Teal),
    RecentDoc("Tax_Deductions_2024.pdf", "850 KB", 4, "Oct 14", false, Icons.Outlined.Description, AppColors.SurfaceSofter, AppColors.Primary)
)

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

/**
 * Sirf content. Top bar / bottom bar MainScreen mein hain.
 * [contentPadding] Scaffold se aata hai.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit = {},
    onChooseFiles: () -> Unit = {},
    onCameraScan: () -> Unit = {},
    onToolClick: (Int) -> Unit = {},
    onViewAllClick: () -> Unit = {},
    onDocClick: (String) -> Unit = {},
    onDocMoreClick: (String) -> Unit = {}
) {
    val layoutDirection = LocalLayoutDirection.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp
            )
    ) {
        SearchBar(onClick = onSearchClick, modifier = Modifier.padding(horizontal = ScreenPadding))

        Spacer(Modifier.height(20.dp))
        HeroCard(
            onChooseFiles = onChooseFiles,
            onCameraScan = onCameraScan,
            modifier = Modifier.padding(horizontal = ScreenPadding)
        )

        Spacer(Modifier.height(20.dp))
        FrequentToolsSection(onToolClick = onToolClick)

        Spacer(Modifier.height(20.dp))
        ToolDirectory(onToolClick = onToolClick, modifier = Modifier.padding(horizontal = ScreenPadding))

        Spacer(Modifier.height(20.dp))
        RecentDocumentsSection(
            onViewAllClick = onViewAllClick,
            onDocClick = onDocClick,
            onDocMoreClick = onDocMoreClick,
            modifier = Modifier.padding(horizontal = ScreenPadding)
        )

        Spacer(Modifier.height(20.dp))
        PrivacyBadge(modifier = Modifier.padding(horizontal = ScreenPadding))
    }
}

/* ----------------------------- Search bar ----------------------------- */

@Composable
private fun SearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Search, null, tint = AppColors.TextHint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.home_search_hint),
            style = AppTextStyle.Body14Regular,
            color = AppColors.TextHint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Mic, stringResource(R.string.cd_voice_input), tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = {}, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Tune, stringResource(R.string.cd_filter_documents), tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

/* ----------------------------- Hero card ----------------------------- */

@Composable
private fun HeroCard(
    onChooseFiles: () -> Unit,
    onCameraScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(AppColors.HeroGradientStart, AppColors.SurfaceTint, AppColors.SurfaceSoft)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(96.dp)
                .clip(CircleShape)
                .background(AppColors.HeroGlow)
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.PrimaryTint)
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Bolt, null, tint = AppColors.Primary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.home_badge_fast_secure),
                            style = AppTextStyle.Caption11Medium,
                            color = AppColors.Primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.home_hero_title),
                        style = AppTextStyle.Title22SemiBold,
                        color = AppColors.TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.home_hero_subtitle),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeroButton(
                    text = stringResource(R.string.home_choose_files),
                    icon = Icons.Outlined.FolderOpen,
                    color = AppColors.PrimaryButton,
                    onClick = onChooseFiles,
                    modifier = Modifier.weight(1f),
                    elevation = 4.dp
                )
                HeroButton(
                    text = stringResource(R.string.home_camera_scan),
                    icon = Icons.Outlined.CameraAlt,
                    color = AppColors.Teal,
                    onClick = onCameraScan,
                    modifier = Modifier.weight(1f),
                    elevation = 1.dp
                )
            }
        }
    }
}

@Composable
private fun HeroButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    elevation: Dp = 1.dp
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .shadow(elevation, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(color)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = text, style = AppTextStyle.Button14SemiBold, color = Color.White, maxLines = 1)
    }
}

/* ----------------------------- Frequent tools ----------------------------- */

@Composable
private fun FrequentToolsSection(onToolClick: (Int) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_frequent_tools),
                style = AppTextStyle.Body14Medium,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.home_slide_to_explore),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.Primary
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(frequentTools) { tool ->
                FrequentToolCard(tool = tool, onClick = { onToolClick(tool.title) })
            }
        }
    }
}

@Composable
private fun FrequentToolCard(tool: FrequentTool, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(112.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(tool.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, null, tint = tool.iconTint, modifier = Modifier.size(18.dp))
            }
            Text(
                text = stringResource(tool.title),
                style = AppTextStyle.Label12Medium,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(tool.subtitle),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        tool.badge?.let { badge ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .clip(CircleShape)
                    .background(tool.badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(badge),
                    style = AppTextStyle.Badge10Regular,
                    color = tool.badgeTextColor
                )
            }
        }
    }
}

/* ----------------------------- Tool directory ----------------------------- */

@Composable
private fun ToolDirectory(onToolClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        CategoryHeader(icon = Icons.Outlined.FolderOpen, titleRes = R.string.cat_organize)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            organizeTools.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { tool ->
                        ToolCard(tool, { onToolClick(tool.title) }, Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        CategoryHeader(icon = Icons.Outlined.TextFields, titleRes = R.string.cat_convert)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            convertTools.forEach { tool ->
                ToolCard(tool, { onToolClick(tool.title) }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        CategoryHeader(icon = Icons.Outlined.VerifiedUser, titleRes = R.string.cat_security)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            securityTools.forEach { tool ->
                ToolCard(tool, { onToolClick(tool.title) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryHeader(icon: ImageVector, @StringRes titleRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(titleRes),
            style = AppTextStyle.Body14Medium,
            color = AppColors.TextPrimary
        )
    }
}

@Composable
private fun ToolCard(tool: ToolItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tool.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, null, tint = tool.iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(tool.title),
                style = AppTextStyle.Body14SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(tool.subtitle),
                style = AppTextStyle.Label12Regular,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ----------------------------- Recent documents ----------------------------- */

@Composable
private fun RecentDocumentsSection(
    onViewAllClick: () -> Unit,
    onDocClick: (String) -> Unit,
    onDocMoreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.History, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.home_recent_documents),
                    style = AppTextStyle.Title16Medium,
                    color = AppColors.TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onViewAllClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_view_all),
                    style = AppTextStyle.Button14SemiBold,
                    color = AppColors.Primary
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recentDocs.forEach { doc ->
                RecentDocItem(
                    doc = doc,
                    onClick = { onDocClick(doc.name) },
                    onMoreClick = { onDocMoreClick(doc.name) }
                )
            }
        }
    }
}

@Composable
private fun RecentDocItem(doc: RecentDoc, onClick: () -> Unit, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(doc.iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(doc.icon, null, tint = doc.accent, modifier = Modifier.size(22.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(doc.accent)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.pdf_badge),
                    style = AppTextStyle.Badge9Bold,
                    color = Color.White
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = doc.name,
                style = AppTextStyle.Body14Medium,
                color = AppColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetaText(doc.size)
                MetaText(stringResource(R.string.bullet_separator))
                MetaText(stringResource(R.string.doc_pages_format, doc.pages))
                MetaText(stringResource(R.string.bullet_separator))
                if (doc.timeHighlight) {
                    Text(
                        text = doc.time,
                        style = AppTextStyle.Label12Regular.copy(fontWeight = FontWeight.Medium),
                        color = AppColors.Teal,
                        maxLines = 1
                    )
                } else {
                    MetaText(doc.time)
                }
            }
        }

        IconButton(onClick = onMoreClick, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.cd_document_options, doc.name),
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

/* ----------------------------- Privacy badge ----------------------------- */

@Composable
private fun PrivacyBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(AppColors.TealLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = AppColors.TealOnLight, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.home_privacy_title),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.home_privacy_body),
                style = AppTextStyle.Label12Regular.copy(lineHeight = 15.sp),
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1024)
@Composable
private fun HomeScreenPreview() {
    PdfToolkitTheme { HomeScreen(contentPadding = PaddingValues(0.dp)) }
}
