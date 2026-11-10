# CamCraft

**Capture. Create. Relive.**

CamCraft is an Android camera application that brings camera controls, photo browsing, and editing tools together in one app.

- **Package:** `com.samarthshukla.camcraft`
- **Platform:** Android
- **Language:** Java
- **UI:** XML Views

## Features

### Camera
- Camera preview powered by CameraX
- Front and rear camera switching
- Flash modes: Auto, On, and Off
- Pinch-to-zoom and zoom slider
- Tap-to-focus
- Exposure compensation and reset
- 3×3 composition grid
- Self-timer: Off, 3, 5, and 10 seconds
- Burst capture
- Capture flash animation

### Gallery
- Photo grid and full-screen photo viewer
- Swipe navigation
- Burst-photo grouping with shot-count badge
- Custom folder support through Android's Storage Access Framework
- Delete and share actions
- Photo details and EXIF metadata

### Photo Editor
- Crop and rotate
- Brightness, contrast, and saturation adjustments
- Nine filters: Original, Warm, Black & White, Vintage, Cool, Cinematic, Fade, Sepia, and Dramatic
- Preview edits before exporting
- EXIF orientation handling during export

### Settings and Metadata
- JPEG and PNG output formats
- High, Medium, and Low resolution options
- Optional GPS geotagging
- Custom save-folder selection
- View available GPS coordinates, altitude, and capture time
- Copy coordinates and open a location in Google Maps

## Tech Stack

- **Java** — Android application development
- **XML** — layouts and UI
- **CameraX** — camera preview and image capture
- **AndroidX ExifInterface** — image metadata
- **Google Play Services Location** — location retrieval for geotagging
- **uCrop** — image cropping

## Project Structure

The app uses an MVVM-oriented organization:

```text
com.samarthshukla.camcraft/
├── camera/
│   └── CameraController.java
├── data/
│   ├── model/
│   └── repository/
├── ui/
│   ├── camera/
│   ├── gallery/
│   └── settings/
└── MainActivity.java
```

The exact package contents may evolve as development continues.

## Getting Started

### Requirements
- Android Studio
- Android SDK configured in Android Studio
- A physical Android device or emulator with camera support

### Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/samarthshukla20/CamCraft.git
   ```
2. Open the cloned project folder in Android Studio.
3. Allow Gradle to sync and download dependencies.
4. Connect an Android device or start an emulator.
5. Run the `app` configuration.


## Permissions

Depending on the features used and Android version, CamCraft may request:

- **Camera:** required to take photos.
- **Location:** optional; used when GPS geotagging is enabled.
- **Folder access:** granted through Android's system folder picker when selecting a custom save location.

Photos can be captured without enabling geotagging.

## Photo Storage

- The default save location is `Pictures/CamCraft`.
- Users can select a custom destination folder.
- GPS metadata is added to supported JPEG images when geotagging is enabled and a location is available.
- PNG images do not use the same EXIF GPS workflow.

## Releases

See the [GitHub Releases](../../releases) page for version history and available builds.

---

**CamCraft — Capture. Create. Relive.**
