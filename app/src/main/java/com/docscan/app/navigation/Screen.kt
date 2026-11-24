package com.docscan.app.navigation

/**
 * Navigation destinations/sealed routes
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Review : Screen("review")
    object CropEditor : Screen("crop_editor")
    object EnhanceModes : Screen("enhance_modes")
    object Export : Screen("export/{documentId}") {
        fun createRoute(documentId: String) = "export/$documentId"
    }
}
