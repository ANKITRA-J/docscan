@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.scanner

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.docscan.app.util.FileUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Scanner preview screen with CameraX integration and torch control
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onImageCaptured: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current

    var flashEnabled by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

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
                onCaptureError = {
                    isCapturing = false
                    Toast.makeText(context, "Capture failed, please try again", Toast.LENGTH_SHORT).show()
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

        // Bottom capture button
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
                        .size(76.dp)
                        .shadow(elevation = 8.dp, shape = CircleShape)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(32.dp),
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
                        onCaptureError()
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
