@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.export

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import com.docscan.app.util.PdfGenerator
import com.docscan.app.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportScreen(
    documentId: String,
    viewModel: ScanViewModel,
    onClose: () -> Unit,
    onExportComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }
    var exportStatusText by remember { mutableStateOf("") }

    // Load document if opening an existing one
    LaunchedEffect(documentId) {
        if (documentId.isNotBlank() && !documentId.startsWith("temp_")) {
            viewModel.loadDocument(documentId)
        }
    }

    val pagePaths = viewModel.getPagePaths()
    val finalBitmap = viewModel.getFinalBitmap()
    val pageCount = if (pagePaths.isNotEmpty()) pagePaths.size else if (finalBitmap != null) 1 else 0

    val timeStamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }
    var docTitle by remember(viewModel.currentDocumentTitle.value) {
        val initial = viewModel.currentDocumentTitle.value
        mutableStateOf(if (initial.isNotBlank()) initial else "Scan_$timeStamp")
    }

    var selectedPageFormat by remember { mutableStateOf(PdfGenerator.PageFormat.A4) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Export Document",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Document Preview Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .background(Color.Gray.copy(alpha = 0.08f))
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (finalBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = finalBitmap.asImageBitmap(),
                            contentDescription = "Document Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else if (pagePaths.isNotEmpty()) {
                        // Load preview of first page
                        var firstBmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(pagePaths) {
                            withContext(Dispatchers.IO) {
                                firstBmp = FileUtils.decodeSampledBitmap(pagePaths.first(), 800, 800)
                            }
                        }
                        if (firstBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = firstBmp!!.asImageBitmap(),
                                contentDescription = "Document Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            Text(
                                text = "📄 $pageCount Pages",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "No document pages found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Document Title Edit Field
            OutlinedTextField(
                value = docTitle,
                onValueChange = {
                    docTitle = it
                    if (viewModel.currentDocumentId.value != null) {
                        viewModel.updateDocumentName(viewModel.currentDocumentId.value!!, it)
                    }
                },
                label = { Text("Document Name") },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp)
            )

            // Page count & format selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$pageCount page${if (pageCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )

                // Page format chips
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        PdfGenerator.PageFormat.A4 to "A4",
                        PdfGenerator.PageFormat.LETTER to "Letter",
                        PdfGenerator.PageFormat.FIT_IMAGE to "Fit"
                    ).forEach { (format, label) ->
                        FilterChip(
                            selected = selectedPageFormat == format,
                            onClick = { selectedPageFormat = format },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Export Options
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Save to Downloads (PDF)
                ExportOptionCard(
                    title = "Save PDF to Downloads",
                    description = "Save formatted PDF ($pageCount pages) to device Downloads folder",
                    icon = Icons.Default.Download,
                    enabled = pageCount > 0 && !isExporting,
                    onClick = {
                        isExporting = true
                        exportStatusText = "Generating PDF..."
                        scope.launch {
                            val cleanName = docTitle.ifBlank { "Scan_$timeStamp" }
                            val pdfFile = withContext(Dispatchers.IO) {
                                if (pagePaths.isNotEmpty()) {
                                    PdfGenerator.createPdfFromPagePaths(
                                        context,
                                        pagePaths,
                                        cleanName,
                                        selectedPageFormat
                                    )
                                } else if (finalBitmap != null) {
                                    PdfGenerator.createPdfFromBitmaps(
                                        context,
                                        listOf(finalBitmap),
                                        cleanName,
                                        selectedPageFormat
                                    )
                                } else {
                                    null
                                }
                            }

                            if (pdfFile != null) {
                                exportStatusText = "Saving to Downloads..."
                                val savedUri = withContext(Dispatchers.IO) {
                                    FileUtils.saveToDownloads(
                                        context,
                                        pdfFile,
                                        pdfFile.name,
                                        "application/pdf"
                                    )
                                }

                                // Create thumbnail and persist document
                                val thumbBmp = finalBitmap ?: pagePaths.firstOrNull()?.let {
                                    FileUtils.decodeSampledBitmap(it, 300, 300)
                                }
                                val thumbFile = FileUtils.createImageFile(context)
                                if (thumbBmp != null) {
                                    FileUtils.saveBitmap(ImageProcessor.createThumbnail(thumbBmp), thumbFile)
                                }
                                viewModel.createDocument(cleanName, thumbFile.absolutePath)

                                isExporting = false
                                if (savedUri != null) {
                                    Toast.makeText(
                                        context,
                                        "Saved to Downloads: ${pdfFile.name}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "PDF created: ${pdfFile.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                viewModel.reset()
                                onExportComplete()
                            } else {
                                isExporting = false
                                Toast.makeText(context, "Failed to generate PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                // 2. Share PDF
                ExportOptionCard(
                    title = "Share PDF",
                    description = "Send PDF via WhatsApp, Email, Drive or other apps",
                    icon = Icons.Default.Share,
                    enabled = pageCount > 0 && !isExporting,
                    onClick = {
                        isExporting = true
                        exportStatusText = "Preparing PDF for sharing..."
                        scope.launch {
                            val cleanName = docTitle.ifBlank { "Scan_$timeStamp" }
                            val pdfFile = withContext(Dispatchers.IO) {
                                if (pagePaths.isNotEmpty()) {
                                    PdfGenerator.createPdfFromPagePaths(
                                        context,
                                        pagePaths,
                                        cleanName,
                                        selectedPageFormat
                                    )
                                } else if (finalBitmap != null) {
                                    PdfGenerator.createPdfFromBitmaps(
                                        context,
                                        listOf(finalBitmap),
                                        cleanName,
                                        selectedPageFormat
                                    )
                                } else {
                                    null
                                }
                            }

                            isExporting = false
                            if (pdfFile != null) {
                                val thumbBmp = finalBitmap ?: pagePaths.firstOrNull()?.let {
                                    FileUtils.decodeSampledBitmap(it, 300, 300)
                                }
                                val thumbFile = FileUtils.createImageFile(context)
                                if (thumbBmp != null) {
                                    FileUtils.saveBitmap(ImageProcessor.createThumbnail(thumbBmp), thumbFile)
                                }
                                viewModel.createDocument(cleanName, thumbFile.absolutePath)

                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    pdfFile
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
                                viewModel.reset()
                                onExportComplete()
                            } else {
                                Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                // 3. Save as Image
                ExportOptionCard(
                    title = "Save Image",
                    description = if (pageCount > 1) "Save all $pageCount pages as JPEG images" else "Save as JPEG image",
                    icon = Icons.Default.Image,
                    enabled = pageCount > 0 && !isExporting,
                    onClick = {
                        isExporting = true
                        exportStatusText = "Saving images..."
                        scope.launch {
                            val cleanName = docTitle.ifBlank { "Scan_$timeStamp" }
                            var savedCount = 0

                            withContext(Dispatchers.IO) {
                                if (pagePaths.isNotEmpty()) {
                                    pagePaths.forEachIndexed { idx, p ->
                                        val srcFile = File(p)
                                        val pageName = "${cleanName}_page_${idx + 1}.jpg"
                                        val uri = FileUtils.saveToDownloads(context, srcFile, pageName, "image/jpeg")
                                        if (uri != null) savedCount++
                                    }
                                } else if (finalBitmap != null) {
                                    val imgFile = PdfGenerator.saveImageAsJpeg(context, finalBitmap, cleanName)
                                    if (imgFile != null) {
                                        val uri = FileUtils.saveToDownloads(context, imgFile, imgFile.name, "image/jpeg")
                                        if (uri != null) savedCount++
                                    }
                                }
                            }

                            // Create thumbnail and persist document
                            val thumbBmp = finalBitmap ?: pagePaths.firstOrNull()?.let {
                                FileUtils.decodeSampledBitmap(it, 300, 300)
                            }
                            val thumbFile = FileUtils.createImageFile(context)
                            if (thumbBmp != null) {
                                FileUtils.saveBitmap(ImageProcessor.createThumbnail(thumbBmp), thumbFile)
                            }
                            viewModel.createDocument(cleanName, thumbFile.absolutePath)

                            isExporting = false
                            if (savedCount > 0) {
                                Toast.makeText(
                                    context,
                                    "Saved $savedCount image${if (savedCount > 1) "s" else ""} to Downloads",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.reset()
                            onExportComplete()
                        }
                    }
                )
            }

            // Exporting progress indicator
            if (isExporting) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = exportStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ExportOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
