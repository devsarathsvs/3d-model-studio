# Realme RMX3370 performance report

Measured 25 September 2026 on the connected physical phone using the latest source, including immersive fullscreen and the updated picker text.

## Environment

- Realme RMX3370, Android 13; 8 GB physical RAM class (kernel MemTotal 7,679,312 kB).
- Qualcomm SM8250 / Adreno 650; 1080 × 2400 display, density 480.
- Wireless ADB; phone on battery. Battery/thermal snapshots are stored with the evidence.
- R8-optimized release, versionName 1.3 / versionCode 4. A local Gradle init script uses the existing development signing key solely to install over the development app without clearing its data. Release optimization remains enabled. This test APK is separate from the earlier submission-signed APKs.
- APK SHA-256: `9e29f7c5aa2cd132f7249eb3c5f95b084670706e01cb0726e0a5c331696ce392`.

## Results

| Scenario | Average presentation FPS | P95 frame interval | Maximum interval |
|---|---:|---:|---:|
| Fullscreen Microscope, labels on, rotate | 60.00 | 16.83 ms | 17.19 ms |
| Five models, all labels on, drag Microscope card | 58.50 | 16.84 ms | 33.29 ms |
| Five models, all labels on, rotate Microscope | 60.00 | 16.76 ms | 17.23 ms |
| Five models, rotate Microscope — run 1 | 60.00 | 16.80 ms | 16.98 ms |
| Five models, rotate Microscope — run 2 | 60.00 | 16.78 ms | 17.40 ms |

All five models were loaded for every scenario. In fullscreen, the other four remained loaded but were covered. Five-model rotation was repeated twice. No measured interval exceeded 33.34 ms in these windows. These short tests support smooth rotation and dragging on this phone; they do not establish sustained performance or performance across every model/camera combination.

Final process memory was 306,052 kB PSS (298.88 MiB), plus 158 kB swap PSS. This is a snapshot, not a memory-leak test. Repeated add/close stress testing and pinch-zoom FPS were not measured in this run; earlier emulator stress results remain separately documented.

## Method

Each scenario used a 12-second Perfetto capture with an 8-second continuous Android input swipe. Frame analysis selects six seconds starting one second after the swipe command's device-uptime timestamp. App FrameTimeline surface tokens are joined to actual display-frame presentation timestamps, excluding dropped app frames. FPS is the number of distinct presentations divided by six; interval percentiles use the gaps between those presentations.

These are app-window presentation rates, not isolated Filament GPU timings. Android UI hierarchy extraction returned a null root on this phone, so setup used screen coordinates verified against screenshots. No app rendering code was changed for the benchmark. Input, screenshot and trace collection succeeded. The attempted temporary screen-timeout change was denied by the phone; its setting was unchanged.

The phone substantially outperformed the earlier emulator in the measured scenarios, but hardware and app revisions differ. This is not evidence that any physical 2 GB phone will necessarily outperform that emulator. The 12 GB phone remains user-reported manual testing, without an FPS measurement.

## Evidence

Local files in `artifacts/verification/realme-rmx3370-latest/` include the test APK, environment, screenshots, five traces, gesture intervals, analysis SQL, presentation timestamp CSVs, `frame-results.json`, and memory/thermal diagnostics. The final coordinate-based setup is in `model-studio-phone-stage1.py` and `model-studio-phone-stage2.py`; the UI-hierarchy attempt was unsuccessful. The analyzer uses the Perfetto v58.2 trace processor at `/tmp/model-studio-trace-processor`. Evidence is excluded from Git, while this report preserves the measured results.
