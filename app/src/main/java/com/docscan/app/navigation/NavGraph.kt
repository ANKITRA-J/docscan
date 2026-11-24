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
import java.io.File

/**
 * Navigation graph for the Document Scanner app
 * Defines all routes and seamless multi-page transitions
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
                onGalleryImagesSelected = { files: List<File> ->
                    viewModel.reset()
                    if (files.isNotEmpty()) {
                        viewModel.setImageQueue(files)
                        navController.navigate(Screen.CropEditor.route)
                    }
                },
                onDocumentClick = { document ->
                    viewModel.loadDocument(document.id)
                    navController.navigate(Screen.Export.createRoute(document.id))
                },
                onDeleteDocument = { document ->
                    viewModel.deleteDocument(document.id)
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

        // Enhance modes screen
        composable(Screen.EnhanceModes.route) {
            EnhanceModesScreen(
                enhancedBitmap = viewModel.enhancedBitmap.value,
                currentMode = viewModel.currentEnhanceMode.value,
                pageCount = viewModel.getPageCount(),
                queueTotal = viewModel.queueTotalCount.value,
                queueIndex = viewModel.queueCurrentIndex.value,
                hasMoreInQueue = viewModel.hasMoreImagesInQueue(),
                onModeSelected = { mode ->
                    viewModel.applyEnhancement(mode)
                },
                onClose = {
                    navController.popBackStack()
                },
                onScanMore = {
                    // Commit current page to disk
                    viewModel.addPageToCurrentDocument()
                    viewModel.resetCurrentPage()
                    // Navigate to scanner for next page
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNext = {
                    // Commit current page to disk
                    viewModel.addPageToCurrentDocument()
                    // Move to next image from queue
                    val hasNext = viewModel.processNextImageFromQueue()
                    if (hasNext) {
                        navController.navigate(Screen.CropEditor.route) {
                            popUpTo(Screen.Home.route)
                        }
                    } else {
                        val docId = viewModel.ensureDocumentId()
                        navController.navigate(Screen.Export.createRoute(docId))
                    }
                },
                onConfirm = {
                    // Commit current page to disk
                    viewModel.addPageToCurrentDocument()
                    // Navigate to export
                    val docId = viewModel.ensureDocumentId()
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
                    viewModel.reset()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onExportComplete = {
                    viewModel.reset()
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}
