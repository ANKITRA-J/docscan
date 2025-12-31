@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.export

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.docscan.app.R
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import com.docscan.app.util.PdfGenerator
import com.docscan.app.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    
    val finalBitmap = viewModel.getFinalBitmap()
    val allPages = viewModel.getAllPages()
    val pageCount = allPages.size
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Export Document",
                    style = MaterialTheme.typography.headlineMedium,
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
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(elevation = 4.dp, shape = RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (finalBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = finalBitmap.asImageBitmap(),
                            contentDescription = "Document Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Document Preview",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Choose export format",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
            )
            
            if (pageCount > 0) {
                Text(
                    text = "$pageCount page${if (pageCount > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExportOptionCard(
                    title = stringResource(R.string.save_pdf),
                    description = if (pageCount > 1) "Export $pageCount pages as PDF" else "Export as PDF document",
                    icon = Icons.Default.PictureAsPdf,
                    onClick = {
                        if (allPages.isNotEmpty() && !isExporting) {
                            isExporting = true
                            scope.launch {
                                val pdfFile = withContext(Dispatchers.IO) {
                                    PdfGenerator.createPdfFromBitmaps(context, allPages)
                                }
                                
                                isExporting = false
                                
                                if (pdfFile != null) {
                                    // Create thumbnail from first page
                                    val thumbnail = ImageProcessor.createThumbnail(allPages.first())
                                    val thumbnailFile = FileUtils.createImageFile(context)
                                    FileUtils.saveBitmap(thumbnail, thumbnailFile)
                                    
                                    val timeStamp = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
                                    viewModel.createDocument("Scan $timeStamp", thumbnailFile.absolutePath)
                                    
                                    Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
                                    
                                    // Share PDF
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
                                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                                    
                                    // Reset for next scan
                                    viewModel.reset()
                                    onExportComplete()
                                } else {
                                    Toast.makeText(context, "Failed to create PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = finalBitmap != null && !isExporting,
                    modifier = Modifier.fillMaxWidth()
                )
                
                ExportOptionCard(
                    title = stringResource(R.string.save_image),
                    description = "Export as JPEG image",
                    icon = Icons.Default.Image,
                    onClick = {
                        if (finalBitmap != null && !isExporting) {
                            isExporting = true
                            scope.launch {
                                val imageFile = withContext(Dispatchers.IO) {
                                    PdfGenerator.saveImageAsJpeg(context, finalBitmap)
                                }
                                
                                isExporting = false
                                
                                if (imageFile != null) {
                                    // Create thumbnail and save document
                                    val thumbnail = ImageProcessor.createThumbnail(finalBitmap)
                                    val thumbnailFile = FileUtils.createImageFile(context)
                                    FileUtils.saveBitmap(thumbnail, thumbnailFile)
                                    
                                    val timeStamp = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date())
                                    viewModel.createDocument("Scan $timeStamp", thumbnailFile.absolutePath)
                                    
                                    Toast.makeText(context, "Image saved successfully", Toast.LENGTH_SHORT).show()
                                    
                                    // Share image
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        imageFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
                                    
                                    // Reset for next scan
                                    viewModel.reset()
                                    onExportComplete()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    enabled = finalBitmap != null && !isExporting,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (isExporting) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Exporting...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
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
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
