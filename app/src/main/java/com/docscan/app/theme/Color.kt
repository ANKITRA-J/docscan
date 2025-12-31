package com.docscan.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Color palette extracted from design
 * Apple-style clean, modern colors
 */
object AppColors {
    // Primary colors - Clean blue accent
    val Primary = Color(0xFF007AFF)
    val PrimaryDark = Color(0xFF0051D5)
    val PrimaryLight = Color(0xFF5AC8FA)
    
    // Background colors - Clean whites and grays
    val Background = Color(0xFFF5F5F7) // Soft off-white
    val Surface = Color(0xFFFFFFFF) // Pure white
    val SurfaceVariant = Color(0xFFF9F9F9) // Slightly off-white
    
    // Text colors
    val OnSurface = Color(0xFF000000) // Black
    val OnSurfaceVariant = Color(0xFF6E6E73) // Medium gray
    val OnPrimary = Color(0xFFFFFFFF) // White
    
    // Semantic colors
    val Error = Color(0xFFFF3B30)
    val Success = Color(0xFF34C759)
    val Warning = Color(0xFFFF9500)
    
    // Border and divider colors
    val Border = Color(0xFFE5E5EA)
    val Divider = Color(0xFFD1D1D6)
    
    // Card shadow colors (using with alpha)
    val Shadow = Color(0x1A000000)
    
    // Scanner overlay colors
    val ScannerOverlay = Color(0x66000000) // Semi-transparent black
    val ScannerFrame = Color(0xFFFFFFFF) // White frame
    val ScannerCorner = Color(0xFF007AFF) // Blue corners
}

