package com.docscan.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docscan.app.ui.export.ExportScreen
import com.docscan.app.ui.home.HomeScreen
import com.docscan.app.ui.review.DocumentReviewScreen
import com.docscan.app.ui.scanner.ScannerScreen
import com.docscan.app.viewmodel.ScanViewModel
import java.io.File

/**
 * Navigation graph for the Document Scanner app
 * Streamlined flow:
 * 1. Home -> Continuous Camera Scanner (click unlimited photos) or Gallery Import
 * 2. Document Review Screen -> Swipe & crop each page, rotate, rearrange, batch filter
 * 3. Export Screen -> Save to Downloads as formatted PDF or JPEG images, share
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
                        viewModel.setScannedPages(files)
                        navController.navigate(Screen.Review.route)
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

        // Continuous Multi-Page Camera Scanner
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onClose = {
                    navController.popBackStack()
                },
                onFinishCapture = { files ->
                    if (files.isNotEmpty()) {
                        viewModel.setScannedPages(files)
                        navController.navigate(Screen.Review.route) {
                            popUpTo(Screen.Home.route)
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        // Multi-Page Review & Editor Studio
        composable(Screen.Review.route) {
            DocumentReviewScreen(
                viewModel = viewModel,
                onBackToCamera = {
                    navController.navigate(Screen.Scanner.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onProceedToExport = {
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
