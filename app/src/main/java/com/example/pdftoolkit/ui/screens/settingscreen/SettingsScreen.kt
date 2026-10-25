package com.example.pdftoolkit.ui.screens.settingscreen

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdftoolkit.R
import com.example.pdftoolkit.ui.theme.AppColors
import com.example.pdftoolkit.ui.theme.AppTextStyle
import com.example.pdftoolkit.ui.theme.PdfToolkitTheme
import java.util.Locale

/* ----------------------------- Models ----------------------------- */

private enum class DpiOption(val dpi: Int, @StringRes val subtitle: Int) {
    STANDARD(150, R.string.settings_quality_standard),
    HIGH(300, R.string.settings_quality_high),
    ULTRA(600, R.string.settings_quality_ultra)
}

private val ScreenPadding = 16.dp

/* ----------------------------- Screen ----------------------------- */

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onUpgradeClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onChangeOutputFolder: () -> Unit = {},
    onEditFilenamePattern: () -> Unit = {},
    onClearHistory: () -> Unit = {},
    onPrivacyPolicyClick: () -> Unit = {},
    onRateClick: () -> Unit = {},
    onShareClick: () -> Unit = {}
) {
    var dpi by remember { mutableStateOf(DpiOption.HIGH) }
    var autoCompress by remember { mutableStateOf(true) }
    var keepOriginals by remember { mutableStateOf(true) }
    var cacheMb by remember { mutableStateOf(142.4f) } // TODO: real cache size

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
            .padding(horizontal = ScreenPadding)
    ) {
        ProUpgradeCard(onUpgradeClick = onUpgradeClick)

        Spacer(Modifier.height(16.dp))
        OfflinePrivacyCard()

        Spacer(Modifier.height(20.dp))
        SettingsGroup(titleRes = R.string.settings_section_general) {
            SettingsItem(
                icon = Icons.Outlined.Palette,
                title = stringResource(R.string.settings_appearance_title),
                subtitle = stringResource(R.string.settings_appearance_value),
                onClick = onAppearanceClick,
                trailing = { Chevron() }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.CreateNewFolder,
                title = stringResource(R.string.settings_output_folder),
                subtitle = stringResource(R.string.settings_output_folder_value),
                trailing = { SmallTonalTag(stringResource(R.string.settings_change), onChangeOutputFolder) }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.DriveFileRenameOutline,
                title = stringResource(R.string.settings_filename_pattern),
                subtitle = stringResource(R.string.settings_filename_pattern_value),
                subtitleMono = true,
                onClick = onEditFilenamePattern,
                trailing = {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_edit),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsGroup(titleRes = R.string.settings_section_processing) {
            SettingsItem(
                icon = Icons.Outlined.HighQuality,
                title = stringResource(R.string.settings_export_quality),
                subtitle = stringResource(dpi.subtitle),
                trailing = {
                    DpiSelector(selected = dpi, onSelected = { dpi = it })
                }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.Compress,
                title = stringResource(R.string.settings_auto_compress),
                subtitle = stringResource(R.string.settings_auto_compress_desc),
                trailing = { AppSwitch(checked = autoCompress, onCheckedChange = { autoCompress = it }) }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.FileCopy,
                title = stringResource(R.string.settings_keep_original),
                subtitle = stringResource(R.string.settings_keep_original_desc),
                trailing = { AppSwitch(checked = keepOriginals, onCheckedChange = { keepOriginals = it }) }
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsGroup(titleRes = R.string.settings_section_storage) {
            SettingsItem(
                icon = Icons.Outlined.CleaningServices,
                title = stringResource(R.string.settings_cache_title),
                subtitle = stringResource(
                    R.string.settings_cache_used,
                    String.format(Locale.getDefault(), "%.1f", cacheMb)
                ),
                trailing = {
                    ClearCacheButton(onClick = { cacheMb = 0f /* TODO: delete cache files */ })
                }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.DeleteSweep,
                iconBg = AppColors.ErrorSoft20,
                iconTint = AppColors.Error,
                title = stringResource(R.string.settings_clear_history),
                titleColor = AppColors.Error,
                subtitle = stringResource(R.string.settings_clear_history_desc),
                onClick = onClearHistory,
                trailing = { Chevron(tint = AppColors.Error) }
            )
        }

        Spacer(Modifier.height(20.dp))
        SettingsGroup(titleRes = R.string.settings_section_about) {
            SettingsItem(
                icon = Icons.Outlined.Shield,
                title = stringResource(R.string.settings_privacy_policy),
                onClick = onPrivacyPolicyClick,
                trailing = {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = stringResource(R.string.cd_open_link),
                        tint = AppColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Filled.Star,
                title = stringResource(R.string.settings_rate),
                subtitle = stringResource(R.string.settings_rate_desc),
                onClick = onRateClick,
                trailing = { StarRow() }
            )
            ItemDivider()
            SettingsItem(
                icon = Icons.Outlined.Share,
                title = stringResource(R.string.settings_share),
                onClick = onShareClick,
                trailing = { Chevron() }
            )
        }

        Spacer(Modifier.height(16.dp))
        AppInfoFooter()
    }
}

/* ----------------------------- Pro card ----------------------------- */

@Composable
private fun ProUpgradeCard(onUpgradeClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.SurfaceTint)
    ) {
        // Decorative blurred glow (top-right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(144.dp)
                .blur(20.dp)
                .background(AppColors.ProGlow, CircleShape)
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.IndigoSoft)
                        .padding(horizontal = 10.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = AppColors.ProOnBadge, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.settings_pro_badge).uppercase(Locale.getDefault()),
                        style = AppTextStyle.Caption11Medium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.275.sp
                        ),
                        color = AppColors.ProOnBadge
                    )
                }
                Text(
                    text = stringResource(R.string.settings_pro_save),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.Primary
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_pro_title),
                    style = AppTextStyle.Title18Medium.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.settings_pro_body),
                    style = AppTextStyle.Body14Regular,
                    color = AppColors.TextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.settings_pro_old_price),
                        style = AppTextStyle.Caption11Medium.copy(textDecoration = TextDecoration.LineThrough),
                        color = AppColors.TextSecondary
                    )
                    Text(
                        text = stringResource(R.string.settings_pro_price),
                        style = AppTextStyle.Title16Medium.copy(fontWeight = FontWeight.SemiBold),
                        color = AppColors.TextPrimary
                    )
                }
                Row(
                    modifier = Modifier
                        .height(44.dp)
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Primary)
                        .clickable(onClick = onUpgradeClick)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_pro_upgrade),
                        style = AppTextStyle.Button14SemiBold,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/* ----------------------------- Privacy card ----------------------------- */

@Composable
private fun OfflinePrivacyCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.PrivacyCardBg)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(AppColors.Teal),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_privacy_title),
                    style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                    color = AppColors.TextPrimary
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Teal))
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = AppTextStyle.Label12Regular.copy(lineHeight = 16.5.sp),
                color = AppColors.TextSecondary
            )
        }
    }
}

/* ----------------------------- Group + item building blocks ----------------------------- */

@Composable
private fun SettingsGroup(@StringRes titleRes: Int, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(titleRes).uppercase(Locale.getDefault()),
            style = AppTextStyle.Label12SemiBold.copy(letterSpacing = 0.6.sp),
            color = AppColors.Primary,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 12.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Surface)
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMono: Boolean = false,
    iconBg: Color = AppColors.SurfaceSoft,
    iconTint: Color = AppColors.TextSecondary,
    titleColor: Color = AppColors.TextPrimary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = AppTextStyle.Body14Medium,
                    color = titleColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = AppTextStyle.Label12Regular.let {
                            if (subtitleMono) it.copy(fontFamily = FontFamily.Monospace) else it
                        },
                        color = AppColors.TextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun ItemDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(AppColors.SurfaceSofter)
    )
}

@Composable
private fun Chevron(tint: Color = AppColors.TextSecondary) {
    Icon(Icons.Outlined.ChevronRight, null, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
private fun SmallTonalTag(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(AppColors.PrimaryTint)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = AppTextStyle.Label12SemiBold,
            color = AppColors.Primary
        )
    }
}

@Composable
private fun ClearCacheButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.SurfaceSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.settings_clear_cache),
            style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.Primary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun StarRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) {
            Icon(Icons.Filled.Star, null, tint = AppColors.OnPeach, modifier = Modifier.size(13.dp))
        }
    }
}

/* ----------------------------- DPI dropdown ----------------------------- */

@Composable
private fun DpiSelector(selected: DpiOption, onSelected: (DpiOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.settings_dpi_value, selected.dpi),
                style = AppTextStyle.Caption11Medium,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DpiOption.values().forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.settings_dpi_value, option.dpi),
                            style = AppTextStyle.Body14Regular,
                            color = AppColors.TextPrimary
                        )
                    },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/* ----------------------------- Switch (Figma M3 style) ----------------------------- */

@Composable
private fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) AppColors.Primary else Color.Gray)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Outlined.Check, null, tint = AppColors.Primary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/* ----------------------------- Footer ----------------------------- */

@Composable
private fun AppInfoFooter() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(AppColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "P",
                    style = AppTextStyle.Caption11Medium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                    color = Color.White
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = AppTextStyle.Body14SemiBold.copy(letterSpacing = 0.sp),
                color = AppColors.TextPrimary
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.TealLight)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_standalone),
                    style = AppTextStyle.Caption11Medium,
                    color = AppColors.TealOnLight
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_version),
            style = AppTextStyle.Caption11Medium,
            color = AppColors.TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.settings_crafted),
            style = AppTextStyle.Label12Regular,
            color = AppColors.TextSecondary80,
            textAlign = TextAlign.Center
        )
    }
}

/* ----------------------------- Preview ----------------------------- */

@Preview(showBackground = true, )
@Composable
private fun SettingsScreenPreview() {
    PdfToolkitTheme { SettingsScreen(contentPadding = PaddingValues(0.dp)) }
}