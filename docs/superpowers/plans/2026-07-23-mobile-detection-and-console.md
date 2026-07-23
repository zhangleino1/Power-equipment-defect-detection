# Mobile Detection and Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a working COCO detector, a future-ready model selector, a larger phone preview, and a light PC dashboard.

**Architecture:** Keep the existing CameraX → `DefectDetectorEngine` → `DetectedDefect` flow. Add model catalog metadata to `ModelArchitecture`, expose detector load state through `MainUiState`, and leave future adapters behind the same normalized detection contract. The embedded NanoHTTPD routes stay unchanged while their generated HTML/CSS is redesigned.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, CameraX, TensorFlow Lite Task Vision, NanoHTTPD, JUnit, Gradle.

## Global Constraints

- SSD MobileNet V1 is the only bundled detector.
- A custom power-defect YOLO is available through a local `.tflite` import entry.
- Camera preview remains usable if model initialization fails.
- Existing HTTP API routes and payloads remain compatible.
- Preserve unrelated worktree changes.

---

### Task 1: Model catalog and load state

**Files:**
- Modify: `app/src/main/java/com/example/tflite/DefectDetectorEngine.kt`
- Modify: `app/src/main/java/com/example/ui/MainViewModel.kt`
- Create: `app/src/test/java/com/example/tflite/ModelArchitectureTest.kt`
- Add asset: `app/src/main/assets/mobilenet_ssd.tflite`

**Interfaces:**
- Produces: `ModelArchitecture.assetFileName: String?`, `ModelArchitecture.isBundled: Boolean`, `DefectDetectorEngine.loadError: String?`, `DefectDetectorEngine.isReady: Boolean`.
- Consumes: TensorFlow Lite Task Vision `ObjectDetector`.

- [ ] **Step 1: Write a failing catalog test**

```kotlin
@Test fun mobileNet_is_the_only_bundled_model() {
    assertEquals("mobilenet_ssd.tflite", ModelArchitecture.MOBILENET_SSD_V1.assetFileName)
    assertTrue(ModelArchitecture.MOBILENET_SSD_V1.isBundled)
    assertTrue(ModelArchitecture.entries.filterNot { it == ModelArchitecture.MOBILENET_SSD_V1 }
        .none { it.isBundled })
}
```

- [ ] **Step 2: Run the focused test and observe the missing enum/API failure**

Run: `./gradlew testDebugUnitTest --tests com.example.tflite.ModelArchitectureTest`

Expected: compilation fails because `MOBILENET_SSD_V1`, `assetFileName`, and `isBundled` do not exist.

- [ ] **Step 3: Implement catalog metadata and reload the selected detector**

```kotlin
enum class ModelArchitecture(
    val displayName: String,
    val sizeMb: String,
    val baseLatencyMs: Long,
    val assetFileName: String?,
    val family: String
) {
    MOBILENET_SSD_V1("SSD MobileNet V1 · COCO 通用识别", "4.0 MB", 30L, "mobilenet_ssd.tflite", "MobileNet"),
    CUSTOM_YOLO("自训练 YOLO 电力缺陷模型", "本地文件", 0L, null, "YOLO");

    val isBundled: Boolean get() = assetFileName != null
}
```

`DefectDetectorEngine` must rebuild `ObjectDetector` when a bundled model is selected, clear the detector for unavailable slots, and expose a localized load error without throwing.

- [ ] **Step 4: Run the focused test**

Run: `./gradlew testDebugUnitTest --tests com.example.tflite.ModelArchitectureTest`

Expected: PASS.

### Task 2: Model settings and large phone preview

**Files:**
- Modify: `app/src/main/java/com/example/ui/screens/ModelTuningScreen.kt`
- Modify: `app/src/main/java/com/example/ui/screens/LiveInspectionScreen.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`

**Interfaces:**
- Consumes: `MainUiState.modelReady`, `MainUiState.modelLoadError`, `ModelArchitecture.isBundled`.
- Produces: disabled future model slots and a compact live-screen layout.

- [ ] **Step 1: Add Compose semantics assertions for unavailable slots**

Create a focused Compose test asserting the “待导入” label is visible and unavailable model cards do not invoke `onModelSelected`.

- [ ] **Step 2: Run the Compose test and observe failure**

Run: `./gradlew connectedDebugAndroidTest`

Expected: FAIL before the unavailable-state UI exists, or document emulator unavailability and rely on compile verification.

- [ ] **Step 3: Implement the model cards and live layout**

Use an industrial telemetry aesthetic: restrained navy surfaces, cyan data accents, compact status chips, and large uninterrupted camera area. Disable clicks for models whose `isBundled` is false; show current model readiness above the list. Reduce screen padding and merge summary/control rows so the preview receives most of the vertical space.

- [ ] **Step 4: Compile the Android UI**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

### Task 3: Light PC dashboard

**Files:**
- Modify: `app/src/main/java/com/example/web/InspectionWebServer.kt`

**Interfaces:**
- Keeps: `/api/status`, `/api/records`, `/api/stream`, `/api/review`, `/api/command`.
- Produces: responsive light dashboard HTML with unchanged JavaScript polling and commands.

- [ ] **Step 1: Add an HTML contract test**

The test invokes the dashboard generator through a test-visible pure HTML builder and asserts the five API route strings, the light color token `#f4f7fb`, and the desktop two-column layout remain present.

- [ ] **Step 2: Run the test and observe failure**

Run: `./gradlew testDebugUnitTest --tests com.example.web.DashboardHtmlTest`

Expected: FAIL because the pure builder and light token do not yet exist.

- [ ] **Step 3: Extract and redesign the dashboard**

Create `buildDashboardHtml(): String` and have `serveDashboardHtml()` return it. Use off-white background, white cards, deep blue typography, semantic status colors, a dominant 16:9 video panel, compact telemetry, and a scrollable review rail. Keep polling intervals and POST payloads unchanged.

- [ ] **Step 4: Run the contract test**

Run: `./gradlew testDebugUnitTest --tests com.example.web.DashboardHtmlTest`

Expected: PASS.

### Task 4: Full verification

**Files:**
- Verify: `app/src/main/assets/mobilenet_ssd.tflite`
- Verify: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Verify model and APK sizes**

Run: `Get-Item app/src/main/assets/mobilenet_ssd.tflite, app/build/outputs/apk/debug/app-debug.apk | Select-Object Name,Length`

Expected: both lengths are greater than zero.

- [ ] **Step 2: Run all local unit tests**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build the debug APK**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.
