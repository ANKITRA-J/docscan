package com.docscan.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docscan.app.ui.crop.CropEditorScreen
import com.docscan.app.ui.enhance.EnhanceModesScreen
import com.docscan.app.ui.export.ExportScreen
import com.docscan.app.ui.home.HomeScreen
import com.docscan.app.ui.scanner.ScannerScreen
import com.docscan.app.viewmodel.ScanViewModel

/**
 * Navigation graph for the Document Scanner app
 * Defines all routes and navigation between screens
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    viewModel: ScanViewModel,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Home / Recent Scans screen
        composable(Screen.Home.route) {
            HomeScreen(
                documents = viewModel.documents.value,
                onScanClick = {
                    viewModel.reset()
                    navController.navigate(Screen.Scanner.route)
                },
                onGalleryImagesSelected = { files: List<java.io.File> ->
                    viewModel.reset()
                    if (files.isNotEmpty()) {
                        // Process first image
                        viewModel.setCapturedImage(files[0])
                        navController.navigate(Screen.CropEditor.route)
                        
                        // TODO: Queue remaining images for processing
                    }
                },
                onDocumentClick = { document ->
                    navController.navigate(Screen.Export.createRoute(document.id))
                }
            )
        }
        
        // Scanner preview screen
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onClose = {
                    navController.popBackStack()
                },
                onImageCaptured = { file ->
                    viewModel.setCapturedImage(file)
                    navController.navigate(Screen.CropEditor.route)
                }
            )
        }
        
        // Manual crop editor screen
        composable(Screen.CropEditor.route) {
            CropEditorScreen(
                imageFile = viewModel.capturedImageFile.value,
                onClose = {
                    navController.popBackStack()
                },
                onConfirm = { topLeft, topRight, bottomLeft, bottomRight ->
                    viewModel.applyCrop(topLeft, topRight, bottomLeft, bottomRight)
                    navController.navigate(Screen.EnhanceModes.route)
                }
            )
        }
        
        // Enhance modes UI screen
        composable(Screen.EnhanceModes.route) {
            EnhanceModesScreen(
                enhancedBitmap = viewModel.enhancedBitmap.value,
                currentMode = viewModel.currentEnhanceMode.value,
                pageCount = viewModel.getPageCount(),
                onModeSelected = { mode ->
                    viewModel.applyEnhancement(mode)
                },
                onClose = {
                    navController.popBackStack()
                },
                onScanMore = {
                    // Save current page
                    viewModel.addPageToCurrentDocument()
                    viewModel.resetCurrentPage()
                    // Go back to scanner
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onConfirm = {
                    // Save current page
                    viewModel.addPageToCurrentDocument()
                    // Navigate to export
                    val docId = "temp_${System.currentTimeMillis()}"
                    navController.navigate(Screen.Export.createRoute(docId))
                }
            )
        }
        
        // Export screen
        composable(
            route = Screen.Export.route,
            arguments = listOf(navArgument("documentId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val documentId = backStackEntry.arguments?.getString("documentId") ?: ""
            ExportScreen(
                documentId = documentId,
                viewModel = viewModel,
                onClose = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onExportComplete = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}

