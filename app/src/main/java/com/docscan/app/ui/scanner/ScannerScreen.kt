@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.scanner

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class ScanMode(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DOCUMENT("Document", Icons.Default.Description),
    ID_CARD("ID Card", Icons.Default.Badge),
    BOOK("Book", Icons.Default.MenuBook)
}

enum class IdCardStep {
    FRONT,
    BACK
}

/**
 * Scanner preview screen with continuous multi-page capture mode,
 * specialized ID Card (2-in-1 Front & Back) mode, and Book (2-page split) mode.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onFinishCapture: (List<File>) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var showFlashOverlay by remember { mutableStateOf(false) }

    // Scan Mode: Document, ID Card, Book
    var currentMode by remember { mutableStateOf(ScanMode.DOCUMENT) }

    // ID Card Mode State
    var idCardStep by remember { mutableStateOf(IdCardStep.FRONT) }
    var frontIdFile by remember { mutableStateOf<File?>(null) }

    // List of files captured in current continuous session
    val capturedFiles = remember { mutableStateListOf<File>() }

    // Gallery Picker within Scanner
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    if (input != null) {
                        val file = FileUtils.createImageFile(context)
                        input.use { inp ->
                            file.outputStream().use { out -> inp.copyTo(out) }
                        }
                        if (file.exists() && file.length() > 0) {
                            capturedFiles.add(file)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            CameraPreview(
                onImageCaptured = { file ->
                    scope.launch {
                        showFlashOverlay = true
                        delay(100)
                        showFlashOverlay = false

                        when (currentMode) {
                            ScanMode.DOCUMENT -> {
                                isCapturing = false
                                capturedFiles.add(file)
                            }
                            ScanMode.ID_CARD -> {
                                withContext(Dispatchers.IO) {
                                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                    val croppedCard = ImageProcessor.cropCenterCard(bmp)
                                    bmp.recycle()

                                    if (idCardStep == IdCardStep.FRONT) {
                                        val frontFile = FileUtils.createImageFile(context)
                                        FileUtils.saveBitmap(croppedCard, frontFile)
                                        croppedCard.recycle()
                                        withContext(Dispatchers.Main) {
                                            frontIdFile = frontFile
                                            idCardStep = IdCardStep.BACK
                                            isCapturing = false
                                            Toast.makeText(context, "Front captured! Now align BACK side.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Back card captured -> merge with front
                                        val frontFile = frontIdFile
                                        if (frontFile != null && frontFile.exists()) {
                                            val frontBmp = BitmapFactory.decodeFile(frontFile.absolutePath)
                                            val mergedA4 = ImageProcessor.mergeIdCard(frontBmp, croppedCard)
                                            frontBmp.recycle()
                                            croppedCard.recycle()

                                            val mergedFile = FileUtils.createImageFile(context)
                                            FileUtils.saveBitmap(mergedA4, mergedFile)
                                            mergedA4.recycle()
                                            frontFile.delete()

                                            withContext(Dispatchers.Main) {
                                                frontIdFile = null
                                                idCardStep = IdCardStep.FRONT
                                                capturedFiles.add(mergedFile)
                                                isCapturing = false
                                                Toast.makeText(context, "ID Card (2-in-1) page created!", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isCapturing = false
                                                capturedFiles.add(file)
                                            }
                                        }
                                    }
                                }
                            }
                            ScanMode.BOOK -> {
                                withContext(Dispatchers.IO) {
                                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                                    val (leftBmp, rightBmp) = ImageProcessor.splitBookPages(bmp)
                                    bmp.recycle()

                                    val page1File = FileUtils.createImageFile(context)
                                    val page2File = FileUtils.createImageFile(context)
                                    FileUtils.saveBitmap(leftBmp, page1File)
                                    FileUtils.saveBitmap(rightBmp, page2File)
                                    leftBmp.recycle()
                                    rightBmp.recycle()

                                    withContext(Dispatchers.Main) {
                                        capturedFiles.add(page1File)
                                        capturedFiles.add(page2File)
                                        isCapturing = false
                                        Toast.makeText(context, "Book scanned: 2 pages created!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                },
                onCaptureError = {
                    isCapturing = false
                    Toast.makeText(context, "Capture failed, please try again", Toast.LENGTH_SHORT).show()
                },
                flashEnabled = flashEnabled,
                isCapturing = isCapturing,
                modifier = Modifier.fillMaxSize()
            )

            // Overlays for ID Card and Book modes
            if (currentMode == ScanMode.ID_CARD) {
                IdCardOverlay(step = idCardStep)
            } else if (currentMode == ScanMode.BOOK) {
                BookOverlay()
            }
        } else {
            // Permission denied state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = "Camera permission is required to scan documents",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        // Shutter flash animation overlay
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.6f))
            )
        }

        // Top App Bar
        TopAppBar(
            title = {
                if (capturedFiles.isNotEmpty()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "${capturedFiles.size} ${if (capturedFiles.size == 1) "Page" else "Pages"} Scanned",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = {
                    if (capturedFiles.isNotEmpty()) {
                        onFinishCapture(capturedFiles)
                    } else {
                        onClose()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            },
            actions = {
                if (cameraPermissionState.status.isGranted) {
                    IconButton(onClick = { flashEnabled = !flashEnabled }) {
                        Icon(
                            imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = if (flashEnabled) "Flash On" else "Flash Off",
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Bottom Controls Area: Mode Selector & Shutter Bar
        if (cameraPermissionState.status.isGranted) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Selector Bar (Document | ID Card | Book)
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.padding(bottom = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ScanMode.values().forEach { mode ->
                            val isSelected = currentMode == mode
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.clickable {
                                    currentMode = mode
                                    if (mode != ScanMode.ID_CARD) {
                                        idCardStep = IdCardStep.FRONT
                                        frontIdFile = null
                                    }
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = mode.icon,
                                        contentDescription = mode.title,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.title,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Shutter Row: Gallery | Capture Button | Review Thumbnail
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Gallery Import Button
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Photo,
                            contentDescription = "Import from Gallery",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // Center: Shutter Button
                    Box(contentAlignment = Alignment.Center) {
                        Button(
                            onClick = { isCapturing = true },
                            enabled = !isCapturing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(76.dp)
                                .shadow(elevation = 8.dp, shape = CircleShape)
                        ) {
                            if (isCapturing) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Capture",
                                    modifier = Modifier.size(36.dp),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Right: Review / Done Button (with latest thumbnail preview)
                    if (capturedFiles.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                .clickable { onFinishCapture(capturedFiles) }
                        ) {
                            AsyncImage(
                                model = capturedFiles.last(),
                                contentDescription = "Review",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Count badge
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                            ) {
                                Text(
                                    text = "${capturedFiles.size}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(52.dp))
                    }
                }
            }
        }
    }
}

/**
 * Overlay for ID Card scanning with guide frame and current step indicator
 */
@Composable
fun IdCardOverlay(step: IdCardStep) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp, top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    text = if (step == IdCardStep.FRONT) "Align FRONT of ID Card inside frame" else "Align BACK of ID Card inside frame",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // ID Card Frame (Standard 85.6mm x 53.98mm aspect ratio ~1.586)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1.586f)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            )
        }
    }
}

/**
 * Overlay for Book mode with vertical center line splitting left and right pages
 */
@Composable
fun BookOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 120.dp, top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        // Vertical dividing line
        Box(
            modifier = Modifier
                .fillMaxHeight(0.75f)
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.8f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "PAGE 1 (Left)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "PAGE 2 (Right)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptured: (File) -> Unit,
    onCaptureError: () -> Unit,
    flashEnabled: Boolean,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val camera = remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(flashEnabled) {
        camera.value?.cameraControl?.enableTorch(flashEnabled)
    }

    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            val capture = imageCapture.value
            if (capture == null) {
                onCaptureError()
                return@LaunchedEffect
            }

            val photoFile = FileUtils.createImageFile(context)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onImageCaptured(photoFile)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        onCaptureError()
                    }
                }
            )
        }
    }

    LaunchedEffect(previewView) {
        val cameraProvider = suspendCoroutine<ProcessCameraProvider> { continuation ->
            ProcessCameraProvider.getInstance(context).also { future ->
                future.addListener({
                    continuation.resume(future.get())
                }, ContextCompat.getMainExecutor(context))
            }
        }

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        imageCapture.value = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            camera.value = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageCapture.value
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
