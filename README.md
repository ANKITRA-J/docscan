# Document Scanner App

A modern, Apple-style Document Scanner app built with Jetpack Compose for Android.

## Features

- **Home/Recent Scans Screen**: Grid view of recently scanned documents
- **Scanner Preview Screen**: Camera preview with document detection frame
- **Manual Crop Editor**: Drag corners to adjust crop area
- **Enhance Modes UI**: Apply different enhancement filters (Original, Grayscale, Color, Black & White, Magic)
- **Export Screen**: Save documents as PDF or images

## Design Philosophy

The app follows Apple's design principles with:
- Clean, minimal UI
- Smooth animations and transitions
- Modern Material 3 theming
- Responsive layouts that adapt to different screen sizes
- Polished visual details

## Project Structure

```
app/src/main/java/com/docscan/app/
├── MainActivity.kt              # Main entry point
├── navigation/
│   ├── Screen.kt               # Navigation routes/sealed classes
│   └── NavGraph.kt             # Navigation graph setup
├── theme/
│   ├── Color.kt                # Color palette
│   ├── Type.kt                 # Typography system
│   └── Theme.kt                # Material 3 theme configuration
├── model/
│   └── ScanDocument.kt         # Data models
└── ui/
    ├── home/
    │   └── HomeScreen.kt       # Recent scans grid
    ├── scanner/
    │   └── ScannerScreen.kt    # Camera preview
    ├── crop/
    │   └── CropEditorScreen.kt # Manual crop editor
    ├── enhance/
    │   └── EnhanceModesScreen.kt # Enhancement filters
    └── export/
        └── ExportScreen.kt     # Export options
```

## Integration Points

The code includes TODO comments marking where to integrate:

1. **Camera Functionality** (`ScannerScreen.kt`):
   - Add CameraX dependencies
   - Integrate `PreviewView` for camera preview
   - Handle camera permissions
   - Implement image capture

2. **Image Processing** (`CropEditorScreen.kt`):
   - Accept captured image bitmap
   - Implement perspective transform for crop
   - Apply crop transformation

3. **Enhancement Filters** (`EnhanceModesScreen.kt`):
   - Apply image filters (grayscale, color correction, etc.)
   - Update preview in real-time

4. **PDF Export** (`ExportScreen.kt`):
   - Use iText or similar library for PDF generation
   - Handle file permissions (Android 13+)
   - Show export progress

5. **Data Persistence**:
   - Add Room database for storing document metadata
   - Implement file storage for images
   - Load documents on app start

## Dependencies

- Jetpack Compose (UI framework)
- Navigation Compose (navigation)
- Material 3 (design system)
- Coil (image loading)
- CameraX (camera - commented out, ready to enable)
- iText (PDF generation - commented out, ready to enable)

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on an Android device or emulator (API 24+)

## License

This is a template/prototype implementation. Add appropriate license as needed.

