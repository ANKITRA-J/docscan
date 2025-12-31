@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.docscan.app.theme.AppColors
import com.docscan.app.util.FileUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Scanner preview screen with CameraX integration
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onImageCaptured: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    
    // Calculate frame dimensions
    val frameWidth = (screenWidth * 0.85f).dp
    val frameAspectRatio = 16f / 9f
    val frameHeight = frameWidth / frameAspectRatio
    
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
                    isCapturing = false
                    onImageCaptured(file)
                },
                flashEnabled = flashEnabled,
                isCapturing = isCapturing,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Permission denied state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Camera permission is required",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
        
        // Top bar
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            },
            actions = {
                IconButton(onClick = { flashEnabled = !flashEnabled }) {
                    Icon(
                        imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = if (flashEnabled) "Flash On" else "Flash Off",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
        
        // Bottom controls
        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { isCapturing = true },
                    enabled = !isCapturing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Capture",
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptured: (File) -> Unit,
    flashEnabled: Boolean,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }
    val camera = remember { mutableStateOf<Camera?>(null) }
    
    LaunchedEffect(flashEnabled) {
        camera.value?.cameraControl?.enableTorch(flashEnabled)
    }
    
    LaunchedEffect(isCapturing) {
        if (isCapturing) {
            val capture = imageCapture.value ?: return@LaunchedEffect
            val file = FileUtils.createImageFile(context)
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
            
            capture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        onImageCaptured(file)
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraPreview", "Image capture failed", exception)
                    }
                }
            )
        }
    }
    
    LaunchedEffect(previewView) {
        val cameraProvider = context.getCameraProvider()
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setFlashMode(if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
        
        imageCapture.value = imageCaptureBuilder.build()
        
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
            Log.e("CameraPreview", "Camera binding failed", e)
        }
    }
    
    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}

suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener({
            continuation.resume(future.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

/**
 * Scanner overlay with semi-transparent mask and detection frame
 */
@Composable
fun ScannerOverlay(
    frameWidth: androidx.compose.ui.unit.Dp,
    frameHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val frameWidthPx = frameWidth.toPx()
        val frameHeightPx = frameHeight.toPx()
        
        val frameLeft = centerX - frameWidthPx / 2
        val frameTop = centerY - frameHeightPx / 2
        val frameRight = centerX + frameWidthPx / 2
        val frameBottom = centerY + frameHeightPx / 2
        
        // Draw semi-transparent overlay
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, frameTop)
        )
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, frameBottom),
            size = Size(size.width, size.height - frameBottom)
        )
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, frameTop),
            size = Size(frameLeft, frameHeightPx)
        )
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(frameRight, frameTop),
            size = Size(size.width - frameRight, frameHeightPx)
        )
        
        // Draw frame border
        drawRect(
            color = AppColors.ScannerFrame,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(frameWidthPx, frameHeightPx),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
        )
        
        // Draw corner indicators
        val cornerLength = 32.dp.toPx()
        val cornerWidth = 4.dp.toPx()
        
        // Top-left
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(cornerLength, cornerWidth)
        )
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameLeft, frameTop),
            size = Size(cornerWidth, cornerLength)
        )
        
        // Top-right
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameRight - cornerLength, frameTop),
            size = Size(cornerLength, cornerWidth)
        )
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameRight - cornerWidth, frameTop),
            size = Size(cornerWidth, cornerLength)
        )
        
        // Bottom-left
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameLeft, frameBottom - cornerWidth),
            size = Size(cornerLength, cornerWidth)
        )
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameLeft, frameBottom - cornerLength),
            size = Size(cornerWidth, cornerLength)
        )
        
        // Bottom-right
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameRight - cornerLength, frameBottom - cornerWidth),
            size = Size(cornerLength, cornerWidth)
        )
        drawRect(
            color = AppColors.ScannerCorner,
            topLeft = Offset(frameRight - cornerWidth, frameBottom - cornerLength),
            size = Size(cornerWidth, cornerLength)
        )
    }
}

