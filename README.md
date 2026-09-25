# Model Studio — 3D Model Viewer

A single-Activity Kotlin Android app using Android Views, with no Fragments. It bundles the
five supplied GLB models and works offline. Minimum Android version: **7.0 / API 24**.

Tap **Add model** to load a model. Each card has separate controls for interaction, labels,
closing and fullscreen. In normal mode, drag moves the card and pinch resizes it. In interaction
mode, drag orbits the camera and pinch zooms without changing the card. Labels start hidden.
Fullscreen hides the app header/footer and system bars, and enables interaction; exiting restores
the card and disables interaction. The workspace is saved locally, with interaction off on app entry.

## 3D library and architecture

**Google Filament 1.77.0**, with **gltfio** and **Filament utilities**, handles rendering, GLB
loading and camera manipulation. It was chosen for direct access to model-node metadata, world
transforms, cameras and resource lifecycles, which this task needs for labels and independent views.

One shared engine, renderer and `TextureView` serve all cards. Each model owns its scene, camera
and viewport. `FilamentEngineHost` manages shared resources, `ModelInstance` owns per-model state,
and `ModelContainerView` handles layout and gestures. Labels read `nodes[].extras.prop`, project
node world positions through the camera, and draw 2D text with connector lines over the model.

## Performance optimizations

- Render on visual changes; stop scheduling frames while idle or in the background. Skip models
  whose cards are fully covered.
- Share the engine, render surface and material provider across models.
- Read model files and parse label metadata off the UI thread using coroutines. Use Filament's
  asynchronous resource loading for textures; keep engine calls on the main thread.
- Reuse label-projection buffers and update positions when the camera or viewport changes.
  Hidden labels do no projection work.
- Use simple directional and ambient lighting, with shadows, post-processing and anti-aliasing
  disabled. Release owned assets, cameras, lights and entity IDs when a card closes.
- Optimize release builds with R8 and package ARM32/ARM64 libraries. Debug also supports x86_64.

## Trade-offs and known limitations

Simple lighting and disabled effects reduce rendering cost but sacrifice visual quality and smooth
edges. Native asset creation is still synchronous and can delay a frame during loading. Labels
can crowd together in small cards and are not depth-occluded by geometry. The supplied Solar System
asset includes “Bronchial tree” in its metadata; the app preserves the source label text.
There is no fixed memory budget for repeated model additions, so sufficiently many copies can
exhaust memory. Dynamic resolution is not enabled.

## Devices, profiling and verification

| Environment | Checks and results |
|---|---|
| Realme RMX3370, Android 13, 8 GB RAM, Adreno 650 | Latest optimized release: **60 FPS** rotating with five models visible, **60 FPS** with all labels on, **58.50 FPS** dragging a card with labels, and **60 FPS** fullscreen with labels. Five-model rotation was repeated twice. |
| `2GB_PHONE` emulator, Android 16 / API 36, x86_64, 2 GB RAM, 2 vCPUs, 1080 × 2400 | Latest fullscreen implementation: all **3 instrumentation tests** passed, including gestures, labels, cleanup, fullscreen restoration and rotation. |
| 12 GB physical phone | Developer-reported manual testing: the app ran smoothly. Its model, Android version and FPS have not been recorded. |
| Same emulator, optimized version 1.3 release before the latest UI changes | Perfetto measured an average of **29.50 FPS** in fullscreen with labels on the emulator. Twenty verified add/close cycles completed without a crash or restart; settled PSS plus swap PSS increased by about **2.37 MiB**. |

All **19 JVM tests** passed; lint reported no errors. Performance figures measure app-window
presentations during active gestures, not idle rendering or isolated GPU time. The emulator uses
host graphics through an NVIDIA GT 710. The tested 8 GB phone achieved higher FPS in the measured
scenarios. Hardware and build differences mean this is not a prediction for every 2 GB phone.
Phone FPS figures cover short rotation/drag samples; pinch-zoom FPS and sustained thermal testing
were not measured in this run.
API 24 compatibility is configured but has not been tested on an API 24 device.

See [verification details](VERIFICATION.md) and the [2 GB emulator profiling report](docs/EMULATOR_2GB_REPORT.md)
for methodology, evidence locations and limitations. The latest measured physical-device results
are in the [Realme performance report](docs/REALME_RMX3370_PERFORMANCE.md).

## Improvements with more time

Prioritize profiling on a physical 2–3 GB phone. Measure reduced render resolution, texture budgets
and model simplification individually against the same five-model gestures. Then address loading
stalls, add a model-memory budget, improve label crowding/occlusion and expand API 24 device coverage.

## Build and run

Install Android SDK 37 and use the included Gradle wrapper. The Gradle daemon is pinned to JDK 25;
make it available or allow Gradle to provision it. Set the local Android SDK path as usual.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest  # requires an emulator or device
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

To create your own signed release, the helper requires Bash, OpenSSL and JDK `keytool`:

```bash
bash scripts/create-signing-key.sh   # creates a local key once; preserves an existing key
./gradlew assembleRelease
```

Release APK: `app/build/outputs/apk/release/app-release.apk`. Keep `.signing/` private and backed up;
it is excluded from version control. Without signing configuration, the release is unsigned.
