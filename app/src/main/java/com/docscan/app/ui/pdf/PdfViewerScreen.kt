@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.docscan.app.util.FileUtils
import com.docscan.app.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Universal PDF Viewer and Tool studio.
 * Supports viewing any PDF, removing passwords from locked PDFs,
 * encrypting with passwords, and compressing file size.
 */
@Composable
fun PdfViewerScreen(
    pdfFile: File?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (pdfFile == null || !pdfFile.exists()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No PDF file opened", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBack) { Text("Go Back") }
            }
        }
        return
    }

    // State for password and rendering
    var isEncrypted by remember { mutableStateOf(false) }
    var isUnlocked by remember { mutableStateOf(false) }
    var enteredPassword by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var unlockError by remember { mutableStateOf(false) }

    // Dialogs
    var showAddPasswordDialog by remember { mutableStateOf(false) }
    var newPasswordInput by remember { mutableStateOf("") }
    var showCompressDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }

    // Current working file (can be original or temporary decrypted)
    var currentRenderFile by remember { mutableStateOf(pdfFile) }
    var pageCount by remember { mutableStateOf(0) }

    // Check encryption on load
    LaunchedEffect(pdfFile) {
        withContext(Dispatchers.IO) {
            val enc = PdfGenerator.isPdfEncrypted(pdfFile)
            withContext(Dispatchers.Main) {
                isEncrypted = enc
                if (!enc) {
                    isUnlocked = true
                    pageCount = PdfGenerator.getPdfPageCount(pdfFile)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pdfFile.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "$pageCount ${if (pageCount == 1) "Page" else "Pages"} • ${pdfFile.length() / 1024} KB",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                currentRenderFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
                        }) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (isUnlocked && !isProcessing) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // 1. Remove Password (Unlock Permanently)
                        if (isEncrypted) {
                            OutlinedButton(
                                onClick = {
                                    isProcessing = true
                                    processingMessage = "Removing password..."
                                    scope.launch {
                                        val baseName = pdfFile.nameWithoutExtension.removeSuffix("_unlocked")
                                        val unlockedFile = File(FileUtils.getPdfDir(context), "${baseName}_unlocked.pdf")
                                        val success = withContext(Dispatchers.IO) {
                                            PdfGenerator.decryptPdf(pdfFile, unlockedFile, enteredPassword)
                                        }
                                        if (success) {
                                            withContext(Dispatchers.IO) {
                                                FileUtils.saveToDownloads(context, unlockedFile, unlockedFile.name, "application/pdf")
                                            }
                                            isProcessing = false
                                            Toast.makeText(context, "Password removed! Saved to Downloads.", Toast.LENGTH_LONG).show()
                                        } else {
                                            isProcessing = false
                                            Toast.makeText(context, "Failed to remove password", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove Password", fontSize = 12.sp)
                            }
                        } else {
                            // Add Password
                            OutlinedButton(onClick = { showAddPasswordDialog = true }) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Password", fontSize = 12.sp)
                            }
                        }

                        // 2. Compress PDF
                        Button(onClick = { showCompressDialog = true }) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compress", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF2F4F7))
        ) {
            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(processingMessage, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (!isUnlocked && isEncrypted) {
                // Password Unlock Screen
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .shadow(8.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Password Protected PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Enter password to view and edit this document",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = {
                                    passwordInput = it
                                    unlockError = false
                                },
                                label = { Text("Password") },
                                isError = unlockError,
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (unlockError) {
                                Text(
                                    text = "Incorrect password, please try again",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .align(Alignment.Start)
                                        .padding(top = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    if (passwordInput.isNotBlank()) {
                                        scope.launch {
                                            isProcessing = true
                                            processingMessage = "Verifying password..."
                                            val valid = withContext(Dispatchers.IO) {
                                                PdfGenerator.verifyPassword(pdfFile, passwordInput)
                                            }
                                            if (valid) {
                                                val tempUnenc = File.createTempFile("view_unenc_", ".pdf", context.cacheDir)
                                                withContext(Dispatchers.IO) {
                                                    PdfGenerator.decryptPdf(pdfFile, tempUnenc, passwordInput)
                                                }
                                                enteredPassword = passwordInput
                                                currentRenderFile = tempUnenc
                                                pageCount = withContext(Dispatchers.IO) {
                                                    PdfGenerator.getPdfPageCount(tempUnenc)
                                                }
                                                isUnlocked = true
                                                isProcessing = false
                                            } else {
                                                isProcessing = false
                                                unlockError = true
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Unlock & Open")
                            }
                        }
                    }
                }
            } else {
                // PDF Pages Viewer
                PdfPagesList(file = currentRenderFile, pageCount = pageCount)
            }
        }
    }

    // Add Password Dialog
    if (showAddPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showAddPasswordDialog = false },
            title = { Text("Protect with Password") },
            text = {
                Column {
                    Text("Set a password to encrypt this PDF document.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = { newPasswordInput = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPasswordInput.isNotBlank()) {
                            showAddPasswordDialog = false
                            isProcessing = true
                            processingMessage = "Encrypting PDF..."
                            scope.launch {
                                val baseName = pdfFile.nameWithoutExtension
                                val encryptedFile = File(FileUtils.getPdfDir(context), "${baseName}_protected.pdf")
                                val success = withContext(Dispatchers.IO) {
                                    PdfGenerator.encryptPdf(currentRenderFile, encryptedFile, newPasswordInput)
                                }
                                if (success) {
                                    withContext(Dispatchers.IO) {
                                        FileUtils.saveToDownloads(context, encryptedFile, encryptedFile.name, "application/pdf")
                                    }
                                    isProcessing = false
                                    Toast.makeText(context, "Encrypted PDF saved to Downloads!", Toast.LENGTH_LONG).show()
                                } else {
                                    isProcessing = false
                                    Toast.makeText(context, "Encryption failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                ) {
                    Text("Protect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPasswordDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Compression Dialog
    if (showCompressDialog) {
        AlertDialog(
            onDismissRequest = { showCompressDialog = false },
            title = { Text("Compress PDF File") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose target compression quality:", fontSize = 13.sp)
                    listOf(
                        PdfGenerator.CompressionLevel.LOW to "Small / Low (~500 KB - Best for Email/Portals)",
                        PdfGenerator.CompressionLevel.MEDIUM to "Medium (~2 MB - Balanced)",
                        PdfGenerator.CompressionLevel.ORIGINAL to "High (~100% Quality)"
                    ).forEach { (level, desc) ->
                        OutlinedButton(
                            onClick = {
                                showCompressDialog = false
                                isProcessing = true
                                processingMessage = "Compressing PDF..."
                                scope.launch {
                                    val baseName = pdfFile.nameWithoutExtension
                                    val compressedFile = File(FileUtils.getPdfDir(context), "${baseName}_compressed.pdf")
                                    val originalSize = pdfFile.length()
                                    val success = withContext(Dispatchers.IO) {
                                        PdfGenerator.compressExistingPdf(
                                            srcFile = currentRenderFile,
                                            destFile = compressedFile,
                                            level = level,
                                            password = null
                                        )
                                    }
                                    if (success) {
                                        withContext(Dispatchers.IO) {
                                            FileUtils.saveToDownloads(context, compressedFile, compressedFile.name, "application/pdf")
                                        }
                                        val newSize = compressedFile.length()
                                        isProcessing = false
                                        Toast.makeText(
                                            context,
                                            "Compressed: ${originalSize / 1024} KB → ${newSize / 1024} KB! Saved to Downloads.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        isProcessing = false
                                        Toast.makeText(context, "Compression failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(desc, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCompressDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Scrollable list of rendered PDF pages using native PdfRenderer
 */
@Composable
fun PdfPagesList(file: File, pageCount: Int) {
    if (pageCount <= 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No pages to display", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(pageCount) { index ->
            PdfPageItem(file = file, pageIndex = index)
        }
    }
}

@Composable
fun PdfPageItem(file: File, pageIndex: Int) {
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(file, pageIndex) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                if (pageIndex < renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = page.width * 2
                    val height = page.height * 2
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(AndroidColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    renderer.close()
                    pfd.close()
                    withContext(Dispatchers.Main) {
                        pageBitmap = bmp
                    }
                } else {
                    renderer.close()
                    pfd.close()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val bmp = pageBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
            Surface(
                color = Color.Black.copy(alpha = 0.05f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Page ${pageIndex + 1}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
