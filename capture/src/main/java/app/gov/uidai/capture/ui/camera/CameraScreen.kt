package app.gov.uidai.capture.ui.camera

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.capture.utils.extension.toBase64
import `in`.gov.uidai.utility.constants.ResultCode

data class CaptureResult(
    val resultCode: Int,
    val finalImage: String? = null,
    val fullImage: String? = null,
    val croppedImage: String? = null,
    val blurScore: Float = 0f,
    val brightnessScore: Float = 0f,
    val glareScore: Float = 0f
)

@Composable
fun CameraScreen(
    txnId: String,
    cameraController: CameraController,
    imageProcessorFactory: ImageProcessorFactory,
    preferenceStore: PreferenceStore,
    onFinish: (CaptureResult) -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val captureSate by viewModel.captureState.collectAsStateWithLifecycle()
    val captureUIState by viewModel.captureUIState.collectAsStateWithLifecycle()
    val liveScores by viewModel.captureStateManager.stage1QualityScores.collectAsStateWithLifecycle()

    val cutoutBoundsHolder = remember { CutoutBoundsHolder() }
    var viewFinderSize by remember { mutableStateOf(Size(0, 0)) }
    var overlayOriginInParent by remember { mutableStateOf(Offset.Zero) }
    var lastKnownImageSize by remember { mutableStateOf<Size?>(null) }

    var imageProcessor by remember { mutableStateOf<ImageProcessor?>(null) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val uiInfoProvider = remember {
        object : CameraController.UIInfoProvider {
            override val viewFinderSurface: Surface get() = cameraController.camerSurface
            override val viewFinderSize: Size get() = viewFinderSize
            override val overlayViewCutoutRectCoordinates: RectF get() = cutoutBoundsHolder.rect
            override val deviceRotation: Int get() = getDeviceRotationCompat(context)
        }
    }

    val imageProcessorProvider = remember {
        object : ImageProcessor.Provider {
            override val totalRotation: Int
                get() = (cameraController.getSensorRotation() - getDeviceRotationCompat(
                    context
                ) + 360) % 360
            override val isFocusLockedForCapture: Boolean get() = cameraController.isFocusLockedForCapture()
            override val previewSize: Size get() = cameraController.getPreviewSize()
            override fun getCutoutRectInImageCoordinates(
                imageSize: Size,
                rotation: Int
            ): RectF {
                // cutoutBoundsHolder.rect is already in the SAME coordinate
                // space as the camera preview (both are direct children of
                // the same parent Box) — no separate origin offset needed,
                // unlike the old View version's left/top combination.
                return app.gov.uidai.capture.utils.getCutoutRectInImageCoordinates(
                    imageSize = imageSize,
                    totalRotation = rotation,
                    overlayViewCutoutRect = cutoutBoundsHolder.rect,
                    viewFinderSize = viewFinderSize,
                    overlayViewOrigin = Point(0, 0)  // already shared coordinate space
                )
            }
        }
    }

    val imageProcessorController = remember {
        object : ImageProcessor.Controller {
            override fun triggerFocusLock(
                fingerRect: RectF,
                cutoutRect: RectF,
                imageSize: Size
            ) {
                lastKnownImageSize = imageSize
                cameraController.triggerFocusLock(fingerRect, cutoutRect, imageSize)
            }

            override fun triggerFocusUnlock() = cameraController.triggerFocusUnlock()
            override fun triggerCapture() = cameraController.capturePhoto()
            override suspend fun saveBitmap(
                bitmap: Bitmap,
                fileName: String
            ): Uri = viewModel.saveBitmapAndGetUri(bitmap, fileName)
        }
    }

    val imageProcessorListener = remember {
        object : ImageProcessor.Listener {
            override fun onFingerMaskResult(mask: Bitmap?, rotation: Int) {}
            override fun onStartAccumulation() = viewModel.captureStateManager.reportIsAccumulationHappening(true)
            override fun onStage1Error() = viewModel.captureStateManager.reportIsAccumulationHappening(false)
            override fun onStage1Result(
                passed: Boolean,
                warnings: List<Warning>,
                passedChecks: List<ProcessingStage>
            ) = viewModel.captureStateManager.reportStage1Result(passed, warnings, passedChecks)
            override fun onStage1ResultValues(values: LiveQualityScores) = viewModel.captureStateManager.reportStage1ResultValues(values)
            override fun onStartStage2Processing() = viewModel.captureStateManager.reportIsStage2Processing(true)
            override fun onStopStage2Processing() = viewModel.captureStateManager.reportIsStage2Processing(false)
            override fun onStage2ProcessingStageUpdate(stage: ProcessingStage) =
                viewModel.captureStateManager.reportStage2ProcessingStage(stage)
            override fun onStage2ResultValues(value: Stage2ResultValue) =
                viewModel.captureStateManager.reportStage2ResultValues(value)
            override fun onStage2Result(passed: Boolean, errors: List<Error>) =
                viewModel.captureStateManager.reportStage2Result(passed, errors)
        }
    }

    fun finish(result: CaptureResult) = onFinish(result)

    BackHandler { finish(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) }

    LaunchedEffect(captureSate) {
        if(captureSate is CaptureState.Success) {
            val segmentedFrame = imageProcessor?.getFinalFrame()
            if(segmentedFrame != null) {
                val encodedBitmap = viewModel.processImageAfterSuccess(segmentedFrame)
                finish(
                    CaptureResult(
                        resultCode = ResultCode.CAPTURE_SUCCESS,
                        finalImage = encodedBitmap,
                        fullImage = segmentedFrame.fullBitmap.toBase64(),
                        croppedImage = segmentedFrame.croppedBitmap.toBase64(),
                        blurScore = segmentedFrame.blurScore,
                        brightnessScore = segmentedFrame.brightnessScore,
                        glareScore = segmentedFrame.glareScore
                    )
                )
            } else {
                finish(CaptureResult(resultCode = ResultCode.CAPTURE_FAILED))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) {
            CameraPreview(
                previewSize = cameraController.getPreviewSize(),
                onSurfaceReady = { holder ->
                    cameraController.currentSurface = holder.surface
                    cameraController.setUIInfoProvider(uiInfoProvider)
                    cameraController.initializeCamera()
                    viewModel.startSessionTimer()
                    imageProcessor = imageProcessorFactory.create(
                        coroutineScope = coroutineScope,
                        provider = imageProcessorProvider,
                        controller = imageProcessorController,
                        listener = imageProcessorListener
                    )
                    cameraController.setOnImageAvailableListener(imageProcessor)
                },
                onSurfaceDestroyed = {
                    viewModel.close()
                    cameraController.setOnImageAvailableListener(null)
                    cameraController.closeCamera()
                    imageProcessor?.close()
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LaunchedEffect(Unit) {
                // permission request wiring — same rememberLauncherForActivityResult
                // pattern PermissionManager used, hoisted at the call site
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A2A2A))
                        .then(Modifier), // clickable { finish(CaptureResult(ResultCode.CAPTURE_USER_ABORT)) }
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Heading chip — pulsing text per state, same strings as before
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShapeCompat(), color = Color.White.copy(alpha = 0.9f)) {
                    Text(
                        text = headingTextFor(captureState, viewModel),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                        color = Color.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The oval overlay — centered
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CaptureOverlay(
                    state = captureState.toOverlayVisualState(),
                    progressAnimationDurationMs = (imageProcessor?.DELAY_IN_ACCUMULATION_OF_FRAMES?.minus(250))
                        ?.coerceIn(0L, 20_000L) ?: 1000L,
                    cutoutBoundsHolder = cutoutBoundsHolder
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Bottom controller
            BottomCameraController(
                isManualCapture = captureUIState.isManualCapture,
                isTorchOn = captureUIState.isTorchOn,
                isCaptureEnabled = captureState is CaptureState.Initial,
                isModeToggleEnabled = captureState is CaptureState.Initial || captureState is CaptureState.Warn,
                version = captureUIState.version,
                txnId = txnId,
                onCaptureClick = { imageProcessor?.unlockAccumulator() },
                onTorchClick = { viewModel.updateTorchState(); cameraController.updateTorchState() },
                onModeChange = { isManual ->
                    viewModel.updateManualCaptureState(isManual)
                    imageProcessor?.close()
                    imageProcessor = imageProcessorFactory.create(
                        coroutineScope = coroutineScope,
                        provider = imageProcessorProvider,
                        controller = imageProcessorController,
                        listener = imageProcessorListener
                    )
                    cameraController.setOnImageAvailableListener(imageProcessor)
                }
            )
        }

        // Bottom sheet — shown for Success/Failed only, per traced OverlayView/CaptureStateManager logic
        val sheetState = captureState
        if (sheetState is CaptureState.Success || sheetState is CaptureState.Failed) {
            BottomSheetResult(
                captureState = sheetState,
                onRetake = {
                    viewModel.reset()
                    imageProcessor?.reset()
                    cameraController.retakeCapture()
                },
                onGoBack = { finish(CaptureResult(resultCode = app.gov.uidai.utility.constants.ResultCode.CAPTURE_USER_ABORT)) }
            )
        }
    }
}



















