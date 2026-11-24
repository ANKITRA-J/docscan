@file:OptIn(ExperimentalMaterial3Api::class)

package com.docscan.app.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.docscan.app.model.CornerType
import com.docscan.app.model.QuadCorners
import com.docscan.app.theme.AppColors
import com.docscan.app.util.FileUtils
import java.io.File
import kotlin.math.abs

/**
 * Manual crop editor screen with precise image display, rotation, and quad corner adjustments
 */
@Composable
fun CropEditorScreen(
    imageFile: File?,
    onClose: () -> Unit,
    onConfirm: (Offset, Offset, Offset, Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationDegrees by remember { mutableStateOf(0) }

    // Load bitmap safely from file with EXIF rotation handled
    val baseBitmap = remember(imageFile) {
        imageFile?.let {
            FileUtils.decodeSampledBitmap(it.absolutePath, 1920, 1920)
        }
    }

    // Rotated bitmap if user taps Rotate button
    val displayBitmap = remember(baseBitmap, rotationDegrees) {
        if (baseBitmap == null) null
        else if (rotationDegrees == 0) baseBitmap
        else FileUtils.rotateBitmap(baseBitmap, rotationDegrees, recycleSource = false)
    }

    // Crop corners state (normalized 0-1 coordinates)
    val defaultCorners = QuadCorners(
        topLeft = Offset(0.05f, 0.05f),
        topRight = Offset(0.95f, 0.05f),
        bottomLeft = Offset(0.05f, 0.95f),
        bottomRight = Offset(0.95f, 0.95f)
    )
    val corners = remember { mutableStateOf(defaultCorners) }

    val imageAspectRatio = remember(displayBitmap) {
        if (displayBitmap != null && displayBitmap.height > 0) {
            displayBitmap.width.toFloat() / displayBitmap.height.toFloat()
        } else {
            0.75f
        }
    }

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
                    style = MaterialTheme.typography.titleLarge,
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
            actions = {
                // Rotate 90 degrees
                IconButton(onClick = {
                    rotationDegrees = (rotationDegrees + 90) % 360
                    corners.value = defaultCorners
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "Rotate 90°"
                    )
                }
                // Reset / Select Full Image
                IconButton(onClick = {
                    corners.value = QuadCorners(
                        topLeft = Offset(0f, 0f),
                        topRight = Offset(1f, 0f),
                        bottomLeft = Offset(0f, 1f),
                        bottomRight = Offset(1f, 1f)
                    )
                }) {
                    Icon(
                        imageVector = Icons.Default.CropFree,
                        contentDescription = "Select Entire Image"
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
            // Image display container matching EXACT image aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (displayBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(imageAspectRatio.coerceIn(0.4f, 2.5f))
                            .background(Color.Black.copy(alpha = 0.05f))
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        androidx.compose.foundation.Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = "Scanned Document",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Crop overlay precisely aligned with image bounds
                        CropOverlay(
                            corners = corners.value,
                            onCornerDrag = { cornerType, offset ->
                                corners.value = corners.value.adjustCorner(cornerType, offset)
                            },
                            onDragEnd = { },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No image loaded",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Instructions text
            Text(
                text = "Drag the 4 corner circles to fit your document",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            )

            // Confirm & Next button
            Button(
                onClick = {
                    onConfirm(
                        corners.value.topLeft,
                        corners.value.topRight,
                        corners.value.bottomLeft,
                        corners.value.bottomRight
                    )
                },
                enabled = displayBitmap != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
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
            if (canvasSize.width <= 0f || canvasSize.height <= 0f) return@Canvas

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

            // Draw crop area border
            drawPath(
                path = path,
                color = Color.White,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )

            // Inner blue accent stroke
            drawPath(
                path = path,
                color = AppColors.ScannerCorner,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )

            // Corner handles
            val cornerRadius = 20.dp.toPx()
            val activeRadius = 26.dp.toPx()

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
                // Outer ring
                drawCircle(
                    color = AppColors.ScannerCorner,
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5.dp.toPx())
                )
                // Center dot
                drawCircle(
                    color = AppColors.ScannerCorner,
                    radius = 4.dp.toPx(),
                    center = center
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(canvasSize, corners) {
                    var currentCorner: CornerType? = null

                    detectDragGestures(
                        onDragStart = { offset ->
                            if (canvasSize.width > 0 && canvasSize.height > 0) {
                                currentCorner = findNearestCorner(offset, corners, canvasSize, threshold = 200f)
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
    threshold: Float = 150f
): CornerType? {
    val touchX = touchPoint.x / size.width
    val touchY = touchPoint.y / size.height

    val distances = listOf(
        CornerType.TopLeft to (abs(corners.topLeft.x - touchX) + abs(corners.topLeft.y - touchY)),
        CornerType.TopRight to (abs(corners.topRight.x - touchX) + abs(corners.topRight.y - touchY)),
        CornerType.BottomLeft to (abs(corners.bottomLeft.x - touchX) + abs(corners.bottomLeft.y - touchY)),
        CornerType.BottomRight to (abs(corners.bottomRight.x - touchX) + abs(corners.bottomRight.y - touchY))
    )

    val nearest = distances.minByOrNull { it.second }
    return if (nearest != null && nearest.second * size.width < threshold) {
        nearest.first
    } else {
        null
    }
}
