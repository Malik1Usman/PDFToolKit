package com.example.pdftoolkit.ui.theme

import androidx.compose.ui.graphics.Color

/** Saare colors Figma design tokens se liye gaye hain. */
object AppColors {
    // Backgrounds / surfaces
    val Background = Color(0xFFF8F9FF)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceTint = Color(0xFFEFF4FF)        // search bar (files), privacy badge
    val SurfaceSoft = Color(0xFFE5EEFF)        // chips, thumbnails
    val SurfaceSofter = Color(0xFFDCE9FF)      // tool icon bg
    val SurfaceTrack = Color(0xFFD3E4FE)       // storage bar last segment

    // Brand
    val Primary = Color(0xFF004AC6)
    val PrimaryButton = Color(0xFF2563EB)
    val PrimaryTint = Color(0x1A004AC6)        // rgba(0,74,198,0.10)
    val PrimaryTint20 = Color(0x332563EB)      // rgba(37,99,235,0.20)
    val HeroGradientStart = Color(0x262563EB)  // rgba(37,99,235,0.15)
    val HeroGlow = Color(0x14004AC6)
    val IndigoSoft = Color(0xFFDBE1FF)
    val IndigoSoft60 = Color(0x99DBE1FF)
    val OnIndigo = Color(0xFF003EA8)

    // Teal (secondary)
    val Teal = Color(0xFF006A61)
    val TealTint = Color(0x1A006A61)
    val TealLight = Color(0xFF86F2E4)
    val TealLight20 = Color(0x3386F2E4)        // rgba(134,242,228,0.2)
    val TealLight40 = Color(0x6686F2E4)        // rgba(134,242,228,0.4)
    val TealLight50 = Color(0x8086F2E4)        // rgba(134,242,228,0.5)
    val TealOnLight = Color(0xFF006F66)

    // Warm / error
    val Peach = Color(0xFFFFDDB8)
    val Peach60 = Color(0x99FFDDB8)            // rgba(255,221,184,0.6)
    val OnPeach = Color(0xFF784B00)
    val ErrorSoft = Color(0xFFFFDAD6)
    val ErrorSoft20 = Color(0x33FFDAD6)        // rgba(255,218,214,0.2)
    val Error = Color(0xFFBA1A1A)
    val OnErrorContainer = Color(0xFF93000A)
    val Star = Color(0xFFF5A623)

    // Text
    val TextPrimary = Color(0xFF0B1C30)
    val TextSecondary = Color(0xFF434655)
    val TextSecondary80 = Color(0xCC434655)    // rgba(67,70,85,0.8)
    val TextHint = Color(0xFF737686)

    // Chrome
    val TopBarBg = Color(0xE6F8F9FF)           // rgba(248,249,255,0.9)
    val BottomBarBg = Color(0xF2FFFFFF)        // rgba(255,255,255,0.95)
    val ShadowSoft = Color(0x0A000000)
    val ShadowNav = Color(0x0F0F172A)
    val FabShadow = Color(0x592563EB)

    // Scanner
    val ViewfinderBg = Color(0xFF213145)
    val ScanCyan = Color(0xFF89F5E7)
    val ScanGlow = Color(0x73006A61)           // rgba(0,106,97,0.45)
    val ScanOverlay60 = Color(0x99213145)
    val ScanOverlay70 = Color(0xB3213145)
    val ScanOverlay85 = Color(0xD9213145)
    val ScanDivider = Color(0x4DC3C6D7)        // rgba(195,198,215,0.3)
    val ShutterGlow = Color(0x33004AC6)        // rgba(0,74,198,0.2)

    // Settings
    val ProOnBadge = Color(0xFF00174B)
    val ProGlow = Color(0x66DBE1FF)            // rgba(219,225,255,0.4)
    val PrivacyCardBg = Color(0x4D89F5E7)      // rgba(137,245,231,0.3)
    val ErrorSoft40 = Color(0x66FFDAD6)        // rgba(255,218,214,0.4)
    val SwitchOff = Color(0xFF737686)

    // Fine-tune
    val GuideDashed = Color(0x40004AC6)        // rgba(0,74,198,0.25)
    val GuideText = Color(0x66004AC6)          // rgba(0,74,198,0.4)
    val WhiteOverlay80 = Color(0xCCFFFFFF)
    val WhiteOverlay20 = Color(0x33FFFFFF)

    // Batch review
    val ScanOverlay80 = Color(0xCC213145)
    val ScanOverlay75 = Color(0xBF213145)
    val OverlayChipText = Color(0xFFEAF1FF)
    val BadgeCountText = Color(0xFFEEEFFF)

    // Compress
    val GaugeReclaimed = Color(0xB36BD8CB)     // rgba(107,216,203,0.7)
}