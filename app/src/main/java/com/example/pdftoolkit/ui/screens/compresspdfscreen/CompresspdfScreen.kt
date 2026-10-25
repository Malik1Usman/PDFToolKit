package com.example.pdftoolkit.ui.screens.compresspdfscreen


import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme
import java.util.Locale

/* ----------------------------- Models ----------------------------- */

enum class CompressionPreset(
    @StringRes val title: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val reductionPercent: Int,
    val dpi: Int,
    val etaSeconds: Float,
    @StringRes val tag: Int,
    val tagBg: Color,
    val tagFg: Color,
    val tagIsPill: Boolean
) {
    EXTREME(
        R.string.compress_preset_extreme, R.string.compress_preset_extreme_desc, Icons.Outlined.Compress,
        75, 72, 1.1f, R.string.compress_tag_web, AppColors.Peach, Color(0xFF2A1700), false
    ),
    RECOMMENDED(
        R.string.compress_preset_recommended, R.string.compress_preset_recommended_desc, Icons.Outlined.AutoAwesome,
        60, 150, 1.4f, R.string.compress_tag_popular, AppColors.Primary, Color.White, true
    ),
    BASIC(
        R.string.compress_preset_basic, R.string.compress_preset_basic_desc, Icons.Outlined.HighQuality,
        35, 220, 1.8f, R.string.compress_tag_print, AppColors.SurfaceSofter, AppColors.TextSecondary, false
    )
}

/** Compress / Preview dabane par ye options milte hain. */
data class CompressOptions(
    val sourceName: String,
    val outputName: String,           // ".pdf" ke saath
    val preset: CompressionPreset,
    val imageDpi: Int,                // 72 / 150 / 220
    val stripMetadata: Boolean,
    val compressFontSubsets: Boolean,
    val convertToGrayscale: Boolean,
    val estimatedSizeMb: Float
)

private val DpiChoices = listOf(72, 150, 220)
private val ScreenPadding = 16.dp

/** Size estimate: preset ke hisab se, phir user ke custom rules ke hisab se thoda adjust. */
private fun dpiFactor(dpi: Int) = when (dpi) {
    72 -> 0.626f
    220 -> 1.626f
    else -> 1f
}

private fun presetEstimateMb(sourceMb: Float, preset: CompressionPreset) =
    sourceMb * (1f - preset.reductionPercent / 100f)

private fun estimateMb(sourceMb: Float, preset: CompressionPreset, dpi: Int, grayscale: Boolean): Float {
    val base = presetEstimateMb(sourceMb, preset)
    val dpiAdjust = dpiFactor(dpi) / dpiFactor(preset.dpi)
    val grayAdjust = if (grayscale) 0.85f else 1f
    return (base * dpiAdjust * grayAdjust).coerceIn(0.1f, sourceMb)
}

private fun Float.mb(): String = String.format(Locale.getDefault(), "%.1f", this)

/* ----------------------------- Screen ----------------------------- */

/**
 * Compress PDF workflow. Bottom nav bar yahan hide hota hai (routesWithoutBottomBar).
 */
@Composable
fun CompressPdfScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    sourceName: String = "Annual_Corporate_Audit_2024.pdf",
    sourceSizeMb: Float = 24.8f,
    pageCount: Int = 42,
    hasHighResScans: Boolean = true,
    onBack: () -> Unit = {},
    onHelp: () -> Unit = {},
    onChangeFile: () -> Unit = {},
    onPreview: (CompressOptions) -> Unit = {},
    onCompress: (CompressOptions) -> Unit = {}
) {
    var preset by remember { mutableStateOf(CompressionPreset.RECOMMENDED) }
    var dpi by remember { mutableStateOf(CompressionPreset.RECOMMENDED.dpi) }
    var stripMetadata by remember { mutableStateOf(true) }
    var fontSubsets by remember { mutableStateOf(true) }
    var grayscale by remember { mutableStateOf(false) }
    var rulesExpanded by remember { mutableStateOf(true) }
    var outputName by remember {
        mutableStateOf(sourceName.removeSuffix(".pdf") + "_compressed.pdf")
    }

    val estimatedMb = estimateMb(sourceSizeMb, preset, dpi, grayscale)
    val savedMb = sourceSizeMb - estimatedMb
    val reductionPercent = ((savedMb / sourceSizeMb) * 100f).toInt()
    val activeRules = 1 + listOf(stripMetadata, fontSubsets, grayscale).count { it }

    fun options() = CompressOptions(
        sourceName = sourceName,
        outputName = outputName.trim(),
        preset = preset,
        imageDpi = dpi,
        stripMetadata = stripMetadata,
        compressFontSubsets = fontSubsets,
        convertToGrayscale = grayscale,
        estimatedSizeMb = estimatedMb
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .padding(top = contentPadding.calculateTopPadding())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding)
            .padding(top = 12.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp)
    ) {
        SubHeader(onBack = onBack, onHelp = onHelp)

        SourceCard(
            name = sourceName,
            sizeMb = sourceSizeMb,
            pageCount = pageCount,
            hasHighResScans = hasHighResScans,
            onChange = onChangeFile,
            modifier = Modifier.padding(top = 16.dp)
        )

        SavingsGauge(
            sourceMb = sourceSizeMb,
            estimatedMb = estimatedMb,
            savedMb = savedMb,
            reductionPercent = reductionPercent,
            etaSeconds = preset.etaSeconds,
            modifier = Modifier.padding(top = 16.dp)
        )

        PresetsSection(
            sourceMb = sourceSizeMb,
            selected = preset,
            onSelected = {
                preset = it
                dpi = it.dpi          // preset badalne par DPI bhi uske default pe aata hai
            },
            modifier = Modifier.padding(top = 16.dp)
        )

        AdvancedRules(
            expanded = rulesExpanded,
            onToggleExpanded = { rulesExpanded = !rulesExpanded },
            activeCount = activeRules,
            dpi = dpi,
            onDpiChange = { dpi = it },
            stripMetadata = stripMetadata,
            onStripChange = { stripMetadata = it },
            fontSubsets = fontSubsets,
            onFontSubsetsChange = { fontSubsets = it },
            grayscale = grayscale,
            onGrayscaleChange = { grayscale = it },
            modifier = Modifier.padding(top = 16.dp)
        )

        DestinationField(
            name = outputName,
            onNameChange = { outputName = it },
            modifier = Modifier.padding(top = 16.dp)
        )

        ActionRow(
            savedMb = savedMb,
            onPreview = { onPreview(options()) },
            onCompress = { onCompress(options()) },
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

/* ----------------------------- Sub header ----------------------------- */

@Composable
private fun SubHeader(onBack: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceTint)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.compress_title),
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
                            text = stringResource(R.string.compress_ready),
                            style = AppTextStyle.Caption11Medium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.275.sp
                            ),
                            color = AppColors.TealOnLight
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.compress_subtitle),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceTint)
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.cd_compress_help),
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ----------------------------- Source document card ----------------------------- */

@Composable
private fun SourceCard(
    name: String,
    sizeMb: Float,
    pageCount: Int,
    hasHighResScans: Boolean,
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
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.PictureAsPdf, null, tint = AppColors.Primary, modifier = Modifier.size(24.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.PrimaryButton)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.compress_pdf_version),
                    style = AppTextStyle.Badge9Bold,
                    color = Color.White,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(
                text = name,
                style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                color = AppColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.compress_size_mb, sizeMb.mb()),
                    style = AppTextStyle.Label12SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.bullet_separator),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
                Text(
                    text = stringResource(R.string.compress_pages, pageCount),
                    style = AppTextStyle.Label12Regular,
                    color = AppColors.TextSecondary
                )
            }
            if (hasHighResScans) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.WarningAmber, null, tint = AppColors.OnPeach, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.compress_highres_note),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clip(RoundedCornerShape(8.dp))
                .background(AppColors.SurfaceSoft)
                .clickable(onClick = onChange)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.compress_change),
                style = AppTextStyle.Label12SemiBold,
                color = AppColors.Primary
            )
        }
    }
}

/* ----------------------------- Savings gauge ----------------------------- */

@Composable
private fun SavingsGauge(
    sourceMb: Float,
    estimatedMb: Float,
    savedMb: Float,
    reductionPercent: Int,
    etaSeconds: Float,
    modifier: Modifier = Modifier
) {
    val finalFraction = (estimatedMb / sourceMb).coerceIn(0.02f, 0.98f)
    val footprintPercent = (finalFraction * 100f).toInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.compress_projected).uppercase(Locale.getDefault()),
                style = AppTextStyle.Label12SemiBold.copy(letterSpacing = 0.6.sp),
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bolt, null, tint = AppColors.Teal, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.compress_eta, String.format(Locale.getDefault(), "%.1f", etaSeconds)),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Teal,
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.compress_size_mb, estimatedMb.mb()),
                    style = AppTextStyle.Title22SemiBold.copy(fontWeight = FontWeight.Bold),
                    color = AppColors.Primary,
                    modifier = Modifier.alignByBaseline()
                )
                Text(
                    text = stringResource(R.string.compress_size_mb, sourceMb.mb()),
                    style = AppTextStyle.Body14Regular.copy(textDecoration = TextDecoration.LineThrough),
                    color = AppColors.TextSecondary,
                    modifier = Modifier.alignByBaseline()
                )
            }
            Row(
                modifier = Modifier
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(AppColors.TealLight)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.ArrowDownward, null, tint = AppColors.TealOnLight, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.compress_reduction_badge, reductionPercent, savedMb.mb()),
                    style = AppTextStyle.Label12SemiBold.copy(fontWeight = FontWeight.Bold),
                    color = AppColors.TealOnLight,
                    maxLines = 1
                )
            }
        }

        // Progress gauge: final footprint (blue) + reclaimed space (teal)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(AppColors.SurfaceTrack)
        ) {
            Box(
                modifier = Modifier
                    .weight(finalFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(AppColors.Primary)
            )
            Box(
                modifier = Modifier
                    .weight(1f - finalFraction)
                    .fillMaxHeight()
                    .background(AppColors.GaugeReclaimed)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.compress_footprint))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)) {
                        append(stringResource(R.string.compress_percent, footprintPercent))
                    }
                },
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.compress_reclaimed))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = AppColors.Teal)) {
                        append(stringResource(R.string.compress_size_mb, savedMb.mb()))
                    }
                },
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Presets ----------------------------- */

@Composable
private fun PresetsSection(
    sourceMb: Float,
    selected: CompressionPreset,
    onSelected: (CompressionPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.compress_profile_title),
                style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                color = AppColors.TextPrimary
            )
            Text(
                text = stringResource(R.string.compress_profile_hint),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompressionPreset.values().forEach { preset ->
                PresetCard(
                    preset = preset,
                    estimateMb = presetEstimateMb(sourceMb, preset),
                    selected = preset == selected,
                    onClick = { onSelected(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: CompressionPreset,
    estimateMb: Float,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(if (selected) AppColors.SurfaceTint else AppColors.Surface)
            .then(if (selected) Modifier.border(2.dp, AppColors.Primary, shape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 24.dp, y = 24.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AppColors.PrimaryTint.copy(alpha = 0.05f))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .then(if (selected) Modifier.shadow(1.dp, CircleShape) else Modifier)
                        .clip(CircleShape)
                        .background(if (selected) AppColors.Primary else AppColors.SurfaceSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        preset.icon,
                        null,
                        tint = if (selected) Color.White else AppColors.TextSecondary,
                        modifier = Modifier.size(if (selected) 18.dp else 16.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(preset.title),
                            style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                            color = AppColors.TextPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(if (preset.tagIsPill) CircleShape else RoundedCornerShape(4.dp))
                                .background(preset.tagBg)
                                .padding(horizontal = if (preset.tagIsPill) 8.dp else 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(preset.tag),
                                style = AppTextStyle.Badge10Regular.copy(
                                    fontWeight = if (preset == CompressionPreset.BASIC) FontWeight.Normal else FontWeight.Bold,
                                    lineHeight = 15.sp,
                                    letterSpacing = if (preset.tagIsPill) 0.25.sp else 0.sp
                                ),
                                color = preset.tagFg,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        text = stringResource(preset.description),
                        style = AppTextStyle.Label12Regular,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                modifier = Modifier.padding(start = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.compress_preset_estimate, estimateMb.mb()),
                        style = AppTextStyle.Body14Regular.copy(fontWeight = FontWeight.Bold),
                        color = if (selected) AppColors.Primary else AppColors.TextPrimary,
                        maxLines = 1
                    )
                    if (selected) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.CheckCircle, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    text = stringResource(R.string.compress_preset_reduction, preset.reductionPercent),
                    style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Teal
                )
            }
        }
    }
}

/* ----------------------------- Advanced rules ----------------------------- */

@Composable
private fun AdvancedRules(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    activeCount: Int,
    dpi: Int,
    onDpiChange: (Int) -> Unit,
    stripMetadata: Boolean,
    onStripChange: (Boolean) -> Unit,
    fontSubsets: Boolean,
    onFontSubsetsChange: (Boolean) -> Unit,
    grayscale: Boolean,
    onGrayscaleChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(if (expanded) 0f else -90f, label = "chevron")

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
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.compress_advanced_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.compress_active_count, activeCount),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(18.dp).rotate(chevronRotation)
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                // Image downsampling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RuleText(
                        title = R.string.compress_downsample,
                        description = R.string.compress_downsample_desc,
                        modifier = Modifier.weight(1f)
                    )
                    DpiSegmented(selected = dpi, onSelected = onDpiChange)
                }

                RuleSwitchRow(
                    title = R.string.compress_strip,
                    description = R.string.compress_strip_desc,
                    checked = stripMetadata,
                    onCheckedChange = onStripChange
                )
                RuleSwitchRow(
                    title = R.string.compress_fonts,
                    description = R.string.compress_fonts_desc,
                    checked = fontSubsets,
                    onCheckedChange = onFontSubsetsChange
                )
                RuleSwitchRow(
                    title = R.string.compress_gray,
                    description = R.string.compress_gray_desc,
                    checked = grayscale,
                    onCheckedChange = onGrayscaleChange
                )
            }
        }
    }
}

@Composable
private fun RuleText(@StringRes title: Int, @StringRes description: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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

@Composable
private fun RuleSwitchRow(
    @StringRes title: Int,
    @StringRes description: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RuleText(title = title, description = description, modifier = Modifier.weight(1f))
        RuleSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DpiSegmented(selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceTint)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DpiChoices.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .then(if (isSelected) Modifier.shadow(1.dp, RoundedCornerShape(4.dp)) else Modifier)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) AppColors.Primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelected(value) })
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.compress_dpi_value, value),
                    style = AppTextStyle.Caption11Medium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) Color.White else AppColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RuleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 24.dp)
            .clip(CircleShape)
            .background(if (checked) AppColors.Primary else AppColors.SurfaceTrack)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
    }
}

/* ----------------------------- Destination file ----------------------------- */

@Composable
private fun DestinationField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.compress_destination).uppercase(Locale.getDefault()),
            style = AppTextStyle.Caption11Medium.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.55.sp
            ),
            color = AppColors.TextSecondary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.InsertDriveFile, null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = AppTextStyle.Label12Regular.copy(
                    fontWeight = FontWeight.Medium,
                    color = AppColors.TextPrimary
                ),
                cursorBrush = SolidColor(AppColors.Primary),
                modifier = Modifier.weight(1f).focusRequester(focusRequester)
            )
            IconButton(onClick = { focusRequester.requestFocus() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_output_name),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/* ----------------------------- Actions ----------------------------- */

@Composable
private fun ActionRow(
    savedMb: Float,
    onPreview: () -> Unit,
    onCompress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceTint)
                .clickable(onClick = onPreview)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Visibility, null, tint = AppColors.TextPrimary, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.compress_preview),
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
                .background(AppColors.PrimaryButton)
                .clickable(onClick = onCompress)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Archive, null, tint = Color.White, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.compress_action, savedMb.mb()),
                style = AppTextStyle.Button14SemiBold.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
        }
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1100)
@Composable
private fun CompressPdfScreenPreview() {
    PdfToolkitTheme { CompressPdfScreen(contentPadding = PaddingValues(0.dp)) }
}