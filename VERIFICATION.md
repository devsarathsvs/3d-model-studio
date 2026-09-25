# Verification record

This record accompanies the current Model Studio implementation. It distinguishes static/JVM
checks, connected-device checks, and the remaining low-end acceptance test.

## Environment

- Connected hardware: Realme RMX3370, Android 13 / API 33, 1080 × 2400, density 480.
- Minimum supported Android version: API 24. API 24 hardware has not been tested in this run.
- The connected phone is not evidence of the task's required 2–3 GB device class.

## Checks

Verified on 24 September 2026:

| Check | Result |
| --- | --- |
| JVM regressions | 19 passed: pinch geometry, zoom direction, fullscreen threshold, projection, viewport coverage |
| Connected instrumentation | 2 passed in 12.482 s on RMX3370; real five-asset rendering and injected multi-pointer gestures |
| Interaction isolation | Orbit/zoom changes camera without moving/resizing the card; normal pinch changes bounds without changing camera |
| Workspace | Five visible cards after Arrange; rotate to landscape and back; bounds remain on screen |
| Labels | Embedded extras projected against actual Filament camera/world matrices after aspect change |
| Lifetime | Three add/close cycles return light count to five and destroy camera entity IDs; closing all returns model/light counts to zero |
| Idle | At most one submitted frame over a settled 1.2-second idle interval |
| Recreation | Models, order, camera and toggle state restored; malformed saved JSON rejected |
| Lint | Zero errors; three warnings: newer AGP, newer coroutines, intentional omission of ChromeOS x86 release ABI |
| Build | Optimized ARM32/ARM64 release, signed with a dedicated local key |

The final additional UI change only ellipsizes long card titles. The optimized release installed successfully on the same phone, cold-launched in 826 ms
(one `am start -W` sample), loaded all five assets through the actual picker, and displayed the
Arrange grid correctly. Labels and orbit also worked in the maximized release view.
Release PSS after the 20-second interaction capture was 302,904 KB (one snapshot, not a peak or
steady-state guarantee). Screenshots are retained locally.

The successful gesture run collected 316 Android window frames: median 22.60 ms, p95 32.46 ms,
and 13 above 33.33 ms. These include layout/rotation/asset operations and are **not a GPU FPS
benchmark**. Instrumented-process PSS was 352,666 KB with five models and 447,067 KB immediately
after rotation and three reload cycles. Those unsettled snapshots include test overhead and
allocator caches; they do not establish stable memory usage or the absence of every leak.
Native ownership assertions cover lights and camera entities explicitly. A longer settled memory
profile on target hardware remains necessary.

Raw test output, the five-card screenshot, memory measurements and profiling captures are retained
under the ignored `artifacts/verification/` directory. `layout-loop-diagnostic.perfetto-trace` is an earlier diagnostic capture;
`model-studio.perfetto-trace` captures release orbit interaction with five models loaded and
labels visible on the maximized model. Traces are retained for analysis, without claiming GPU FPS.

## Reproduce

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest
```

The integration test uses Android's instrumentation input injection for real multi-pointer
MotionEvents; root is not needed. The phone must be awake and unlocked. It loads the bundled
models, checks mode separation and zoom direction, exercises fullscreen transitions, validates
label projection after aspect changes, checks idle submissions and add/close cleanup, then reloads
saved state through Activity recreation. Test setup restores the previous saved workspace when done.

## Profiling scope

FrameMetrics measures Android window frame durations. Those figures are not a claim of complete
Filament GPU frame time or sustained 30 FPS. Rendering is demand-driven, so idle submission rate
should be near zero and must not be reported as poor FPS. Use active gestures when measuring.

A production acceptance pass should use the release APK on a real 2–3 GB device:

1. Cold-launch and add all five models, recording load latency and peak process memory.
2. Drag/resize, then orbit/zoom each model for at least 30 seconds; repeat with labels visible.
3. Capture Perfetto scheduling, CPU frequencies, frame timelines and GPU events where supported.
4. Check frame-time distribution during active interaction, not merely an average FPS counter.
5. Repeat add/close at least 20 times; compare native/graphics memory after resources settle.
6. Rotate, background/resume, recreate the Activity, and test gesture cancellation and screen edges.
7. Record device RAM, chipset, Android version, thermal conditions, build hash and trace paths.

## Submission checklist

- Install the signed APK and rehearse every requirement on the evaluation device.
- Publish the source to the candidate's GitHub repository and confirm reviewer access.
- Record a 5–10 minute narrated app/code walkthrough using README and IMPLEMENTATION as an outline.
- Explain the measurements and limitations accurately; do not substitute architecture claims for data.

## Delivered build

- File: `artifacts/Model-Studio-1.1.apk` (version 1.1 / code 2).
- SHA-256: `e85c8142c1a7a75554767fd02fffcd74db0a50a7b466599bf9299bfab529d168`.
- APK Signature Scheme v2 verified with `apksigner`; RSA 3072-bit dedicated submission key.
- Packaged ABIs: `armeabi-v7a`, `arm64-v8a`; all five GLBs are present.

## Version 1.2 icon-controls preview

- Four horizontal icon controls replace the text buttons: interact, labels, close and fullscreen.
  Each has an accessible name and a long-press tooltip; active controls are highlighted.
- Entering fullscreen via the icon, title or normal-mode resize automatically enables interaction
  and ends the previous gesture. Restoring with the icon or Back returns to move/resize mode.
  Fullscreen interactive pinches zoom; normal-mode pinch restoration remains available after
  explicitly turning interaction off.
- Debug, instrumentation and optimized release builds succeeded; all 19 JVM tests passed.
  Lint reported no errors: three toolchain/ABI warnings and three unused legacy button-text
  resources retained for this reversible UI preview.
- The instrumentation test now checks automatic interaction, fullscreen icon restoration,
  accessible names, zoom-versus-restore behavior, and fullscreen state recreation. It compiled
  successfully but has **not run on a device for version 1.2**: the USB device disconnected.
  The earlier connected-test results above belong to version 1.1.
- APK: `artifacts/Model-Studio-1.2-icons.apk`, version 1.2 / code 3.
- SHA-256: `25cd96c477111720ff96aca15a5e2aaac981a41cb87775e2f03f65f45d9396a7`.
- APK Signature Scheme v2 verified; same signing certificate as version 1.1.
- The previous APK and source ZIP remain under `artifacts/` for comparison and rollback.

## Version 1.3 Help and startup behavior

- Help uses the actual control icons beside explanations for Interact, Labels, Close, Fullscreen
  and Exit fullscreen. The content scrolls on smaller screens.
- Loading a saved workspace resets fullscreen and interaction. Returning from the background
  also restores normal cards with Interact unselected. Geometry, camera positions and labels
  remain saved. Explicit manual interaction remains available.
- Fullscreen still enables interaction automatically; leaving fullscreen disables it.
- All 19 JVM tests passed. Debug, instrumentation and signed optimized release builds succeeded.
  Lint has zero errors and three existing toolchain/ABI warnings.
- The updated instrumented test covers fresh-card defaults, background/resume and Activity
  recreation with Interact unselected. Both instrumentation tests passed on the 2 GB Android 16
  emulator on 2026-09-25. The Help dialog was visually checked on its release build.
- APK: `artifacts/Model-Studio-1.3.apk`, version 1.3 / code 4.
- SHA-256: `62f34c211dd06f2c3e4135aa78f01cd63c02a55e9bda3df25354a43bb919391d`. APK v2 signature verified with the existing release key.

## Version 1.3 — 2 GB emulator performance

See [the emulator report](docs/EMULATOR_2GB_REPORT.md) for configuration, evidence and methodology.
An optimized x86_64 release measured 8.50–15.83 presentation FPS with five models visible,
and 29.50 FPS in fullscreen with labels. Five-model smoothness does not pass on this emulator.
A physical 2–3 GB device test remains required. The ARM submission APK was not changed.
Twenty verified release add/close cycles also completed without process restart. Settled
PSS plus swap PSS increased by about 2.37 MiB; this short check does not establish absence of leaks.

## Immersive fullscreen update — 2026-09-25

- Fullscreen now hides the app header/footer and loading-strip space, expands the shared render
  surface, and hides status/navigation bars. Swiping the edge temporarily reveals system bars.
  Display cutouts retain safe padding for the model controls.
- Exit fullscreen, Back, and closing the fullscreen model restore the app controls and system
  bars. Interaction remains automatic on entry and disabled on exit.
- All 19 unit tests and all 3 emulator instrumentation tests passed (35.904 seconds for the
  instrumentation suite). New coverage checks full-height geometry, original card restoration,
  rotation while fullscreen, Back, exit icon, and closing the last fullscreen model.
- Debug build and lint passed; lint has zero errors and the three existing warnings.
- Updated debug build installed on `emulator-5554`. Evidence is in
  `artifacts/verification/immersive-fullscreen/`. Previous release APKs and performance numbers
  describe the earlier build; no new performance claim is made for this UI update.

## Latest release — physical Realme FPS measurement, 2026-09-25

The latest R8-optimized release was installed on the Realme RMX3370 / Android 13 / 8 GB RAM,
using the existing development signing identity to preserve app data. Two five-model rotation
runs measured 60 FPS each. Rotation with all labels enabled and fullscreen rotation with labels
also measured 60 FPS; five-model card dragging with labels measured 58.50 FPS. These are short
app-window presentation measurements, not pinch-zoom or sustained thermal benchmarks.
See [the physical-device report](docs/REALME_RMX3370_PERFORMANCE.md) for method and evidence.
