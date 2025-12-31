@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.crop

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.app.theme.AppColors
import java.io.File

/**
 * Manual crop editor screen with actual image display
 */
@Composable
fun CropEditorScreen(
    imageFile: File?,
    onClose: () -> Unit,
    onConfirm: (Offset, Offset, Offset, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    
    // Load bitmap from file with proper rotation
    val bitmap = remember(imageFile) {
        imageFile?.let { 
            val bmp = BitmapFactory.decodeFile(it.absolutePath)
            val rotation = com.docscan.app.util.FileUtils.getImageRotation(it)
            if (rotation != 0) {
                com.docscan.app.util.FileUtils.rotateBitmap(bmp, rotation)
            } else {
                bmp
            }
        }
    }
    
    // Crop corners state (normalized 0-1 coordinates)
    val corners = remember {
        mutableStateOf(
            QuadCorners(
                topLeft = Offset(0.1f, 0.1f),
                topRight = Offset(0.9f, 0.1f),
                bottomLeft = Offset(0.1f, 0.9f),
                bottomRight = Offset(0.9f, 0.9f)
            )
        )
    }
    
    var draggedCorner: CornerType? by remember { mutableStateOf(null) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "Crop Document",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel"
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
            // Image display with crop overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Scanned Document",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Crop overlay with draggable corners
                        CropOverlay(
                            corners = corners.value,
                            onCornerDrag = { cornerType, offset ->
                                draggedCorner = cornerType
                                corners.value = corners.value.adjustCorner(cornerType, offset)
                            },
                            onDragEnd = {
                                draggedCorner = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No image captured",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Instructions and button
            Text(
                text = "Drag corners to adjust crop area",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            )
            
            // Next button
            Button(
                onClick = {
                    onConfirm(
                        corners.value.topLeft,
                        corners.value.topRight,
                        corners.value.bottomLeft,
                        corners.value.bottomRight
                    )
                },
                enabled = bitmap != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Next",
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

data class QuadCorners(
    val topLeft: Offset,
    val topRight: Offset,
    val bottomLeft: Offset,
    val bottomRight: Offset
) {
    fun adjustCorner(cornerType: CornerType, newOffset: Offset): QuadCorners {
        return when (cornerType) {
            CornerType.TopLeft -> copy(topLeft = newOffset)
            CornerType.TopRight -> copy(topRight = newOffset)
            CornerType.BottomLeft -> copy(bottomLeft = newOffset)
            CornerType.BottomRight -> copy(bottomRight = newOffset)
        }
    }
}

enum class CornerType {
    TopLeft, TopRight, BottomLeft, BottomRight
}

@Composable
fun CropOverlay(
    corners: QuadCorners,
    onCornerDrag: (CornerType, Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(Size(0f, 0f)) }
    var activeCorner by remember { mutableStateOf<CornerType?>(null) }
    
    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    canvasSize = Size(
                        coordinates.size.width.toFloat(),
                        coordinates.size.height.toFloat()
                    )
                }
        ) {
            if (canvasSize.width == 0f || canvasSize.height == 0f) return@Canvas
            
            val topLeftPx = Offset(
                corners.topLeft.x * size.width,
                corners.topLeft.y * size.height
            )
            val topRightPx = Offset(
                corners.topRight.x * size.width,
                corners.topRight.y * size.height
            )
            val bottomLeftPx = Offset(
                corners.bottomLeft.x * size.width,
                corners.bottomLeft.y * size.height
            )
            val bottomRightPx = Offset(
                corners.bottomRight.x * size.width,
                corners.bottomRight.y * size.height
            )
            
            val path = Path().apply {
                moveTo(topLeftPx.x, topLeftPx.y)
                lineTo(topRightPx.x, topRightPx.y)
                lineTo(bottomRightPx.x, bottomRightPx.y)
                lineTo(bottomLeftPx.x, bottomLeftPx.y)
                close()
            }
            
            // Draw crop area border (no overlay to keep image bright)
            drawPath(
                path = path,
                color = Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
            )
            
            // Draw corner handles - larger and more visible
            val cornerRadius = 24.dp.toPx()
            val activeRadius = 28.dp.toPx()
            
            // Draw white circles with blue border
            listOf(
                topLeftPx to (activeCorner == CornerType.TopLeft),
                topRightPx to (activeCorner == CornerType.TopRight),
                bottomLeftPx to (activeCorner == CornerType.BottomLeft),
                bottomRightPx to (activeCorner == CornerType.BottomRight)
            ).forEach { (center, isActive) ->
                val radius = if (isActive) activeRadius else cornerRadius
                // White fill
                drawCircle(
                    color = Color.White,
                    radius = radius,
                    center = center
                )
                // Blue border
                drawCircle(
                    color = AppColors.ScannerCorner,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var currentCorner: CornerType? = null
                    
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                currentCorner = findNearestCorner(offset, corners, canvasSize, threshold = 150f)
                                activeCorner = currentCorner
                            }
                        },
                        onDragEnd = {
                            currentCorner = null
                            activeCorner = null
                            onDragEnd()
                        },
                        onDragCancel = {
                            currentCorner = null
                            activeCorner = null
                            onDragEnd()
                        }
                    ) { change, _ ->
                        if (canvasSize.width > 0 && canvasSize.height > 0 && currentCorner != null) {
                            change.consume()
                            val newOffset = Offset(
                                (change.position.x / canvasSize.width).coerceIn(0.0f, 1.0f),
                                (change.position.y / canvasSize.height).coerceIn(0.0f, 1.0f)
                            )
                            onCornerDrag(currentCorner!!, newOffset)
                        }
                    }
                }
        )
    }
}

fun findNearestCorner(
    touchPoint: Offset,
    corners: QuadCorners,
    size: Size,
    threshold: Float = 100f
): CornerType? {
    val touchX = touchPoint.x / size.width
    val touchY = touchPoint.y / size.height
    
    val distances = listOf(
        CornerType.TopLeft to kotlin.math.abs(corners.topLeft.x - touchX) + kotlin.math.abs(corners.topLeft.y - touchY),
        CornerType.TopRight to kotlin.math.abs(corners.topRight.x - touchX) + kotlin.math.abs(corners.topRight.y - touchY),
        CornerType.BottomLeft to kotlin.math.abs(corners.bottomLeft.x - touchX) + kotlin.math.abs(corners.bottomLeft.y - touchY),
        CornerType.BottomRight to kotlin.math.abs(corners.bottomRight.x - touchX) + kotlin.math.abs(corners.bottomRight.y - touchY)
    )
    
    val nearest = distances.minByOrNull { it.second }
    return if (nearest != null && nearest.second * size.width < threshold) {
        nearest.first
    } else {
        null
    }
}
