# Decisions

## 2026-10-22 — Capture flow: one Activity, capture loop owns the sequence and the upload

**Decision:** Removed the Intent/ActivityResult hop into `CaptureActivity`.
`RegistrationActivity`'s single NavHost now has a `Routes.CaptureLoop`
destination (`CaptureLoopRoute`) that renders `CameraScreen` /
`SlapCaptureRoute` directly. `RegistrationViewModel` (already Activity-scoped
and shared by `CaptureMethodRoute` / `RegistrationRoute`) is the session
ViewModel: `startSlapSequence` / `startHandSequence` (ordering logic
unchanged) fill the queue, `activeCapture` exposes the current position,
and the capture screens hand back a `CaptureResult` holding the final
`Bitmap` in memory through `onCaptureResult`. On success the ViewModel marks
the finger `UPLOADING`, launches JP2 encode -> (single finger only)
`FingerEmbedder.embed` -> `EncryptionService.encryptImage` ->
`CaptureQueueManager.uploadOrQueue` in an `@ApplicationScope`
`CoroutineScope(SupervisorJob() + Dispatchers.Default)` (new
`CoroutineScopeModule`), and starts the next capture at the same moment.
When the queue empties, or on back-press / abort / capture failure /
camera-permission denial, `activeCapture` goes null and the loop pops back
to the screen that launched it, with statuses intact.

**Screen reuse:** `CameraController` is single-use (`closeCamera()` stops its
threads), so each run of same-type captures (a "segment": single-finger vs
slap) gets a fresh controller (`Provider<CameraController>`) and a
`hiltViewModel(key = ...)` instance. Within a segment the screen stays
mounted: `captureId` changes trigger `viewModel.reset()` + `restartCapture()`
(single finger) or `viewModel.reset()` (slap). Left -> right slap is handled
in place by making `SlapCaptureListener.expectedHandType` mutable.

**Why:** Each finger previously went back to the method screen and
relaunched an Activity, paying for camera + model start-up and passing
images through PidOptions XML and a base64 file in `cacheDir/shared`
behind a FileProvider. Uploads ran in `viewModelScope`.

**Removed:** `CaptureActivity`, `CaptureRoutes`, `PidOptions` / `Opts` /
`CustOpts` / `Param` / `SDKResponse`, `XmlMapper`, `JourneyConstant`,
`SDK_*` + `CAPTURE_TIMEOUT` result codes, `FingerSDKManager(Impl)`,
`SlapCaptureLauncher`, `SDKResult`, Jackson XML / kotlin / stax deps, the
shared FileProvider + `file_path.xml`, `CameraFragment`, `GuidelineFragment`,
`GuidelineAdapter`, `DebugSettingsFragment`, `sdk_nav_graph.xml` and the
`activity_camera` / `fragment_camera` / `fragment_guideline` layouts. Chaquopy
start-up and the debug `MainThreadWatchdog` moved into `RegistrationActivity`,
which also asks for `WRITE_EXTERNAL_STORAGE` below Q (as CaptureActivity did).
`CameraController.closeCamera()` now tolerates a session that never opened.

**Kept:** `FingerEmbedder` -- still used by `SharedViewModel` (initialize /
embed on assets) and for single-finger captures (the grid preview and
`registerUser`). The bound impl is still `DemoFingerEmbedderImpl`. The
BuildConfig.DEBUG gating for review screens / segmentation / local saves is
unchanged.

**Verified:** `:app:assembleDebug` and `:app:assembleRelease` pass. Not yet
tested on a device.

**Follow-up needed:** On-device pass of both sequences. Legacy view classes
now appear unreferenced (`BottomSheetView`, `LiveQualityScoresView`,
`OverlayMaskView`, `BottomCameraControllerView`, `ChipExtensions`,
`list_item_guideline.xml`) -- left for a separate cleanup.

---

## 2026-10-22 — Collapse to one library module (:capture) + :app shell

**Decision:** Folded `:network`, `:utility`, `:core` and `:registration` into
`:capture` (in that order, building after each). Kotlin packages are
unchanged (`in.gov.uidai.network`, `in.gov.uidai.utility`,
`app.gov.uidai.registration`, `in.gov.uidai.embedding` now live under
`capture/src/main/java`). `settings.gradle.kts` is `include(":app",
":capture")` and `:app` depends only on `:capture`. One namespace
(`app.gov.uidai.capture`): every registration `R` / `BuildConfig` import
now points at capture's.

**Core:** `CoreActivity` / `CaptureSDK` deleted. Callers used
`CaptureActivity.createIntent` directly and `parseResponse` accepted the
`CAPTURE_*` codes (both later removed by the flow change above).

**Registration merge:** manifest entries (INTERNET, POST_NOTIFICATIONS,
`RegistrationActivity`), Room / WorkManager / Hilt-work, Retrofit / OkHttp,
JP2, Coil, Chucker debug / no-op release, the compose foundation 1.7.6
force and Remote Config moved into `capture/build.gradle.kts`.
`BACKEND_URL` now comes from `local.properties` per build type
(`BACKEND_URL_DEBUG` / `BACKEND_URL_RELEASE`) through
`androidComponents.onVariants` with a lazy provider: only the variant
being built needs its key, and a missing key fails with "Missing
BACKEND_URL_<TYPE> in local.properties ...".

**Resource collisions:** dropped registration's `app_name` (`:app`'s value
already won at runtime, so the JP2 gallery folder is unchanged),
`hello_blank_fragment`, `md_theme_surface` (same colour), and the
identical fonts, launcher icons/drawables and backup XMLs (`:app` already
overrides them). Registration's strings, `black` / `white` and
`Theme.ContactlessRegistration` were appended to capture's values.

**Dedup:** one `PreferenceStore` / `PreferenceType` (union: BOOLEAN, INT,
FLOAT, DOUBLE, STRING, CHOICE) writing to `debug_settings`. Registration's
old `attendance_app_pref` file is copied in once (keys not already present)
and cleared, so stored operator / resident values survive the update. One
`FileRepository` (`in.gov.uidai.network.data.local`) with the bitmap-save
and JP2-save / readAsset methods and a single `StorageModule` binding. One
`toYYYYMMDDHHmmss` (utility).

**Verified:** `:app:assembleDebug` + `:app:assembleRelease` pass. Fail-fast
checked by removing `BACKEND_URL_DEBUG` locally.

---

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
