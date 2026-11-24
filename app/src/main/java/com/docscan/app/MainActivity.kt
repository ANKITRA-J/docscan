package com.docscan.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.docscan.app.navigation.NavGraph
import com.docscan.app.navigation.Screen
import com.docscan.app.theme.DocumentScannerTheme
import com.docscan.app.viewmodel.ScanViewModel
import java.io.File

/**
 * Main Activity for Document Scanner app.
 * Handles app launch, navigation, and external PDF viewing intents (Open With DocScan).
 */
class MainActivity : ComponentActivity() {

    private val scanViewModel: ScanViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handlePdfIntent(intent)

        setContent {
            DocumentScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(scanViewModel.openedPdfFile.value) {
                        if (scanViewModel.openedPdfFile.value != null) {
                            navController.navigate(Screen.PdfViewer.route)
                        }
                    }

                    NavGraph(
                        navController = navController,
                        viewModel = scanViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePdfIntent(intent)
    }

    private fun handlePdfIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val isViewOrSend = action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND
        val isPdf = intent.type?.equals("application/pdf", ignoreCase = true) == true ||
                intent.data?.toString()?.endsWith(".pdf", ignoreCase = true) == true

        if (isViewOrSend || isPdf) {
            val uri = intent.data ?: intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                try {
                    val file = copyUriToCache(uri)
                    if (file != null) {
                        scanViewModel.setOpenedPdf(file)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File? {
        return try {
            if (uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val srcFile = File(path)
                    if (srcFile.exists()) {
                        val destFile = File(cacheDir, srcFile.name)
                        srcFile.copyTo(destFile, overwrite = true)
                        return destFile
                    }
                }
            }
            val input = contentResolver.openInputStream(uri) ?: return null
            var name = "Document.pdf"
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val displayName = it.getString(idx)
                        if (!displayName.isNullOrBlank()) name = displayName
                    }
                }
            }
            val destFile = File(cacheDir, name)
            input.use { inp -> destFile.outputStream().use { out -> inp.copyTo(out) } }
            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
