package com.example.pdftoolkit.ui.screens.scanscreen

import com.example.pdftoolkit.core.scan.EnhanceParams
import com.example.pdftoolkit.core.scan.ScanFilter
import com.example.pdftoolkit.ui.screens.batchreviewscreen.PageFilterTag
import com.example.pdftoolkit.ui.screens.finetunescreen.EnhancePreset
import com.example.pdftoolkit.ui.screens.finetunescreen.EnhanceSettings

/** UI enums ke naam core enums jaise hi hain (MAGIC, BW, GRAYSCALE, WHITEBOARD, ORIGINAL). */
fun EnhanceSettings.toParams() = EnhanceParams(
    filter = ScanFilter.valueOf(preset.name),
    contrast = contrast,
    brightness = brightness,
    shadowRemoval = shadowRemoval,
    colorBalance = colorBalance,
    deepInk = deepInk
)

fun EnhanceParams.toSettings() = EnhanceSettings(
    preset = EnhancePreset.valueOf(filter.name),
    contrast = contrast,
    brightness = brightness,
    shadowRemoval = shadowRemoval,
    colorBalance = colorBalance,
    deepInk = deepInk
)

fun tagFor(filter: ScanFilter): PageFilterTag = PageFilterTag.valueOf(filter.name)

fun EnhancePreset.toFilter(): ScanFilter = ScanFilter.valueOf(name)