# Model Studio interview walkthrough

Target length: about eight minutes, including the live gestures below. Rehearse once and explain
these decisions in your own words. This is a recording script, not a completed video or video URL.

## Before recording

- Open the delivered signed release on the phone. Start with an empty workspace by closing the
  existing cards; this also gives you a chance to rehearse the three controls.
- Open the source files linked below in your editor. Keep README and VERIFICATION visible.
- Capture the phone and editor, with your own microphone narration. Hide notifications and any
  unrelated personal windows. Confirm the recording contains both the demo and code explanation.
- Keep the source's unexpected Solar System label text unchanged and explain it if it appears.

## 0:00–0:40 — Introduce the project

**Show:** the empty workspace and Add model picker.

“Model Studio is a Kotlin Android app with one Activity and no Fragments. It bundles five GLB
models and works offline. Each model has its own movable, resizable card and independent camera.
The icon row switches interaction mode, toggles labels, closes the model, and expands or restores
the card. Long-pressing an icon shows its description. The minimum
Android version is API 24.”

## 0:40–2:10 — Demonstrate the required behavior

**Do:** add Bulb, Fiagena, Lungs, Microscope and Solar System, then tap Arrange. Drag one card and
pinch it smaller and larger. Show the four icons and their tooltips, then briefly open Help to show the illustrated guide.

“Normal mode changes the card bounds. One finger moves the card and two fingers resize it.
The 3D viewport follows the content area below the controls. Arrange is an extra convenience
for showing all five models together; it does not replace individual movement or resizing.”

**Do:** tap the fullscreen icon and point out that interaction activates automatically. Rotate
and pinch to zoom, keeping the card in the same position. Restore the card; show that it returns
to move/resize mode. Turn Interact on explicitly, then turn it
off and move the card again. Turn Labels on and rotate with Interact on. Turn Labels off.

“In interaction mode these same gestures affect only the camera. The card stays fixed. Labels
are hidden when a model is added. Their text comes from the supplied model metadata and the
connector anchors follow the parts as the camera changes.”

**Do:** close one model, re-add it, and briefly show background/resume or device rotation.

“Closing removes the model and releases its owned rendering resources. The workspace also
saves model choices, card positions, camera positions and label visibility. Opening the app starts
with movable cards and Interact off. Fullscreen turns it on; restoring turns it off.”

## 2:10–3:20 — Explain ownership and the shared renderer

**Open:** [MainActivity.kt](../app/src/main/java/com/iftl/threedee/viewer/MainActivity.kt), then
[FilamentEngineHost.kt](../app/src/main/java/com/iftl/threedee/viewer/render/FilamentEngineHost.kt).
Show `loadCard`, the frame callback and `requestRender`.

“Filament gives this implementation native GLB loading, scene transforms, camera control and
access to node extras. The Activity coordinates the UI and serialized model loading. The host
owns one engine, renderer and TextureView. Each model owns an independent scene, view and camera,
and renders into its card's viewport. This keeps engine ownership and frame scheduling in one
place while preserving independent model interaction.”

“File reads run on an IO dispatcher. Extras parsing runs off the main thread after the native
extras are collected. Filament calls stay on the main thread. Resource loading uses Filament's
asynchronous API for decoding, but native asset creation is still synchronous. That remaining
cost is a reason to measure loading on the target device.”

## 3:20–4:20 — Explain gesture separation

**Open:** [ModelContainerView.kt](../app/src/main/java/com/iftl/threedee/viewer/ui/ModelContainerView.kt).
Show `onTouchEvent`, `startDrag`, `startPinch`, then briefly open
[PinchResizeState.kt](../app/src/main/java/com/iftl/threedee/viewer/ui/PinchResizeState.kt).

“A gesture captures its mode when it starts. Normal mode changes bounds; interaction mode calls
the model's camera methods. Pointer IDs are tracked so lifting one finger does not accidentally
reuse another finger's coordinates. Pointer changes rebase the gesture. The pinch geometry and
zoom direction decisions are separated into small classes with JVM tests.”

“The laid-out view bounds drive the renderer. Resize requests a real layout rather than leaving
the renderer and Android controls with different sizes. Bounds are constrained to the workspace
so the controls stay reachable. Fullscreen restoration uses the actual finger-span ratio.”

## 4:20–5:20 — Explain labels and cleanup

**Open:** [ModelInstance.kt](../app/src/main/java/com/iftl/threedee/viewer/render/ModelInstance.kt).
Show `onFrame` and `destroy`. Briefly show
[LabelProjector.kt](../app/src/main/java/com/iftl/threedee/viewer/render/LabelProjector.kt).

“Only `extras.prop` provides label text. The transform manager provides a node's world position.
The projector transforms that point through the current view and projection matrices, then maps
it into local viewport pixels. Hidden labels do no projection work. Camera or viewport changes
invalidate the projection, so labels are refreshed for changed frames. Off-screen and
behind-camera anchors are excluded.”

“The overlay draws text boxes and connector lines. It searches nearby positions to reduce
collisions, although very dense labels can still overlap. It does not perform depth occlusion.”

“When closing a model, the host removes it from the render list before destroying its view,
scene, skybox, ambient light, light entity, camera component and entity, and asset. The shared
material provider belongs to the host and survives until the whole host is destroyed.”

## 5:20–6:30 — Explain performance decisions and trade-offs

**Show:** the host's frame callback and README performance notes.

“Rendering is demand driven. Input, layout, loading and other visual changes request a frame;
one pending Choreographer callback coalesces those requests. Idle static models do not need a
continuous render loop. Rendering also stops in the background. Fully covered model views are
skipped until they become visible.”

“Lighting uses one directional light plus a constant ambient fill. Shadows, post-processing,
anti-aliasing and dynamic resolution are disabled. That reduces rendering work, at the cost of
simple lighting and less smooth edges. Dynamic resolution is a possible future tuning option,
not something this version already uses.”

“One regression test caught repeated frames caused by updating text during every layout. Those
updates now happen only when bounds change. This was a useful example of measuring an Android
UI cost as well as thinking about 3D rendering costs.”

## 6:30–7:30 — Present verification accurately

**Open:** [VERIFICATION.md](../VERIFICATION.md) and
[WorkspaceIntegrationTest.kt](../app/src/androidTest/java/com/iftl/threedee/viewer/WorkspaceIntegrationTest.kt).

“The recorded checks include 19 JVM tests and two connected tests on a Realme RMX3370 running
Android 13. The device tests load the actual five models, inject multi-pointer gestures, check
camera and card separation, validate label anchors against Filament matrices, rotate the device,
exercise cleanup, and restore state. The optimized signed release was also installed and checked.”

“The Android window measurements were a median of 22.60 milliseconds and a 95th percentile of
32.46 milliseconds in that instrumented run. These are not complete Filament GPU frame times.
A release memory snapshot after interaction was about 296 MiB PSS; that is not a peak or a
long-term memory result. Perfetto traces are retained for further analysis.”

“The connected phone is not a 2–3 GB target device. Sustained 30 FPS on that device class is
still unverified. I would run the same interactions on the evaluation class of hardware, inspect
CPU and GPU bottlenecks, and tune assets or resolution from those measurements.”

## 7:30–8:00 — Close with limitations and deliverables

**Show:** README and the signed APK filename.

“The main follow-ups are low-end device profiling, a longer settled-memory stress test, and
better label handling for dense or occluded parts. The supplied Solar System asset also contains
unexpected label text, which the app preserves as required. The handoff includes the source,
build instructions, test record and signed APK.”

After recording, verify the video lasts 5–10 minutes and its sharing link opens for the reviewer.
Do not describe the GitHub or video link as submitted until those links actually exist.

## Questions to rehearse

| Question | Points to explain from this implementation |
| --- | --- |
| Why Filament and Views? | Direct GLB/extras/transform access; explicit native ownership; direct MotionEvent handling and viewport geometry. Other approaches are possible. |
| Why one engine? | Centralized ownership, shared materials and one frame scheduler; independent views and cameras still provide model isolation. |
| Why not render every display refresh? | The supplied scenes are static until input changes them. Dirty rendering saves idle work; animation support would need scheduled updates. |
| Are resources definitely leak-free? | Owned resources are explicitly destroyed and entity/light assertions pass. These checks are bounded; longer settled-memory measurements are still needed. |
| Does the measured p95 prove 30 FPS? | No. It is Android window timing from this device and workload, not full GPU timing or low-end evidence. |
| Why not change the bad Solar System label? | The requirement uses the exact `extras.prop` text from the supplied assets. Fixing source metadata is a separate asset decision. |
| What would you improve first? | Measure the release on the required low-end device, identify the limiting stage, then tune asset, rendering or UI costs accordingly. |
