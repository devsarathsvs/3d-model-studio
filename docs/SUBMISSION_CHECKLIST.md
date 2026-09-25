# Submission readiness

Status as of 24 September 2026, checked against the supplied screening task.

Version 1.3 is the latest icon-controls, illustrated Help and startup-state update. Its build/JVM/lint checks pass, but a fresh
connected-device run is pending. The table below records the previously verified 1.1 handoff.

| Item | Current status | Remaining action |
| --- | --- | --- |
| Signed APK | `artifacts/Model-Studio-1.1.apk`, signed and delivered to Telegram; installed and smoke-tested on the Realme | Include an accessible APK download with the actual submission |
| Source code | Source bundle prepared as `artifacts/Model-Studio-source-1.1.zip` | Publish to the candidate's GitHub repository and check reviewer access; no remote is visible in this workspace |
| README | Library choice, architecture, performance choices, trade-offs, device and limitations documented | Review alongside the final repository |
| Walkthrough | Eight-minute script prepared in `INTERVIEW_WALKTHROUGH.md` | Record your narration and screen, confirm 5–10 minutes, then supply an accessible video URL |
| Functional verification | 19 JVM tests, two connected tests and signed-release smoke test recorded | Rehearse the demo on the phone used for recording |
| Required low-end performance | Not established on 2–3 GB hardware | Run the release on that device class; inspect active-interaction frame times and settled memory |
| Minimum Android version | API 24 configured; connected checks used API 33 | Test API 24 separately if hardware/emulator coverage is available |

## Source bundle contents

The source ZIP contains the Gradle wrapper and build configuration, app source/resources, all
five supplied GLBs, tests, scripts and documentation. It excludes `.signing`, local SDK paths,
IDE state, Git metadata, build caches, generated APKs and raw device captures.

The source ZIP is a convenient handoff; it does not replace the required GitHub link. 

To build from the extracted bundle, configure the Android SDK and use the wrapper commands in
README. A reviewer can build debug without your release key. Generating a new local signing key
produces a different application signature; keep the existing private `.signing` directory backed
up separately for future updates to the delivered release.

## Before sending the actual submission

1. Confirm the GitHub link opens with the intended reviewer access and includes the five assets.
2. Confirm the APK is the verified file whose SHA-256 is recorded in VERIFICATION.md.
3. Open the video URL in a session with the same access as the reviewer; check sound and length.
4. State tested hardware and remaining performance coverage accurately.
5. Replace the placeholders below with real URLs before using the message.

## Submission message draft

Hello,

Here is my Android 3D Model Viewer submission:

- Source code: [GitHub repository URL]
- Signed APK: [APK download URL]
- Demo and code walkthrough: [video URL]

The README explains the Filament-based architecture, performance choices and trade-offs. The
verification record includes the automated checks and testing on a Realme RMX3370 / Android 13.
Sustained performance on a 2–3 GB device remains unverified in this test environment.

Thank you for reviewing my submission.

[Your name]

This message is a draft only. Nothing has been emailed or shared with reviewers by this workflow.
