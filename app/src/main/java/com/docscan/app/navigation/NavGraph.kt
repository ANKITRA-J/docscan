package com.docscan.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.docscan.app.model.ScanDocument
import com.docscan.app.ui.crop.CropEditorScreen
import com.docscan.app.ui.enhance.EnhanceModesScreen
import com.docscan.app.ui.export.ExportScreen
import com.docscan.app.ui.home.HomeScreen
import com.docscan.app.ui.scanner.ScannerScreen

/**
 * Navigation graph for the Document Scanner app
 * Defines all routes and navigation between screens
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    documents: List<ScanDocument>,
    onDocumentClick: (ScanDocument) -> Unit,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Home / Recent Scans screen
        composable(Screen.Home.route) {
            HomeScreen(
                documents = documents,
                onScanClick = {
                    navController.navigate(Screen.Scanner.route)
                },
                onDocumentClick = { document ->
                    onDocumentClick(document)
                }
            )
        }
        
        // Scanner preview screen
        composable(Screen.Scanner.route) {
            ScannerScreen(
                onClose = {
                    navController.popBackStack()
                },
                onCapture = {
                    navController.navigate(Screen.CropEditor.route)
                }
            )
        }
        
        // Manual crop editor screen
        composable(Screen.CropEditor.route) {
            CropEditorScreen(
                imageUri = null, // TODO: Pass captured image URI
                onClose = {
                    navController.popBackStack()
                },
                onConfirm = {
                    navController.navigate(Screen.EnhanceModes.route)
                }
            )
        }
        
        // Enhance modes UI screen
        composable(Screen.EnhanceModes.route) {
            EnhanceModesScreen(
                imageUri = null, // TODO: Pass cropped image URI
                currentMode = com.docscan.app.model.EnhanceMode.Original,
                onModeSelected = { mode ->
                    // TODO: Save enhancement mode
                },
                onClose = {
                    navController.popBackStack()
                },
                onConfirm = {
                    // TODO: Create document with enhanced image
                    // For now, navigate to export with a dummy ID
                    navController.navigate(Screen.Export.createRoute("dummy_id"))
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
                imageUri = null, // TODO: Pass document image URI
                onClose = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onExportPdf = {
                    // TODO: Implement PDF export
                    // After export, navigate back to home
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onExportImage = {
                    // TODO: Implement image export
                    // After export, navigate back to home
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }
    }
}

