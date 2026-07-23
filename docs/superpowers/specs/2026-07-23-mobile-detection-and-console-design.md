# Mobile Detection and Console Design

## Goal

Replace the empty detector asset with a working general-purpose on-device object detector, reserve model selection for future power-defect YOLO models, enlarge the phone live preview, and redesign the browser-based PC console in a light visual system.

## Model strategy

- Bundle SSD MobileNet V1 trained on COCO as the single working general detector. It has TensorFlow Lite metadata and is compatible with the existing `ObjectDetector` API.
- Treat SSD MobileNet V1 as the default model and show its load state, FPS, latency, confidence threshold, and detected-object count.
- Keep one visible import entry for a custom power-defect YOLO `.tflite` model. The imported file is validated before it replaces the bundled detector.
- If a custom YOLO import fails, keep SSD MobileNet V1 active and surface the compatibility error.
- A future YOLO adapter owns its output decoding, non-maximum suppression, and conversion to the existing normalized `DetectedDefect` type so camera UI, database records, and the web stream remain unchanged.

## Mobile live inspection UX

- Make the camera preview the visual priority in portrait orientation using the available vertical space and a stable 16:9 presentation area.
- Keep detection boxes, source status, FPS, and latency as overlays on the preview.
- Compress the summary and controls below the preview so they do not compete with live video.
- Preserve the existing camera permission flow, CameraX analysis pipeline, alarm control, and detection toggle.

## PC web console UX

- Use a light palette: off-white page background, white panels, blue primary action color, slate text, and semantic green/orange/red statuses.
- Keep the live video as the dominant desktop panel; organize telemetry as compact cards below it.
- Keep the live-detection review list and remote alarm command in a separate, scrollable side panel.
- Maintain responsive one-column behavior below the desktop breakpoint.
- Preserve the existing HTTP routes and polling contract: `/api/status`, `/api/records`, `/api/stream`, `/api/review`, and `/api/command`.

## Error handling

- A model-load error is surfaced in the model selection UI and detection stays disabled for that model.
- Camera preview remains available when a model cannot load.
- Model slots without a bundled asset cannot be activated.

## Verification

- Verify the downloaded model is non-empty and is included in the Android assets.
- Build the debug APK after Kotlin and Compose changes.
- Exercise the model screen state logic with unit tests where it does not require Android camera hardware.
- Verify the generated browser dashboard is valid through its existing HTTP server route and retains every API action.

## Out of scope

- Training or fine-tuning a power-defect model.
- Shipping any additional pre-trained detector binary beyond SSD MobileNet V1.
- Replacing the current TensorFlow Lite Task Library with a YOLO-specific runtime.
