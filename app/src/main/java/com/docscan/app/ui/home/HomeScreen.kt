package com.docscan.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.docscan.app.R
import com.docscan.app.model.ScanDocument
import com.docscan.app.navigation.Screen
import com.docscan.app.theme.AppColors

/**
 * Home / Recent Scans screen
 * Displays a grid of recently scanned documents with a floating action button to scan new documents
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    documents: List<ScanDocument>,
    onScanClick: () -> Unit,
    onDocumentClick: (ScanDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.recent_scans),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScanClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .size(56.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(28.dp)
            )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Scan Document",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        if (documents.isEmpty()) {
            EmptyState(
                onScanClick = onScanClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp)
            )
        } else {
            // Calculate columns based on screen width (2 columns for phones, 3+ for tablets)
            val columns = if (screenWidth < 600.dp) 2 else 3
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(
                    horizontal = 16.dp,
                    vertical = 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(documents) { document ->
                    DocumentCard(
                        document = document,
                        onClick = { onDocumentClick(document) },
                        modifier = Modifier
                            .aspectRatio(0.7f) // Portrait aspect ratio
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }
        }
    }
}

/**
 * Document card displaying thumbnail and basic info
 */
@Composable
fun DocumentCard(
    document: ScanDocument,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Gray.copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            ) {
                if (document.thumbnailPath != null) {
                    AsyncImage(
                        model = document.thumbnailPath,
                        contentDescription = document.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📄",
                            fontSize = 48.sp
                        )
                    }
                }
            }
            
            // Document info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = document.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDate(document.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Empty state when no documents are scanned
 */
@Composable
fun EmptyState(
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = "📄",
            fontSize = 80.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.no_scans),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tap_to_scan),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onScanClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(50.dp)
                .widthIn(min = 200.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.scan_button),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * Format date for display
 * TODO: Use proper date formatting library
 */
fun formatDate(date: java.util.Date): String {
    // Placeholder - implement proper date formatting
    return "Today" // Replace with actual date formatting
}

