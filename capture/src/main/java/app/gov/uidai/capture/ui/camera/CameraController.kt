package app.gov.uidai.capture.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.Surface
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.ui.camera.focus.FocusFactory
import app.gov.uidai.capture.ui.camera.focus.FocusManager
import app.gov.uidai.capture.ui.camera.focus.FocusState
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider
import app.gov.uidai.capture.utils.CameraUtils
import app.gov.uidai.capture.utils.calculateFingerDistance
import app.gov.uidai.capture.utils.convertCroppedRectToSensorMeteringRect
import app.gov.uidai.capture.utils.extension.getFPSRange
import dagger.hilt.android.qualifiers.ApplicationContext
import app.gov.uidai.capture.R
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

class CameraController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceStore: PreferenceStore,
    private val focusFactory: FocusFactory
) : CameraContextProvider {

    companion object {
        private val TAG = CameraController::class.simpleName
        private const val IMAGE_BUFFER_SIZE: Int = 1
        private const val IMAGE_FORMAT = ImageFormat.YUV_420_888
        private const val DESIRED_UPPER_FPS = 24
    }

    var currentSurface: Surface? = null
    private var isCameraInitialized = false


    private val frameCounter = AtomicLong(0)

    private var _uiInfoProvider: UIInfoProvider? = null
    private val uiInfoProvider
        get() = checkNotNull(_uiInfoProvider)

    private val cameraFacing
        get() = preferenceStore.get(CameraSettings.CAMERA_FACING)

    private val isTorchOn
        get() = preferenceStore.get(CameraSettings.TORCH_ON)

    private val averageFingerWidthMM
        get() = preferenceStore.get(CameraSettings.AVERAGE_FINGER_WIDTH_MM)

    private val averageHandWidthMM
        get() = preferenceStore.get(CameraSettings.AVERAGE_HAND_WIDTH_MM)

    private val isManualAE
        get() = preferenceStore.get(CameraSettings.MANUAL_AE_SETTINGS)

    private val iso
        get() = preferenceStore.get(CameraSettings.ISO)

    private val shutterSpeed
        get() = preferenceStore.get(CameraSettings.SHUTTER_SPEED).toLong()


    private val cameraManager = context.getSystemService(
        Context.CAMERA_SERVICE
    ) as CameraManager

    private val cameraId = CameraUtils.getCameraId(cameraManager, cameraFacing)
        ?: throw Exception("No Camera Found for camera facing: $cameraFacing")

    override val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    private val captureSize: Size by lazy {
        CameraUtils.getCaptureSize(characteristics, IMAGE_FORMAT)
    }

    private var cameraDevice: CameraDevice? = null
    override lateinit var captureSession: CameraCaptureSession
    override lateinit var captureRequestBuilder: CaptureRequest.Builder

    // Camera preview thread
    private val cameraPreviewThread = HandlerThread("cameraPreviewThread")
    override val cameraPreviewHandler by lazy {
        cameraPreviewThread.start()
        Handler(cameraPreviewThread.looper)
    }

    // Session executor
    private val sessionExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sessionTimerThread")
    }

    // Image reader thread
    private val imageReaderThread = HandlerThread("imageReaderThread")
    private val imageReaderHandler by lazy {
        imageReaderThread.start()
        Handler(imageReaderThread.looper)
    }

    private val imageReader: ImageReader by lazy {
        ImageReader.newInstance(
            captureSize.width,
            captureSize.height,
            IMAGE_FORMAT,
            IMAGE_BUFFER_SIZE
        )
    }

    private var focusManager: FocusManager = focusFactory.create(this)

    // Media Player for shutter sound
    private val mediaPlayer = MediaPlayer.create(context, R.raw.shutter_sound).apply {
        setOnCompletionListener {
            it.seekTo(0)
        }
    }

    override val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun processFocusDistance(focusDistanceDio: Float?) {
            if (focusDistanceDio == null) return

            val focusDistanceMM = CameraUtils.diopterToDistance(focusDistanceDio) * 1000

            val (focusThMinMM, focusThMaxMM) = CameraUtils.calculateOptimalFocusDistance(
                sensorPhysicalSize = getSensorPhysicalSize(),
                viewFinderSize = Size(
                    uiInfoProvider.viewFinderSize.width,
                    uiInfoProvider.viewFinderSize.height
                ),
                overlayViewSize = SizeF(
                    uiInfoProvider.overlayViewCutoutRectCoordinates.width(),
                    uiInfoProvider.overlayViewCutoutRectCoordinates.height()
                ),
                deviceRotation = uiInfoProvider.deviceRotation,
                focalLengthMM = getFocalLengthInMM(),
                minFocusDistanceMM = getMinFocusDistanceInMM(),
                averageFingerWidthMM = averageFingerWidthMM
            )

            Log.d(TAG, "FOCUS_DISTANCE --: ${focusDistanceMM}mm")

            val needsFocusTrigger = focusDistanceMM > focusThMaxMM

            focusManager.setNeedFocusTrigger(needsFocusTrigger)
        }

        private fun processAFState(afState: Int?) {
            if (focusManager.getFocusState() == FocusState.TRIGGERING) {

                if (afState == null) {
                    // If AF state is not available, we can't lock focus. End the sequence.
                    Log.w(TAG, "FOCUS -- AF state is null, cannot lock focus.")
                    focusManager.unlock()
                    return
                }

                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                ) {
                    // Focus is locked, now revert to repeating request
                    // IMPORTANT: Reset the trigger and regions to avoid issues
                    captureRequestBuilder.apply {
                        set(CaptureRequest.CONTROL_AF_TRIGGER, null)
                        set(CaptureRequest.CONTROL_AF_REGIONS, null)
                    }

                    focusManager.setOptimalMode()

                    captureSession.setRepeatingRequest(
                        captureRequestBuilder.build(),
                        this,
                        cameraPreviewHandler
                    )

                    focusManager.updateFocusState(FocusState.LOCKED)
                } else {
                    Log.w(TAG, "FOCUS -- Focus lock sequence not completed yet")
                }
            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            Log.d(TAG, "FRAME_COUNT -- ${frameCounter.incrementAndGet()}")
            Log.d(TAG, "FRAME_TICK -- ${SystemClock.uptimeMillis()}")
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            processAFState(afState)
            val focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE)
            processFocusDistance(focusDistance)
            Log.d(TAG, "FRAME_DEBUG -- onCaptureCompleted fired")

            Log.d(
                TAG,
                """
                ======== Capture Result ========
                AE Mode          : ${result.get(CaptureResult.CONTROL_AE_MODE)}
                AE State         : ${result.get(CaptureResult.CONTROL_AE_STATE)}
                Exposure Time    : ${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}
                ISO              : ${result.get(CaptureResult.SENSOR_SENSITIVITY)}
                Frame Duration   : ${result.get(CaptureResult.SENSOR_FRAME_DURATION)}
                Focus Distance   : ${result.get(CaptureResult.LENS_FOCUS_DISTANCE)}
                ===============================
                """.trimIndent()
            )
        }
    }

    fun setUIInfoProvider(uIInfoProvider: UIInfoProvider) {
        _uiInfoProvider = uIInfoProvider
    }

    fun initializeCamera() {
        if (isCameraInitialized) {
            Log.w(TAG, "initializeCamera() called again while already initialized — ignoring")
            return
        }
        isCameraInitialized = true

        // Creates list of Surfaces where the camera will output frames
        val targets =
            listOf(uiInfoProvider.viewFinderSurface, imageReader.surface)

        openCamera(cameraManager, cameraId, targets, cameraPreviewHandler)

        Log.i(TAG, "Camera initialized")
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(
        manager: CameraManager,
        cameraId: String,
        targets: List<Surface>,
        handler: Handler? = null
    ) {

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createCaptureSession(device, targets, handler)
                Log.i(TAG, "Camera $cameraId has been opened")
            }

            override fun onClosed(camera: CameraDevice) {
                super.onClosed(camera)
                Log.i(TAG, "Camera $cameraId has been closed")
            }

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)

            }
        }, handler)
    }

    private fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ) {
        Log.d(TAG, "AE_RANGE -- Device supports exposure time: " +
                "${characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}")
        Log.d(TAG, "AE_RANGE -- Device supports ISO: " +
                "${characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}")

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            targets.map {
                OutputConfiguration(it)
            },
            sessionExecutor,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session

                    captureRequestBuilder =
                        device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                    targets.forEach {
                        captureRequestBuilder.addTarget(it)
                    }

                    // Auto exposure and white balance
                    captureRequestBuilder.apply {
                        set(
                            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
                            CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
                        )

                        set(
                            CaptureRequest.EDGE_MODE,
                            CaptureRequest.EDGE_MODE_HIGH_QUALITY
                        )

                        set(
                            CaptureRequest.NOISE_REDUCTION_MODE,
                            CaptureRequest.NOISE_REDUCTION_MODE_MINIMAL
                        )

                        set(
                            CaptureRequest.CONTROL_AWB_MODE,
                            CaptureRequest.CONTROL_AWB_MODE_AUTO
                        )

                        if (isTorchOn) {
                            set(
                                CaptureRequest.FLASH_MODE,
                                CaptureRequest.FLASH_MODE_TORCH
                            )
                        }

                        if (isManualAE) {
                            set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_OFF
                            )
                            set(
                                CaptureRequest.SENSOR_SENSITIVITY,
                                iso
                            )
                            set(
                                CaptureRequest.SENSOR_EXPOSURE_TIME,
                                shutterSpeed
                            )
                        } else {
                            set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_AUTO
                            )
                        }

                        focusManager.setOptimalMode()

                        // Set a stable, lower FPS range to reduce processing load and avoid drops
                        characteristics.getFPSRange(
                            desiredUpperFPS = DESIRED_UPPER_FPS
                        )?.let { fpsRange ->
                            set(
                                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                                fpsRange
                            )
                            Log.d(TAG, "FPS -- Using AE target FPS range: $fpsRange")
                        }
                    }

                    val request = captureRequestBuilder.build()

                    for (key in request.keys) {
                        Log.d(TAG, "${key.name} = ${request.get(key)}")
                    }

                    session.setRepeatingRequest(
                        request,
                        captureCallback,
                        handler
                    )
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Capture session configuration failed")
                }
            }
        )

        device.createCaptureSession(sessionConfig)
    }

    fun setOnImageAvailableListener(listener: ImageReader.OnImageAvailableListener?) {
        Log.d(TAG, "SET_LISTENER -- listener=$listener, imageReader=$imageReader")
        imageReader.setOnImageAvailableListener(listener, imageReaderHandler)
    }

    fun getPreviewSize(): Size {
        return CameraUtils.getPreviewSizeMatchingCapture(context, characteristics, captureSize)
    }

    fun updateTorchState() {
        captureRequestBuilder.let {
            val flashMode =
                if (isTorchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
            it.set(CaptureRequest.FLASH_MODE, flashMode)
            captureSession.setRepeatingRequest(it.build(), captureCallback, cameraPreviewHandler)
        }
    }

    fun updateFocusManager(){
        focusManager = focusFactory.create(this)
    }

    fun triggerFocusLock(fingerRect: RectF, cutoutRect: RectF, imageSize: Size) {
        val paramProvider = object : FocusLockParamProvider {
            override fun getMeteringRectangle(): MeteringRectangle? {
                return convertCroppedRectToSensorMeteringRect(
                    fingerRectInCroppedImage = fingerRect,
                    cutoutRectInFullImage = cutoutRect,
                    fullImageSize = Size(imageSize.width, imageSize.height),
                    sensorActiveArraySize = getSensorActiveArraySize()
                )
            }

            override fun getFingerDistance(): Float {
                return calculateFingerDistance(
                    fingerRect = fingerRect,
                    imageSize = imageSize,
                    sensorPhysicalSize = getSensorPhysicalSize(),
                    focalLengthMM = getFocalLengthInMM(),
                    averageFingerWidthMM = averageFingerWidthMM
                )
            }

            override fun getManualDistance(): Float {
                return preferenceStore.get(CameraSettings.MANUAL_FOCUS_DISTANCE)
            }
        }

        focusManager.lock(paramProvider = paramProvider)
    }

    fun triggerFocusUnlock() {
        focusManager.unlock()
    }

    fun isFocusLockedForCapture(): Boolean {
        return focusManager.isFocusLockedForCapture()
    }

    fun capturePhoto() {
        val device = cameraDevice ?: return
        val session = captureSession
        val stillSurface = imageReader.surface

        try {
            val captureBuilder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(stillSurface)
                }

            // Temporary listener just for this shot
            session.abortCaptures()
            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        sess: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(sess, request, result)

                        Log.d(TAG, "Still capture complete — preview restored")
                    }
                },
                cameraPreviewHandler
            )
            mediaPlayer?.start()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Still capture failed", e)
        }
    }

    fun retakeCapture() {
        focusManager.setOptimalMode()
        captureSession.setRepeatingRequest(
            captureRequestBuilder.build(),
            captureCallback,
            cameraPreviewHandler
        )
        Log.i(TAG, "Preview resumed — no re-init needed")
    }

    fun getFocalLengthInMM(): Float {
        return characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?.first() ?: 0f
    }

    fun getMinFocusDistanceInMM(): Float {
        val minFocusDistanceDio = characteristics.get(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
        ) ?: 0f
        return minFocusDistanceDio.let {
            CameraUtils.diopterToDistance(it) * 1000
        }
    }

    fun getSensorPhysicalSize(): SizeF {
        return characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            ?: SizeF(0f, 0f)
    }

    fun getSensorActiveArraySize(): Rect {
        return characteristics.get(
            CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
        ) ?: Rect()
    }

    fun getSensorRotation(): Int {
        return try {
            characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get sensor orientation", e)
            0
        }
    }

    fun handleTapToFocus(tapPointInCroppedImage: PointF, cutoutRect: RectF, imageSize: Size) {
        // Build a small square RectF around the tap point — reuses the
        // EXISTING, already-working rect-to-MeteringRectangle conversion
        // instead of a separate point-based one.
        val tapTargetSize = imageSize.width * 0.12f  // ~12% of frame width, tune empirically
        val tapRect = RectF(
            tapPointInCroppedImage.x - tapTargetSize / 2,
            tapPointInCroppedImage.y - tapTargetSize / 2,
            tapPointInCroppedImage.x + tapTargetSize / 2,
            tapPointInCroppedImage.y + tapTargetSize / 2
        )
        val meteringRect = convertCroppedRectToSensorMeteringRect(
            fingerRectInCroppedImage = tapRect,
            cutoutRectInFullImage = cutoutRect,
            fullImageSize = Size(imageSize.width, imageSize.height),
            sensorActiveArraySize = getSensorActiveArraySize()
        )

        focusManager.updateFocusState(FocusState.TRIGGERING)

        captureRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
        }
        captureSession.capture(captureRequestBuilder.build(), null, cameraPreviewHandler)

        captureRequestBuilder.apply {
            meteringRect?.let { set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it)) }
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        }
        captureSession.capture(captureRequestBuilder.build(), captureCallback, cameraPreviewHandler)
        // AF mode gets restored to whatever the active strategy actually uses
        // once focus locks — see the processAFState fix below. Without that
        // fix, every tap permanently strands the camera in AF_MODE_AUTO.
    }

    fun triggerHandFocusLock(handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) {
        val axesSwapped = rotationDegrees == 90 || rotationDegrees == 270
        val sensorPhysical = getSensorPhysicalSize()
        val sensorWidthMMForUprightWidthAxis =
            if (axesSwapped) sensorPhysical.height else sensorPhysical.width

        val paramProvider = object : FocusLockParamProvider {
            override fun getMeteringRectangle(): MeteringRectangle? {
                // Full sensor active array -- see note above on why we don't
                // attempt a precise region for the upright hand box here.
                val active = getSensorActiveArraySize()
                return MeteringRectangle(active, MeteringRectangle.METERING_WEIGHT_MAX)
            }

            override fun getFingerDistance(): Float {
                val perceivedWidthPixels = handBoxUpright.width()
                val imageWidthPixels = uprightImageSize.width.toFloat()
                if (perceivedWidthPixels <= 0) return 0f

                val distanceM = 2 * (getFocalLengthInMM() * averageHandWidthMM * imageWidthPixels) /
                        (perceivedWidthPixels * sensorWidthMMForUprightWidthAxis) / 1000

                Log.d(TAG, "HAND DISTANCE -- ${distanceM}m")
                return distanceM
            }

            override fun getManualDistance(): Float {
                return preferenceStore.get(CameraSettings.MANUAL_FOCUS_DISTANCE)
            }
        }

        focusManager.lock(paramProvider = paramProvider)
    }


    fun closeCamera() {
        Log.i(TAG, "closeCamera()")
        isCameraInitialized = false
        captureSession.close()
        cameraDevice?.close()
        sessionExecutor.shutdown()
        imageReaderThread.quitSafely()
        cameraPreviewThread.quitSafely()
        cameraDevice = null
    }

    interface UIInfoProvider {
        val viewFinderSurface: Surface
        val viewFinderSize: Size
        val overlayViewCutoutRectCoordinates: RectF
        val deviceRotation: Int
    }
}