@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.docscan.app.ui.review

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docscan.app.model.CornerType
import com.docscan.app.model.EnhanceMode
import com.docscan.app.model.QuadCorners
import com.docscan.app.model.ScannedPage
import com.docscan.app.ui.crop.CropOverlay
import com.docscan.app.util.FileUtils
import com.docscan.app.util.ImageProcessor
import com.docscan.app.viewmodel.ScanViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Multi-page Document Review & Editor Screen.
 * Allows swiping through all pages, dragging corners to crop each page, rotating,
 * rearranging, and applying filters to individual, selected, or all pages in one click.
 */
@Composable
fun DocumentReviewScreen(
    viewModel: ScanViewModel,
    onBackToCamera: () -> Unit,
    onProceedToExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = viewModel.scannedPages.value
    val pageCount = pages.size

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { pageCount }
    )

    var showFilterDialog by remember { mutableStateOf(false) }
    var isProcessingExport by remember { mutableStateOf(false) }
    var pageToDelete by remember { mutableStateOf<Int?>(null) }

    // Gallery Picker for adding more pages
    val galleryAddLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val files = withContext(Dispatchers.IO) {
                    val list = mutableListOf<File>()
                    uris.forEach { uri ->
                        try {
                            val input = context.contentResolver.openInputStream(uri)
                            if (input != null) {
                                val file = FileUtils.createImageFile(context)
                                input.use { inp -> file.outputStream().use { out -> inp.copyTo(out) } }
                                if (file.exists() && file.length() > 0) list.add(file)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    list
                }
                files.forEach { viewModel.addScannedPage(it) }
            }
        }
    }

    if (pages.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No pages scanned",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onBackToCamera) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Camera")
                }
            }
        }
        return
    }

    val currentPageIndex = pagerState.currentPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    val currentPage = pages[currentPageIndex]

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Page ${currentPageIndex + 1} of $pageCount",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackToCamera) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Camera")
                    }
                },
                actions = {
                    // Filter / Magic Wand Button
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Apply Filters",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    // Delete Page Button
                    IconButton(onClick = { pageToDelete = currentPageIndex }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Page",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    // Next / Export Button
                    TextButton(
                        onClick = {
                            isProcessingExport = true
                            scope.launch {
                                viewModel.processAllPagesForExport(context)
                                isProcessingExport = false
                                onProceedToExport()
                            }
                        },
                        enabled = !isProcessingExport
                    ) {
                        Text(
                            text = "Export",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    // Page action tools row (Rotate, Reset Full Crop, Previous, Next)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rotate button
                        TextButton(onClick = { viewModel.rotatePage(currentPage.id) }) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rotate 90°", fontSize = 13.sp)
                        }

                        // Full / Reset Crop button
                        TextButton(onClick = { viewModel.resetPageCrop(currentPage.id) }) {
                            Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Full Page", fontSize = 13.sp)
                        }

                        // Filter Indicator badge
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { showFilterDialog = true }
                        ) {
                            Text(
                                text = currentPage.filter.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Thumbnail Gallery Strip (Inside App)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(pages) { index, page ->
                            val isSelected = index == currentPageIndex
                            Box(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                            ) {
                                AsyncImage(
                                    model = page.originalFile,
                                    contentDescription = "Page ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.7f),
                                    shape = RoundedCornerShape(bottomStart = 6.dp),
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        // Add Page button at end of strip
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .size(width = 54.dp, height = 70.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { galleryAddLauncher.launch("image/*") }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add page",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(text = "Add", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // Primary Export Action Button
                    Button(
                        onClick = {
                            isProcessingExport = true
                            scope.launch {
                                viewModel.processAllPagesForExport(context)
                                isProcessingExport = false
                                onProceedToExport()
                            }
                        },
                        enabled = !isProcessingExport,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .height(50.dp)
                    ) {
                        if (isProcessingExport) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Processing $pageCount pages...")
                        } else {
                            Text("Continue to Export ($pageCount pages)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Horizontal Pager for Pages (Swipe left / right to review & crop)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { pageIdx ->
                val page = pages[pageIdx]
                PageEditorItem(
                    page = page,
                    onCornersChanged = { newCorners ->
                        viewModel.updatePageCorners(page.id, newCorners)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            }
        }
    }

    // Filter Dialog (supports applying to ALL or SELECTED pages)
    if (showFilterDialog) {
        FilterSelectorDialog(
            totalPages = pageCount,
            currentPageIndex = currentPageIndex,
            currentFilter = currentPage.filter,
            onDismiss = { showFilterDialog = false },
            onApply = { selectedFilter, applyToAll, selectedIndices ->
                showFilterDialog = false
                if (applyToAll) {
                    viewModel.applyFilterToAllPages(selectedFilter)
                    Toast.makeText(context, "Applied ${selectedFilter.displayName} to all $pageCount pages", Toast.LENGTH_SHORT).show()
                } else if (selectedIndices.isNotEmpty()) {
                    viewModel.applyFilterToSelectedPages(selectedIndices, selectedFilter)
                    Toast.makeText(context, "Applied ${selectedFilter.displayName} to ${selectedIndices.size} pages", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.applyFilterToPage(currentPageIndex, selectedFilter)
                    Toast.makeText(context, "Applied ${selectedFilter.displayName} to Page ${currentPageIndex + 1}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Delete confirmation dialog
    if (pageToDelete != null) {
        val targetIdx = pageToDelete!!
        AlertDialog(
            onDismissRequest = { pageToDelete = null },
            title = { Text("Delete Page ${targetIdx + 1}") },
            text = { Text("Are you sure you want to remove this page from your scan?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pageToDelete = null
                        viewModel.deletePage(targetIdx)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Single page editor item inside HorizontalPager.
 * Displays the page with interactive crop overlay directly on the canvas.
 */
@Composable
fun PageEditorItem(
    page: ScannedPage,
    onCornersChanged: (QuadCorners) -> Unit,
    modifier: Modifier = Modifier
) {
    // Load bitmap safely with rotation applied
    val bitmap = remember(page.originalFile, page.rotation) {
        val bmp = FileUtils.decodeSampledBitmap(page.originalFile.absolutePath, 1600, 1600)
        if (bmp != null && page.rotation != 0) {
            FileUtils.rotateBitmap(bmp, page.rotation, recycleSource = true)
        } else {
            bmp
        }
    }

    val aspectRatio = remember(bitmap) {
        if (bitmap != null && bitmap.height > 0) {
            (bitmap.width.toFloat() / bitmap.height.toFloat()).coerceIn(0.4f, 2.5f)
        } else {
            0.75f
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspectRatio)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Page Photo",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive quad crop overlay on top of the image
                    CropOverlay(
                        corners = page.corners,
                        onCornerDrag = { cornerType, offset ->
                            onCornersChanged(page.corners.adjustCorner(cornerType, offset))
                        },
                        onDragEnd = { },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            CircularProgressIndicator()
        }
    }
}

/**
 * Filter Selection Dialog with "Apply to All Pages" or "Apply to Selected Pages"
 */
@Composable
fun FilterSelectorDialog(
    totalPages: Int,
    currentPageIndex: Int,
    currentFilter: EnhanceMode,
    onDismiss: () -> Unit,
    onApply: (selectedFilter: EnhanceMode, applyToAll: Boolean, selectedIndices: Set<Int>) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(currentFilter) }
    var applyScope by remember { mutableStateOf("all") } // "all", "current", "custom"
    val selectedPages = remember { mutableStateOf(mutableSetOf(currentPageIndex)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enhancement Filter", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Filter mode chips
                Text("Select Style:", style = MaterialTheme.typography.labelMedium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(EnhanceMode.values().size) { idx ->
                        val mode = EnhanceMode.values()[idx]
                        val isSelected = selectedFilter == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = mode },
                            label = { Text(mode.displayName, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider()

                // Target Pages selector
                Text("Apply to:", style = MaterialTheme.typography.labelMedium)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyScope = "all" }
                ) {
                    RadioButton(
                        selected = applyScope == "all",
                        onClick = { applyScope = "all" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("All $totalPages pages (Recommended)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { applyScope = "current" }
                ) {
                    RadioButton(
                        selected = applyScope == "current",
                        onClick = { applyScope = "current" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Current page only (Page ${currentPageIndex + 1})", style = MaterialTheme.typography.bodyMedium)
                }

                if (totalPages > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { applyScope = "custom" }
                    ) {
                        RadioButton(
                            selected = applyScope == "custom",
                            onClick = { applyScope = "custom" }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select specific pages", style = MaterialTheme.typography.bodyMedium)
                    }

                    if (applyScope == "custom") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(totalPages) { pageIdx ->
                                val isChecked = selectedPages.value.contains(pageIdx)
                                FilterChip(
                                    selected = isChecked,
                                    onClick = {
                                        val newSet = selectedPages.value.toMutableSet()
                                        if (isChecked) newSet.remove(pageIdx) else newSet.add(pageIdx)
                                        selectedPages.value = newSet
                                    },
                                    label = { Text("P${pageIdx + 1}", fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        selectedFilter,
                        applyScope == "all",
                        if (applyScope == "custom") selectedPages.value else setOf(currentPageIndex)
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
