# 2 GB emulator verification — version 1.3

Test date: 2026-09-25. Functional checks passed, but five-model rendering did not achieve approximately 30 FPS on this emulator. Fullscreen approached 30 FPS on average with uneven frame delivery. These results do not establish performance on a physical 2–3 GB phone.

## Environment and build

| Item | Configuration |
|---|---|
| AVD | `2GB_PHONE`, serial `emulator-5554` |
| OS | Android 16 / API 36, x86_64 |
| RAM | Configured 2048 MiB; guest MemTotal 2,019,272 kB |
| CPUs | 2 virtual CPUs |
| Display | 1080 × 2400, density 420 |
| Graphics | Host GPU mode, NVIDIA GeForce GT 710 through Android Emulator OpenGL ES Translator |
| Functional test build | Debug 1.3 with Android instrumentation |
| Performance build | Optimized release 1.3 with x86_64 added using a temporary Gradle init script |

The normal ARM submission APK is unchanged. Its SHA-256 remains `62f34c211dd06f2c3e4135aa78f01cd63c02a55e9bda3df25354a43bb919391d`.
The separate emulator test APK SHA-256 is `a32024d867a10a66fc14457e0813e17db1025374bc5c589847ccbc0a116b5a8d`.

## Functional results

Both `WorkspaceIntegrationTest` tests passed in 49.546 seconds. Coverage includes loading all five bundled models, move/resize, separate orbit/zoom, labels projected from model transforms, fullscreen enabling interaction, restoration disabling interaction, background/resume and Activity recreation defaults, resource cleanup, idle rendering and workspace serialization. These assertions ran against the debug build; release received separate UI, performance and memory checks.

The release build displayed all five actual models. The Help popup was visually checked: the icons and descriptions are visible. Screenshots are retained with the evidence.

## Release frame measurements

| Scenario | Presented frames / 6 seconds | Average presentation FPS | Median interval | 95th-percentile interval |
|---|---:|---:|---:|---:|
| Five models visible, orbit Microscope, run 1 | 51 | 8.50 | 106.97 ms | 288.30 ms |
| Five models visible, orbit Microscope, run 2 | 95 | 15.83 | 38.22 ms | 209.14 ms |
| Five models visible, all labels on, orbit Microscope | 94 | 15.67 | 40.92 ms | 170.21 ms |
| Microscope fullscreen, labels on | 177 | 29.50 | 33.23 ms | 63.05 ms |

**Five-model smoothness: failed on this configuration.** Fullscreen's average is approximately 30 FPS, but the tail intervals show stutter. Do not describe this as consistently smooth 30 FPS.

Each capture is a 12-second Perfetto trace containing an 8-second continuous ADB swipe. Analysis uses the middle six seconds of that gesture, excluding gesture start and end. FrameTimeline app-surface tokens are joined to actual display-frame presentation timestamps; dropped app frames are excluded. These are application-window presentation rates, not isolated Filament GPU timings. Models are static when idle, so idle FPS would be misleading for this demand-driven renderer.

An initial fullscreen swipe started at the display edge and exited fullscreen, consistent with Android's Back gesture. That invalid capture was replaced with a swipe inset 120 pixels; the corrected run verifies fullscreen is still active afterward. A first stress-automation attempt selected an obscured duplicate-model control; the corrected test chooses the topmost card and verifies every count transition. Neither automation incident is reported as an app crash.

Results vary with emulator translation, host GPU/CPU load, caching and warm-up. The two grid runs differ considerably; labels-on versus labels-off is not a controlled comparison. Debug UI timings captured while a host build was running are retained as functional diagnostics and excluded from this release table.

## Release memory and repeated load/close test

Twenty additional Bulb add/close cycles completed with the original five models retained. Every cycle verified the count changed from five to six and back to five. The app PID stayed unchanged; the captured crash buffer contains no app crash.

| Checkpoint | PSS (MiB) | Swap PSS (MiB) | PSS + Swap PSS (MiB) |
|---|---:|---:|---:|
| Before cycles | 124.12 | 31.55 | 155.67 |
| After 5 | 124.56 | 31.41 | 155.97 |
| After 10 | 124.52 | 32.49 | 157.01 |
| After 15 | 124.74 | 36.98 | 161.72 |
| After 20 | 125.05 | 36.98 | 162.03 |
| After 8-second settling period | 121.06 | 36.98 | 158.04 |

The first five-model release snapshot used 120.32 MiB PSS plus 32.73 MiB swap PSS. The cycle baseline is later, after profiling and an automation setup attempt, so comparisons above use that baseline. After settling, PSS plus swap PSS increased by approximately 2.37 MiB across the test. There was no large retained-memory growth or process restart in this short run. This is not a proof of zero leaks: allocator caches, garbage collection and swapping affect these numbers, and host GPU allocations are not fully represented by guest process PSS. No forced garbage collection was used.

## Profiling interpretation and next steps

The grid traces put much of the app's scheduled CPU time in `FEngine::loop` and Android `RenderThread`, rather than the main thread. For example, grid run 1 used approximately 3.51 CPU-seconds in Filament's engine thread and 2.00 CPU-seconds in RenderThread during the six-second window. This points toward rendering/compositing work as an investigation priority; it does not prove a particular GPU or shader bottleneck.

The current shared renderer, demand-driven rendering and covered-card culling are documented in README. Fullscreen shows the benefit of reducing visible views, although its different viewport size means this is not an isolated experiment. Next experiments should measure reduced render resolution and model/texture complexity, changing one variable at a time, then repeat the same gestures. Confirm final performance on a physical 2–3 GB phone. A 2 GB emulator constrains guest memory but does not reproduce a low-end mobile GPU.

## Evidence and reproduction

Local evidence is under `artifacts/verification/emulator-2gb/`: environment, test output, screenshots, memory dumps, the x86 test APK, Perfetto traces, gesture intervals, extracted presentation timestamps, analysis SQL and `frame-results.json`. This artifact directory is excluded from source control.

The copied Python scripts use `adb -s emulator-5554` and assume execution from the repository root. `model_studio_emulator_check.py` initializes an empty workspace; the original profiling script creates the grid scenarios, and `model-studio-fullscreen-retest.py` supplies the corrected fullscreen gesture. The analysis script expects Perfetto trace processor at `/tmp/model-studio-trace-processor` (v58.2 used here). The stress script checks six models after each addition and five after each removal.
