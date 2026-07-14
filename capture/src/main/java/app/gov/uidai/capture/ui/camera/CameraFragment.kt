package app.gov.uidai.capture.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.ui.camera.manager.PermissionManager
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import app.gov.uidai.capture.ui.camera.view.OverlayView
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ProcessingSettings
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.utils.extension.animateToFitText
import app.gov.uidai.capture.utils.extension.enablePulseOnTextChange
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule_PackageNameFactory.packageName
import com.google.android.gms.common.wrappers.Wrappers.packageManager
import dagger.hilt.android.AndroidEntryPoint
import app.gov.uidai.capture.R
import app.gov.uidai.capture.databinding.FragmentCameraBinding
import app.gov.uidai.capture.utils.extension.toBase64
import app.gov.uidai.capture.utils.getCutoutRectInImageCoordinates
import `in`.gov.uidai.utility.constants.ResultCode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.graphics.PointF
import android.view.MotionEvent

@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding: FragmentCameraBinding get() = _binding!!

    private val viewModel: CameraViewModel by viewModels()
    private val navArgs: CameraFragmentArgs by navArgs()

    // Permission Manager
    private val permissionManager = PermissionManager(this)

    private var imageProcessor: ImageProcessor? = null

    @Inject
    lateinit var cameraController: CameraController

    @Inject
    lateinit var imageProcessorFactory: ImageProcessorFactory

    @Inject
    lateinit var preferenceStore: PreferenceStore

    private var lastLiveScoreUpdateMs = 0L

    private var lastKnownImageSize: Size? = null

    private val uiInfoProvider = object : CameraController.UIInfoProvider {
        override val overlayViewCutoutRectCoordinates: RectF
            get() = binding.viewOverlay.getCutoutRect()

        override val deviceRotation: Int
            get() = getDeviceRotation(requireActivity())

        override val viewFinderSurface: Surface
            get() = binding.viewFinder.holder.surface

        override val viewFinderSize: Size
            get() = Size(binding.viewFinder.width, binding.viewFinder.height)
    }

    private val imageProcessorProvider = object : ImageProcessor.Provider {
        override val totalRotation: Int
            get() = (cameraController.getSensorRotation() - getDeviceRotation(requireActivity()) + 360) % 360

        override val isFocusLockedForCapture: Boolean
            get() = cameraController.isFocusLockedForCapture()

        override val previewSize: Size
            get() = cameraController.getPreviewSize()

        override fun getCutoutRectInImageCoordinates(imageSize: Size, rotation: Int): RectF {
            return getCutoutRectInImageCoordinates(
                imageSize = imageSize,
                totalRotation = rotation,
                overlayViewCutoutRect = binding.viewOverlay.getCutoutRect(),
                viewFinderSize = Size(binding.viewFinder.width, binding.viewFinder.height),
                overlayViewOrigin = Point(binding.viewOverlay.left, binding.viewOverlay.top)
            )
        }
    }

    private val imageProcessorController = object : ImageProcessor.Controller {
        override fun triggerFocusLock(fingerRect: RectF, cutoutRect: RectF, imageSize: Size) {
            lastKnownImageSize = imageSize
            cameraController.triggerFocusLock(
                fingerRect = fingerRect,
                cutoutRect = cutoutRect,
                imageSize = imageSize
            )
        }

        override fun triggerFocusUnlock() {
            cameraController.triggerFocusUnlock()
        }

        override fun triggerCapture() {
            cameraController.capturePhoto()
        }

        override suspend fun saveBitmap(bitmap: Bitmap, fileName: String): Uri {
            return viewModel.saveBitmapAndGetUri(bitmap = bitmap, captureName = fileName)
        }
    }

    private val imageProcessorListener = object : ImageProcessor.Listener {
        override fun onFingerMaskResult(mask: Bitmap?, rotation: Int) {

        }

        override fun onStartAccumulation() {
            viewModel.captureStateManager.reportIsAccumulationHappening(true)
        }

        override fun onStage1Error() {
            viewModel.captureStateManager.reportIsAccumulationHappening(false)
        }

        override fun onStage1Result(
            passed: Boolean,
            warnings: List<Warning>,
            passedChecks: List<ProcessingStage>
        ) {
            viewModel.captureStateManager.reportStage1Result(passed, warnings, passedChecks)
        }

        override fun onStage1ResultValues(values: LiveQualityScores) {
            viewModel.captureStateManager.reportStage1ResultValues(values)
        }

        override fun onStartStage2Processing() {
            viewModel.captureStateManager.reportIsStage2Processing(true)
        }

        override fun onStopStage2Processing() {
            viewModel.captureStateManager.reportIsStage2Processing(false)
        }

        override fun onStage2ProcessingStageUpdate(processingStage: ProcessingStage) {
            viewModel.captureStateManager.reportStage2ProcessingStage(processingStage)
        }

        override fun onStage2ResultValues(stage2ResultValue: Stage2ResultValue) {
            viewModel.captureStateManager.reportStage2ResultValues(stage2ResultValue)
        }

        override fun onStage2Result(passed: Boolean, errors: List<Error>) {
            viewModel.captureStateManager.reportStage2Result(passed, errors)
        }
    }

    private var canExitOnTimeout: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(requireContext()))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txnId = navArgs.txnId
        Log.d(TAG, "Transaction Id: $txnId")
        val versionName = packageManager(requireContext()).getPackageInfo(
            packageName(requireContext()),
            0
        ).versionName

        cameraController.setUIInfoProvider(uiInfoProvider)

        imageProcessor = imageProcessorFactory.create(
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            provider = imageProcessorProvider,
            controller = imageProcessorController,
            listener = imageProcessorListener
        )

        cameraController.setOnImageAvailableListener(imageProcessor)

        binding.bottomCameraController.setTransactionInfo(
            version = versionName,
            txnId = txnId
        )

        binding.headingOverlay.enablePulseOnTextChange()

        setupSurfaceView()
        setupButtonClickListeners()
        setupOnBackPressed()
        observeUIState()
        setupLiveQualityScoresDebugPanel()
        setupTapToFocus()
    }

    private fun setupLiveQualityScoresDebugPanel() {
        val isEnabled = preferenceStore.get(ProcessingSettings.SHOW_LIVE_QUALITY_SCORES)
        binding.viewLiveQualityScores.isVisible = isEnabled
        if (!isEnabled) return

        lifecycleScope.launch {
            viewModel.captureStateManager.stage1QualityScores.collectLatest { scores ->
                scores ?: return@collectLatest
                // Stage 1 emits at up to ~30fps; throttle UI redraws independently so the
                // debug panel doesn't become a rendering bottleneck.
                val now = SystemClock.uptimeMillis()
                if (now - lastLiveScoreUpdateMs < LIVE_SCORE_THROTTLE_MS) return@collectLatest
                lastLiveScoreUpdateMs = now
                binding.viewLiveQualityScores.bind(scores)
            }
        }
    }

    private fun setupSurfaceView() {
        binding.viewFinder.apply {
            holder.addCallback(
                object : SurfaceHolder.Callback {
                    override fun surfaceDestroyed(
                        holder: SurfaceHolder
                    ) = Unit

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) = Unit

                    override fun surfaceCreated(
                        holder: SurfaceHolder
                    ) {
                        permissionManager.requestPermissions(arrayOf(Manifest.permission.CAMERA)) { isGranted ->
                            if (isGranted) {
                                cameraController.initializeCamera()
                                viewModel.startSessionTimer()
                            } else {
                                Log.e(TAG, "Camera Permissions denied by the user")
                            }
                        }
                    }
                }
            )
            val previewSize = cameraController.getPreviewSize()
            setAspectRatio(
                width = previewSize.width,
                height = previewSize.height
            )
        }
    }

    private fun setupButtonClickListeners() {
        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.bottomSheetBiometric.setGoBackButtonClickListener {
            finish()
        }

        binding.bottomSheetBiometric.setRetakeButtonClickListener {
            viewModel.reset()
            imageProcessor?.reset()
            cameraController.retakeCapture()
        }

        binding.bottomCameraController.setCaptureButtonClickListener {
            Log.d("FLOW_TRACE", "1. Capture button tapped — calling unlockAccumulator()")
            imageProcessor?.unlockAccumulator()
        }

        binding.bottomCameraController.setTorchButtonClickListener {
            viewModel.updateTorchState()
            cameraController.updateTorchState()
        }

        binding.bottomCameraController.setOnCaptureModeChangeListener { isManual ->
            viewModel.updateManualCaptureState(isManual)

            imageProcessor?.close()
            imageProcessor = imageProcessorFactory.create(
                coroutineScope = viewLifecycleOwner.lifecycleScope,
                provider = imageProcessorProvider,
                controller = imageProcessorController,
                listener = imageProcessorListener
            )

            cameraController.setOnImageAvailableListener(imageProcessor)
        }
    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )
    }

    private fun observeUIState() {
        // Observe capture state
        lifecycleScope.launch {
            viewModel.captureState.collectLatest { state ->
                updateUI(state)
            }
        }

        // Observe Updated Capture State
        lifecycleScope.launch {
            viewModel.captureUIState.collectLatest { state ->
                binding.bottomCameraController.setCaptureMode(state.isManualCapture)
                binding.bottomCameraController.setTorchState(state.isTorchOn)
            }
        }

        // Observe session timeout events
        lifecycleScope.launch {
            viewModel.sessionTimeoutEvent.collectLatest {
                handleSessionTimeout()
            }
        }
    }

    private fun updateUI(state: CaptureState) {
        val heading = binding.headingOverlay
        val biometricOverlay = binding.viewOverlay
        val bottomSheet = binding.bottomSheetBiometric

        binding.bottomCameraController.setCaptureButtonEnabled(state is CaptureState.Initial)
        binding.bottomCameraController.enableCaptureModeToggling()

        when (state) {
            is CaptureState.Initial -> {
                canExitOnTimeout = true
                // White, Dashed, No animation, Bottom sheet hidden
                biometricOverlay.setState(OverlayView.State.INITIAL)
                val timePassed = viewModel.getSessionElapsedTime()

                if (timePassed > 3_000L) {
                    heading.animateToFitText(R.string.info_title_capture_now)
                } else {
                    heading.animateToFitText(R.string.heading_initial_state)
                }
                if (bottomSheet.isVisible) {
                    bottomSheet.hide()
                }
                // binding.btnCapture.isEnabled = true
                Log.d(TAG, "State: Initial")
            }

            is CaptureState.AutoCaptureTrigger -> {
                canExitOnTimeout = false
                // Amber, Dashed, With animation, Bottom sheet hidden
                binding.bottomCameraController.disableCaptureModeToggling()
                biometricOverlay.setState(
                    newState = OverlayView.State.PRE_AUTO_CAPTURE,
                    progressAnimationDuration = getProgressAnimationDuration()
                )
                heading.animateToFitText(state.info.titleRes)
                // bottomSheet.updateContent(state.info).show()
                Log.d(TAG, "State: Auto Capture Triggered")
            }

            is CaptureState.AutoCaptureSuccess -> {
                canExitOnTimeout = false
                // Green, Solid, No animation, Bottom sheet hidden
                binding.bottomCameraController.disableCaptureModeToggling()
                biometricOverlay.setState(OverlayView.State.AUTO_CAPTURE_SUCCESS)
                heading.animateToFitText(state.info.titleRes)
                // bottomSheet.updateContent(state.info).show()
                Log.d(TAG, "State: Auto Capture Success")
            }

            is CaptureState.Success -> {
                canExitOnTimeout = false
                // Green Solid, No animation
                binding.bottomCameraController.disableCaptureModeToggling()
                biometricOverlay.setState(OverlayView.State.SUCCESS)
                heading.animateToFitText(state.info.titleRes)
                bottomSheet.updateContent(state.info, state.resultValue).show()

                // Final Image Processing after Capture
                lifecycleScope.launch {
                    val segmentedFrame = imageProcessor?.getFinalFrame()
                    segmentedFrame?.let {
                        val encodedBitmap = viewModel.processImageAfterSuccess(it)
                        finish(
                            resultCode = ResultCode.CAPTURE_SUCCESS,
                            finalImage = encodedBitmap,
                            fullImage = it.fullBitmap.toBase64(),
                            croppedImage = it.croppedBitmap.toBase64()
                        )
                    } ?: let {
                        Log.e(TAG, "ACTIVITY_RESULT -- No image found")
                        finish(resultCode = ResultCode.CAPTURE_FAILED)
                    }
                }
            }

            is CaptureState.Failed -> {
                canExitOnTimeout = false
                // Red, Solid, No animation
                binding.bottomCameraController.disableCaptureModeToggling()
                biometricOverlay.setState(OverlayView.State.FAILURE)
                heading.animateToFitText(state.error.titleRes)
                bottomSheet.updateContent(state.error, state.resultValue).show()
                Log.d(TAG, "State: Failed")
            }

            is CaptureState.Warn -> {
                canExitOnTimeout = true
                // Red, Solid, No animation, Bottom sheet hidden
                biometricOverlay.setState(OverlayView.State.WARNING)
                heading.animateToFitText(state.warning.titleRes)
                // bottomSheet.updateContent(state.warning).showIfNotVisible()
                Log.d(TAG, "State: Warning")
            }
        }
    }

    fun getDeviceRotation(context: Context): Int {
        return try {
            val rotation = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    // Only works if context is Activity or visual
                    (context as? Activity)?.display?.rotation
                        ?: context.display.rotation
                }

                else -> {
                    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    @Suppress("DEPRECATION")
                    wm.defaultDisplay.rotation
                }
            }

            when (rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get device rotation", e)
            0
        }
    }

    fun getProgressAnimationDuration(): Long {
        return imageProcessor?.DELAY_IN_ACCUMULATION_OF_FRAMES?.minus(250)?.coerceIn(0L, 20_000)
            ?: 1000
    }

    private fun handleSessionTimeout() {
        if (canExitOnTimeout) {
            // Timeout
            Log.w(TAG, "Session timeout - showing toast and exiting")

            // Show toast notification
            Toast.makeText(
                requireContext(),
                "Session timed out. Please try again.",
                Toast.LENGTH_LONG
            ).show()

            finish(resultCode = ResultCode.CAPTURE_TIMEOUT)
        } else {
            // Extend timer by 10 seconds from now
            viewModel.resetSessionTimerTo(TIMER_EXTENSION_DURATION)
        }
    }

    fun closeAll() {
        viewModel.close()
        cameraController.setOnImageAvailableListener(null)
        cameraController.closeCamera()
        imageProcessor?.close()
        Log.i(TAG, "closeAll()")
    }

    override fun onStop() {
        super.onStop()
        closeAll()
        finish()
        Log.i(TAG, "onStop()")
    }

    private fun finish(
        resultCode: Int = ResultCode.CAPTURE_USER_ABORT,
        finalImage: String? = null,
        fullImage: String? = null,
        croppedImage: String? = null
    ) {
        val activity = activity ?: return
        if (!activity.isFinishing && !activity.isDestroyed) {
            Log.i(TAG, "finish()")
            requireActivity().supportFragmentManager.setFragmentResult(
                CAPTURE_FRAGMENT_RESULT,
                bundleOf(
                    (KEY_RESULT_CODE to resultCode),
                    (KEY_FINAL_IMAGE to finalImage),
                    (KEY_FULL_IMAGE to fullImage),
                    (KEY_CROPPED_IMAGE to croppedImage)
                )
            )
        }
    }

    private fun convertScreenTapToCroppedImageCoordinates(
        tapX: Float,
        tapY: Float,
        viewFinderSize: Size,
        imageSize: Size,
        totalRotation: Int,
        cutoutRectInFullImage: RectF
    ): PointF {
        val rotatedImageSize = when (totalRotation) {
            90, 270 -> Size(imageSize.height, imageSize.width)
            else -> imageSize
        }
        val scaleX = rotatedImageSize.width.toFloat() / viewFinderSize.width
        val scaleY = rotatedImageSize.height.toFloat() / viewFinderSize.height
        val xInRotated = tapX * scaleX
        val yInRotated = tapY * scaleY

        val pointInFullImage = when (totalRotation) {
            90 -> PointF(yInRotated, rotatedImageSize.width.toFloat() - xInRotated)
            180 -> PointF(
                rotatedImageSize.width.toFloat() - xInRotated,
                rotatedImageSize.height.toFloat() - yInRotated
            )
            270 -> PointF(rotatedImageSize.height.toFloat() - yInRotated, xInRotated)
            else -> PointF(xInRotated, yInRotated)
        }

        return PointF(
            pointInFullImage.x - cutoutRectInFullImage.left,
            pointInFullImage.y - cutoutRectInFullImage.top
        )
    }

    private fun setupTapToFocus() {
        binding.viewFinder.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val imageSize = lastKnownImageSize
                    if (imageSize == null) {
                        Log.w(TAG, "FOCUS -- Tap-to-focus ignored, no frame received yet")
                        return@setOnTouchListener true
                    }
                    val cutoutRect = imageProcessorProvider.getCutoutRectInImageCoordinates(
                        imageSize, imageProcessorProvider.totalRotation
                    )
                    val tapPointInCroppedImage = convertScreenTapToCroppedImageCoordinates(
                        tapX = event.x,
                        tapY = event.y,
                        viewFinderSize = Size(view.width, view.height),
                        imageSize = imageSize,
                        totalRotation = imageProcessorProvider.totalRotation,
                        cutoutRectInFullImage = cutoutRect
                    )
                    cameraController.handleTapToFocus(
                        tapPointInCroppedImage = tapPointInCroppedImage,
                        cutoutRect = cutoutRect,
                        imageSize = imageSize
                    )
                }
                MotionEvent.ACTION_UP -> {
                    view.performClick()
                }
            }
            true
        }
    }

    companion object {
        private val TAG = CameraFragment::class.java.simpleName

        private const val TIMER_EXTENSION_DURATION = 10_000L // 10 seconds
        private const val LIVE_SCORE_THROTTLE_MS = 150L

        const val CAPTURE_FRAGMENT_RESULT = "capture-fragment-result"
        const val KEY_RESULT_CODE = "result-code-key"
        const val KEY_FINAL_IMAGE = "final-image-key"
        const val KEY_FULL_IMAGE = "full-image-key"
        const val KEY_CROPPED_IMAGE = "cropped-image-key"
    }
}