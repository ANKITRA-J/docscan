package com.docscan.app.ui.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.docscan.app.R
import com.docscan.app.theme.AppColors

/**
 * Scanner preview screen
 * Displays camera preview with document detection frame and capture controls
 * 
 * TODO: Integrate CameraX for actual camera preview
 * - Use androidx.camera:camera-view with PreviewView
 * - Handle camera permissions
 * - Implement image capture on button press
 * - Pass captured image to crop editor
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onClose: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    var flashEnabled by remember { mutableStateOf(false) }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val screenHeight = LocalConfiguration.current.screenHeightDp
    
    // Calculate frame dimensions (16:9 aspect ratio, centered)
    val frameWidth = (screenWidth * 0.85f).dp
    val frameAspectRatio = 16f / 9f
    val frameHeight = frameWidth / frameAspectRatio
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera preview placeholder
        // TODO: Replace with actual CameraX PreviewView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Camera Preview\n(Integrate CameraX here)",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        
        // Top bar with close button
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
        
        // Document detection frame overlay
        ScannerOverlay(
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            modifier = Modifier.fillMaxSize()
        )
        
        // Bottom controls
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 48.dp)
        ) {
            // Instructions
            Text(
                text = "Position document within frame",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Capture button
            Button(
                onClick = onCapture,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = CircleShape,
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = CircleShape
                    )
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
        
        // Draw semi-transparent overlay (four rectangles around the frame)
        // Top
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, frameTop)
        )
        // Bottom
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, frameBottom),
            size = Size(size.width, size.height - frameBottom)
        )
        // Left
        drawRect(
            color = AppColors.ScannerOverlay,
            topLeft = Offset(0f, frameTop),
            size = Size(frameLeft, frameHeightPx)
        )
        // Right
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
            style = Stroke(width = 3.dp.toPx())
        )
        
        // Draw corner indicators
        val cornerLength = 32.dp.toPx()
        val cornerWidth = 4.dp.toPx()
        
        // Top-left corner
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
        
        // Top-right corner
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
        
        // Bottom-left corner
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
        
        // Bottom-right corner
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

