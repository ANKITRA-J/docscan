package com.docscan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.docscan.app.model.ScanDocument
import com.docscan.app.navigation.NavGraph
import com.docscan.app.theme.DocumentScannerTheme

/**
 * Main Activity for Document Scanner app
 * Sets up navigation and theme
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // TODO: Load documents from database/storage
        val documents = mutableListOf<ScanDocument>()
        
        setContent {
            DocumentScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavGraph(
                        navController = navController,
                        documents = documents,
                        onDocumentClick = { document ->
                            // Navigate to export screen for existing document
                            navController.navigate(
                                com.docscan.app.navigation.Screen.Export.createRoute(document.id)
                            )
                        }
                    )
                }
            }
        }
    }
}

