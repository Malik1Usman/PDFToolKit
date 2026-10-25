package com.example.pdftoolkit.ui.screens.finetunescreen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoFilter
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Tonality
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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

enum class EnhancePreset(
    @StringRes val label: Int,
    @StringRes val description: Int,
    val icon: ImageVector,
    val contrast: Float,
    val brightness: Float,
    val shadowRemoval: Float
) {
    MAGIC(R.string.fine_preset_magic, R.string.fine_preset_magic_desc, Icons.Outlined.AutoFixHigh, 35f, 12f, 75f),
    BW(R.string.fine_preset_bw, R.string.fine_preset_bw_desc, Icons.Outlined.Contrast, 60f, 20f, 85f),
    GRAYSCALE(R.string.fine_preset_gray, R.string.fine_preset_gray_desc, Icons.Outlined.FilterBAndW, 25f, 8f, 60f),
    WHITEBOARD(R.string.fine_preset_whiteboard, R.string.fine_preset_whiteboard_desc, Icons.Outlined.WbSunny, 40f, 30f, 90f),
    ORIGINAL(R.string.fine_preset_original, R.string.fine_preset_original_desc, Icons.Outlined.Image, 0f, 0f, 0f)
}

/** Ye settings scanner pipeline (OpenCV / ML Kit etc.) ko bhejna hai. */
data class EnhanceSettings(
    val preset: EnhancePreset = EnhancePreset.MAGIC,
    val contrast: Float = EnhancePreset.MAGIC.contrast,          // 0..100
    val brightness: Float = EnhancePreset.MAGIC.brightness,      // 0..100
    val shadowRemoval: Float = EnhancePreset.MAGIC.shadowRemoval, // 0..100
    val colorBalance: Float = 0f,                                // -50..50
    val autoDeskew: Boolean = true,
    val eraseFingers: Boolean = true,
    val deepInk: Boolean = true
) {
    companion object {
        fun forPreset(preset: EnhancePreset, keep: EnhanceSettings? = null) = EnhanceSettings(
            preset = preset,
            contrast = preset.contrast,
            brightness = preset.brightness,
            shadowRemoval = preset.shadowRemoval,
            colorBalance = 0f,
            autoDeskew = keep?.autoDeskew ?: true,
            eraseFingers = keep?.eraseFingers ?: true,
            deepInk = keep?.deepInk ?: true
        )
    }
}

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

/**
 * "Enhance & Filter" screen. Bottom nav bar yahan hide hota hai (MainScreen).
 *
 * [preview] slot: yahan scanned page ka real bitmap dikhao. [rotation] 0/90/180/270 degrees hai.
 */
@Composable
fun FineTuneScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    fileName: String = "MSA-Doc-Signed.pdf",
    pageIndex: Int = 1,
    pageCount: Int = 2,
    shadowConfidence: Int = 94, // AI/OCR engine se aane wala confidence %
    onClose: () -> Unit = {},
    onApplyToPage: (EnhanceSettings) -> Unit = {},
    onApplyToAll: (EnhanceSettings) -> Unit = {},
    preview: @Composable BoxScope.(rotation: Float) -> Unit = { SampleDocumentPreview(it) }
) {
    var settings by remember { mutableStateOf(EnhanceSettings()) }
    var comparing by remember { mutableStateOf(false) }
    var rotation by remember { mutableFloatStateOf(0f) }

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
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ContextHeader(
                title = stringResource(R.string.fine_title),
                subtitle = stringResource(R.string.fine_subtitle_page, pageIndex, pageCount, fileName),
                comparing = comparing,
                onClose = onClose,
                onCompareToggle = { comparing = !comparing },
                onReset = {
                    settings = EnhanceSettings.forPreset(EnhancePreset.MAGIC)
                    rotation = 0f
                    comparing = false
                }
            )

            PreviewCanvas(
                settings = settings,
                comparing = comparing,
                rotation = rotation,
                shadowConfidence = shadowConfidence,
                onRotate = { rotation = (rotation + 90f) % 360f },
                preview = preview
            )

            PresetShelf(
                selected = settings.preset,
                onSelected = { settings = EnhanceSettings.forPreset(it, keep = settings) }
            )

            ManualTuning(settings = settings, onChange = { settings = it })

            SmartCorrections(settings = settings, onChange = { settings = it })
        }

        BottomActions(
            pageIndex = pageIndex,
            pageCount = pageCount,
            onApplyToPage = { onApplyToPage(settings) },
            onApplyToAll = { onApplyToAll(settings) },
            modifier = Modifier
                .padding(horizontal = ScreenPadding)
                .padding(top = 4.dp, bottom = contentPadding.calculateBottomPadding() + 12.dp)
        )
    }
}

/* ----------------------------- Contextual header ----------------------------- */

@Composable
private fun ContextHeader(
    title: String,
    subtitle: String,
    comparing: Boolean,
    onClose: () -> Unit,
    onCompareToggle: () -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(width = 32.dp, height = 40.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.cd_fine_close),
                    tint = AppColors.TextPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTextStyle.Title16Medium,
                    color = AppColors.TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 5.dp, height = 6.dp)
                            .clip(CircleShape)
                            .background(AppColors.Teal)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = subtitle,
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (comparing) AppColors.Primary else AppColors.SurfaceSoft)
                    .clickable(onClick = onCompareToggle)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Compare,
                    null,
                    tint = if (comparing) Color.White else AppColors.TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.fine_compare),
                    style = AppTextStyle.Caption11Medium,
                    color = if (comparing) Color.White else AppColors.TextSecondary
                )
            }
            IconButton(onClick = onReset, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.RestartAlt,
                    contentDescription = stringResource(R.string.cd_fine_reset),
                    tint = AppColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/* ----------------------------- Preview canvas ----------------------------- */

@Composable
private fun PreviewCanvas(
    settings: EnhanceSettings,
    comparing: Boolean,
    rotation: Float,
    shadowConfidence: Int,
    onRotate: () -> Unit,
    preview: @Composable BoxScope.(rotation: Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(12.dp)
    ) {
        // Top meta pills
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clip(CircleShape)
                    .background(AppColors.PrimaryTint)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.AutoFixHigh, null, tint = AppColors.Primary, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (comparing) {
                        stringResource(R.string.fine_original_view)
                    } else {
                        stringResource(
                            R.string.fine_active_summary,
                            stringResource(settings.preset.label),
                            "+${settings.contrast.toInt()}%"
                        )
                    },
                    style = AppTextStyle.Caption11Medium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.275.sp
                    ),
                    color = AppColors.Primary
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.SurfaceTint)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Straighten, null, tint = AppColors.TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.fine_paper_dpi),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary
                )
            }
        }

        // Scanned sheet stage
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceTint)
        ) {
            preview(rotation)

            // Edge detection / deskew guide marks
            val dash = AppColors.GuideDashed
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(8.dp)
                    .drawBehind {
                        drawRoundRect(
                            color = dash,
                            cornerRadius = CornerRadius(8.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                            )
                        )
                    }
                    .padding(9.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.fine_ortho_align),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.GuideText
                    )
                    Text(
                        text = stringResource(R.string.fine_ortho_angle),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.GuideText
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.WhiteOverlay80)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fine_auto_crop),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.Primary
                    )
                }
            }
        }

        // Zoom & micro-view actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.TaskAlt, null, tint = AppColors.Teal, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fine_shadows_removed, shadowConfidence),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Teal
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRotate),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.RotateRight, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.fine_rotate),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary
                    )
                }
                Row(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { /* TODO: zoom / inspect */ },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.ZoomIn, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = stringResource(R.string.fine_inspect),
                        style = AppTextStyle.Caption11Medium,
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
    }
}

/** Placeholder: do kagaz + stamp. Real app mein scanned bitmap dikhao. */
@Composable
private fun BoxScope.SampleDocumentPreview(rotation: Float) {
    Box(modifier = Modifier.matchParentSize().background(Color(0xFFCFCFCF)))
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.86f)
            .rotate(rotation),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(2) { page ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFFF4F4F2), RoundedCornerShape(2.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(16) { i ->
                        Box(
                            Modifier
                                .fillMaxWidth(if (i % 5 == 4) 0.55f else 1f)
                                .height(3.dp)
                                .background(Color(0xFF9AA0AC), RoundedCornerShape(2.dp))
                        )
                    }
                }
                if (page == 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFC62828))
                    )
                }
            }
        }
    }
}

/* ----------------------------- Preset shelf ----------------------------- */

@Composable
private fun PresetShelf(selected: EnhancePreset, onSelected: (EnhancePreset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoFilter, null, tint = AppColors.Primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fine_presets_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.fine_presets_available, EnhancePreset.values().size),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.Primary
            )
        }

        LazyRow(
            modifier = Modifier.fullBleed(ScreenPadding),
            contentPadding = PaddingValues(horizontal = ScreenPadding, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(EnhancePreset.values().toList()) { preset ->
                PresetCard(
                    preset = preset,
                    selected = preset == selected,
                    onClick = { onSelected(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetCard(preset: EnhancePreset, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(112.dp)
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.Primary else AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) AppColors.WhiteOverlay20 else AppColors.SurfaceSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                preset.icon,
                null,
                tint = if (selected) Color.White else AppColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = stringResource(preset.label),
            style = AppTextStyle.Label12SemiBold,
            color = if (selected) Color.White else AppColors.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = stringResource(preset.description),
            style = AppTextStyle.Caption11Medium.copy(lineHeight = 13.75.sp),
            color = if (selected) Color.White.copy(alpha = 0.8f) else AppColors.TextSecondary,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2
        )
    }
}

/* ----------------------------- Manual fine-tuning ----------------------------- */

@Composable
private fun ManualTuning(settings: EnhanceSettings, onChange: (EnhanceSettings) -> Unit) {
    val presetDefault = settings.preset
    val shadowIsAuto = settings.shadowRemoval == presetDefault.shadowRemoval

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Tune, null, tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.fine_manual_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.TealLight40)
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.fine_ai_calibrated),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Teal
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TuneSlider(
                icon = Icons.Outlined.Tonality,
                label = R.string.fine_contrast,
                valueText = "+${settings.contrast.toInt()}%",
                valueColor = AppColors.Primary,
                value = settings.contrast,
                onValueChange = { onChange(settings.copy(contrast = it)) },
                valueRange = 0f..100f,
                minLabel = R.string.fine_contrast_min,
                maxLabel = R.string.fine_contrast_max
            )
            TuneSlider(
                icon = Icons.Outlined.WbSunny,
                label = R.string.fine_brightness,
                valueText = "+${settings.brightness.toInt()}%",
                valueColor = AppColors.Primary,
                value = settings.brightness,
                onValueChange = { onChange(settings.copy(brightness = it)) },
                valueRange = 0f..100f,
                minLabel = R.string.fine_brightness_min,
                maxLabel = R.string.fine_brightness_max
            )
            TuneSlider(
                icon = Icons.Outlined.CleaningServices,
                label = R.string.fine_shadow,
                valueText = if (shadowIsAuto) {
                    stringResource(R.string.fine_shadow_auto, settings.shadowRemoval.toInt())
                } else {
                    "${settings.shadowRemoval.toInt()}%"
                },
                valueColor = AppColors.Teal,
                value = settings.shadowRemoval,
                onValueChange = { onChange(settings.copy(shadowRemoval = it)) },
                valueRange = 0f..100f,
                minLabel = R.string.fine_shadow_min,
                maxLabel = R.string.fine_shadow_max
            )
            val color = settings.colorBalance.toInt()
            TuneSlider(
                icon = Icons.Outlined.Palette,
                label = R.string.fine_color,
                valueText = when {
                    color == 0 -> stringResource(R.string.fine_color_neutral)
                    color > 0 -> stringResource(R.string.fine_color_warm, color)
                    else -> stringResource(R.string.fine_color_cool, color)
                },
                valueColor = AppColors.TextSecondary,
                value = settings.colorBalance,
                onValueChange = { onChange(settings.copy(colorBalance = it)) },
                valueRange = -50f..50f,
                minLabel = R.string.fine_color_min,
                maxLabel = R.string.fine_color_max
            )
        }
    }
}

@Composable
private fun TuneSlider(
    icon: ImageVector,
    @StringRes label: Int,
    valueText: String,
    valueColor: Color,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    @StringRes minLabel: Int,
    @StringRes maxLabel: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = AppColors.TextSecondary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(label),
                    style = AppTextStyle.Label12Medium,
                    color = AppColors.TextPrimary
                )
            }
            Text(
                text = valueText,
                style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
                color = valueColor
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = AppColors.Primary,
                activeTrackColor = AppColors.Primary,
                inactiveTrackColor = AppColors.SurfaceSoft
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(minLabel), style = AppTextStyle.Caption11Medium, color = AppColors.TextSecondary)
            Text(text = stringResource(maxLabel), style = AppTextStyle.Caption11Medium, color = AppColors.TextSecondary)
        }
    }
}

/* ----------------------------- Smart corrections ----------------------------- */

@Composable
private fun SmartCorrections(settings: EnhanceSettings, onChange: (EnhanceSettings) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = AppColors.Teal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.fine_smart_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.fine_engine),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CorrectionRow(
                icon = Icons.Outlined.Straighten,
                title = R.string.fine_deskew_title,
                subtitle = R.string.fine_deskew_desc,
                checked = settings.autoDeskew,
                checkColor = AppColors.Primary,
                onCheckedChange = { onChange(settings.copy(autoDeskew = it)) }
            )
            CorrectionRow(
                icon = Icons.Outlined.Fingerprint,
                title = R.string.fine_fingers_title,
                subtitle = R.string.fine_fingers_desc,
                checked = settings.eraseFingers,
                checkColor = AppColors.Teal,
                onCheckedChange = { onChange(settings.copy(eraseFingers = it)) }
            )
            CorrectionRow(
                icon = Icons.Outlined.Edit,
                title = R.string.fine_ink_title,
                subtitle = R.string.fine_ink_desc,
                checked = settings.deepInk,
                checkColor = AppColors.Primary,
                onCheckedChange = { onChange(settings.copy(deepInk = it)) }
            )
        }
    }
}

@Composable
private fun CorrectionRow(
    icon: ImageVector,
    @StringRes title: Int,
    @StringRes subtitle: Int,
    checked: Boolean,
    checkColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Column {
                Text(
                    text = stringResource(title),
                    style = AppTextStyle.Label12Medium,
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(subtitle),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TextSecondary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        // Checkbox
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) checkColor else AppColors.SurfaceTrack),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/* ----------------------------- Bottom actions ----------------------------- */

@Composable
private fun BottomActions(
    pageIndex: Int,
    pageCount: Int,
    onApplyToPage: () -> Unit,
    onApplyToAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceSofter)
                .clickable(onClick = onApplyToPage)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Check, null, tint = AppColors.TextPrimary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.fine_apply_page, pageIndex),
                style = AppTextStyle.Button14SemiBold,
                color = AppColors.TextPrimary,
                maxLines = 1
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Primary)
                .clickable(onClick = onApplyToAll)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.DoneAll, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.fine_apply_all, pageCount),
                style = AppTextStyle.Button14SemiBold,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/* ----------------------------- Helper: full-bleed row ----------------------------- */

/** Parent ke horizontal padding ke bawajood row ko screen edge tak phailata hai. */
private fun Modifier.fullBleed(horizontalPadding: Dp): Modifier = this.layout { measurable, constraints ->
    val extra = horizontalPadding.roundToPx() * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.maxWidth + extra,
            maxWidth = constraints.maxWidth + extra
        )
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-horizontalPadding.roundToPx(), 0)
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, widthDp = 390, heightDp = 1500)
@Composable
private fun FineTuneScreenPreview() {
    PdfToolkitTheme { FineTuneScreen(contentPadding = PaddingValues(0.dp)) }
}