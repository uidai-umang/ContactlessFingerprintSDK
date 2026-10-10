# Decisions

## 2026-09-03 — Slap capture v3: native MediaPipe Tasks HandLandmarker (replaces Python/Chaquopy bridge)

**Decision:** Replaced the Python/Chaquopy `mediapipe` bridge for slap-capture
hand detection with Google's native MediaPipe Tasks Android library
(`com.google.mediapipe:tasks-vision`), which this project already depends on
and already uses natively in `MediapipeSegmenter.kt`. Added
`SlapHandLandmarker.kt` (mirrors `MediapipeSegmenter.kt`'s
`BaseOptions`/`RunningMode.IMAGE`/`Delegate.CPU` setup pattern, but wraps
`HandLandmarker` instead of `ImageSegmenter`), rewired
`SlapFrameAnalyzer.analyze()`'s internals to call it (public signature
unchanged, so `SlapCaptureListener`/`SlapCaptureViewModel`/`SlapCaptureRoute`
needed zero changes), and fully reverted the `slap_frame_check` Python
function + its `PyObject` extension helpers back to pristine (this repo's
`liveness_detector_mediapipe.py`/`PyObjectExt.kt` are back to their
pre-slap-work state — the file was never a real dependency, since Python
`mediapipe` was never actually declared in the Chaquopy `pip {}` block and
isn't reliably installable there at all, being a heavy native/Bazel-built
package).

**Model asset:** Downloaded from
`https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task`
(the exact URL given in the task, confirmed live/working) to
`capture/src/main/assets/hand_landmarker.task` (7.8MB, verified as a valid
zip-based `.task` bundle containing `hand_detector.tflite` +
`hand_landmarks_detector.tflite`). Re-download from that same URL if the
asset is ever lost — it always resolves to Google's latest float16 build.

**Why:** `ModuleNotFoundError: No module named mediapipe` crashed the
slap-capture screen on entry every time, because the pip-installed
`mediapipe` package was never actually wired into Chaquopy's `pip {}` block
and Python `mediapipe` is not a lightweight pure-Python package Chaquopy
can reliably build for Android regardless. The native Tasks Vision library
is Google's officially supported Android path for this exact model family
and was already a proven, working dependency in this codebase.

**No handedness mirroring correction applied:** `CameraSettings.CAMERA_FACING`
defaults to `LENS_FACING_BACK` (see `CameraController.kt`), and back-camera
frames aren't mirrored, so `HandLandmarker`'s handedness label is used as-is
-- same assumption the earlier Python `HandDetector` made.

**Affects:** `capture/src/main/assets/hand_landmarker.task` (new),
`capture/.../domain/method/hand/SlapHandLandmarker.kt` (new),
`capture/.../usecase/slap/SlapFrameAnalyzer.kt` (internals rewritten, public
API unchanged), `capture/src/main/python/liveness_detector_mediapipe.py`
(reverted to pristine -- no slap code), `capture/.../utils/extension/PyObjectExt.kt`
(reverted to pristine). `capture/build.gradle` already had
`implementation libs.tasks.vision` wired in from the original
`MediapipeSegmenter.kt` work -- no dependency changes needed.

**Verified:** `:capture:compileDebugKotlin` passes. Built `:app:assembleDebug`,
installed on a connected device, and launched the flow directly via
`in.gov.uidai.core.CoreActivity` (bypassing the registration UI) with a
`fingerType=Left` PidOptions request -- confirmed via `adb logcat` that
`hand_landmarker.task` loads successfully, `HandLandmarker.detect()` runs
cleanly every throttled frame (~130-240ms per call on-device, no crash, no
exception), and a screenshot confirms `SlapCaptureRoute` renders correctly
("Left hand · 4-finger slap" label, "Move hand closer" status, dark bottom
bar) with no crash on entry.

**Follow-up needed:** None for correctness. Real inference latency
(~130-240ms/call on the test device) is well above the ~10fps/100ms
throttle target, meaning actual effective detection cadence is closer to
~4-7fps in practice -- the `isProcessing` guard in `SlapCaptureListener`
already self-limits to whatever the real inference speed allows, so this
doesn't break anything, but the "~10fps" framing in the earlier Decisions
entry is now aspirational-ceiling rather than actual-achieved on this
device.

---

## 2026-09-03 — Slap capture v2: simple standalone detector + screen (replaces earlier complex plan)

**Decision:** Rebuilt slap capture (Left/Right, 4-finger) as a small, standalone
`SlapCaptureListener` (`ImageReader.OnImageAvailableListener`) plugged directly
into `CameraController.setOnImageAvailableListener()`, instead of the earlier
`ImageProcessor` subclass / `HandCheckRunner` design from the previous session.
No guide-overlay silhouette, no Stage1/Stage2 pipeline, no dual fast/slow
check runners. Used the defaults given in this task's spec unchanged:
**2 consecutive passing frames** for the auto-capture debounce, **~10fps
(100ms) throttle** for the Python bridge calls, **area ratio threshold
0.65**, and **8% crop padding**.

**Why:** Explicit instruction to keep this lean — the earlier design's
`ImageProcessor` base-class hooks (`includesFingerCheck`, `extraStage1*`,
etc.) added real surface area to a shared file for a single new capture
mode. This version touches no shared pipeline file at all: `CameraController`
is reused completely unmodified (its `setOnImageAvailableListener` was
already decoupled from `ImageProcessor` for exactly this kind of use), and
`SlapCaptureListener`/`SlapFrameAnalyzer`/`SlapCaptureViewModel` are all new,
self-contained files.

**Affects:** `capture/.../usecase/slap/SlapFrameAnalyzer.kt`,
`capture/.../usecase/slap/SlapCaptureListener.kt`,
`capture/.../ui/camera/slap/SlapCaptureViewModel.kt`,
`capture/.../ui/camera/slap/SlapCaptureRoute.kt`,
`capture/src/main/python/liveness_detector_mediapipe.py` (`slap_frame_check`).

**Follow-up needed:** None functionally required, but the 0.65/2-frame/10fps
values are still untuned against real device footage — same recalibration
caveat as before, just simpler knobs now (all four constants live as
`private const val`s at the top of `SlapCaptureListener`, not
preference-store-backed this time, since nothing else in this design reads
`PreferenceStore` for slap-specific values).

---

**Decision:** `SlapCaptureViewModel` constructor-injects its own
`CameraController` (via plain `@Inject constructor`, since `CameraController`
isn't `@Singleton`-scoped) rather than receiving one from `CaptureActivity`
the way `CameraScreen.kt` does. The ViewModel is responsible for
`initializeCamera()`/`closeCamera()`/listener wiring itself; `SlapCaptureRoute`
only calls methods on `viewModel.cameraController`.

**Why:** The task explicitly asked for "a CameraController instance (same
construction pattern CameraViewModel uses)" -- `CameraViewModel` doesn't
actually hold one today (it's Activity-owned there), so the closest faithful
reading was "get it via plain Hilt constructor injection," which naturally
gives the ViewModel-owned shape the spec described, and keeps
`CaptureActivity`/`CameraScreen.kt` completely untouched for the existing
single-finger flow (per the explicit constraint not to touch either).

**Affects:** `capture/.../ui/camera/slap/SlapCaptureViewModel.kt`.

**Follow-up needed:** None -- `CameraController`'s own camera-open/close
lifecycle already guards against double-init (`isCameraInitialized` flag).

---

**Decision:** `slap_frame_check()` (Python) does NOT rotate the frame or
compensate for sensor orientation -- it runs MediaPipe Hands directly on the
raw NV21→BGR frame, exactly mirroring the spec's literal function signature
(no rotation parameter) and `process_frame()`'s existing (also
non-rotating) conversion. Rotation is instead handled entirely on the
Kotlin side: `SlapCaptureListener` rotates the returned fingertip/box points
from raw sensor space to upright-image space using a new `PointF.rotateACW()`
extension (mirroring the existing `RectF.rotateACW()` in the same file,
same per-point transform), before publishing them to `SlapLiveState` --
the crop itself needs no rotation compensation since `CameraFrame.getByteArray()`
crops the raw NV21 bytes directly in their native (already-consistent) space,
and the final saved bitmap gets rotated upright via the existing
`Bitmap.rotate()` extension, same as everywhere else in this codebase.

**Why:** Keeps the Python function exactly as simple as specified. The
rotation math genuinely has to live somewhere for the on-screen fingertip
markers to land in the right place, and reusing `RectF.rotateACW()`'s proven
per-corner transform (rather than inventing a new one) was the lowest-risk
option available without device testing.

**Affects:** `capture/.../utils/extension/RectFExtensions.kt`
(`PointF.rotateACW()`, `RectF.inflatedByPercent()` -- both additive),
`capture/.../usecase/slap/SlapCaptureListener.kt`.

**Follow-up needed:** Verify on a real portrait device that fingertip
markers land under the actual fingertips, particularly at sensor rotations
90/270 (most phones). This is the one part of this design not exercised by
any existing, already-verified code path.

---

**Decision:** Confirming **Thumbs (2-finger, dual-hand) capture remains out
of scope** -- not built in this session either. The registration module's
"Choose capture method" screen's Thumbs chip stays selectable (per the
original task) but does not enable Continue (`isMethodReadyToContinue` only
treats `LEFT_SLAP`/`RIGHT_SLAP` as ready); `SlapCaptureLauncher.toExpectedHandType()`
throws `IllegalArgumentException` for `SlapSubOption.THUMBS` as a defensive
guard, since that code path should be unreachable given the Continue-button gate.

**Affects:** `registration/.../ui/registration/method/CaptureMethodRoute.kt`,
`registration/.../usecase/SlapCaptureLauncher.kt`.

**Follow-up needed:** Build Thumbs capture as a separate task when
prioritized -- different detection shape entirely (2 hands simultaneously,
thumb-only landmarks).
