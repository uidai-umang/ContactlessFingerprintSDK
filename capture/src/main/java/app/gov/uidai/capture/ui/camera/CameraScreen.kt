package app.gov.uidai.capture.ui.camera

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.domain.model.LiveCheckScore
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ProcessingSettings
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.capture.utils.KotlinUtils.RoundedCornerShapeCompat
import app.gov.uidai.capture.utils.KotlinUtils.getDeviceRotationCompat
import app.gov.uidai.capture.utils.KotlinUtils.headingTextFor
import app.gov.uidai.capture.utils.extension.toBase64
import `in`.gov.uidai.utility.constants.ResultCode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample

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
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val captureUIState by viewModel.captureUIState.collectAsStateWithLifecycle()
    val showLiveScores = remember { preferenceStore.get(ProcessingSettings.SHOW_LIVE_QUALITY_SCORES) }
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
            override val viewFinderSurface: Surface get() = cameraController.currentSurface!!
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
                Log.d("CutoutDebug", "REAL viewFinderSize (Compose state) = $viewFinderSize")
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

    LaunchedEffect(captureState) {
        if(captureState is CaptureState.Success) {
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
                    val newProcessor = imageProcessorFactory.create(
                        coroutineScope = coroutineScope,
                        provider = imageProcessorProvider,
                        controller = imageProcessorController,
                        listener = imageProcessorListener
                    )
                    imageProcessor = newProcessor
                    cameraController.setOnImageAvailableListener(newProcessor)
                },
                onSurfaceDestroyed = {
                    viewModel.close()
                    cameraController.setOnImageAvailableListener(null)
                    cameraController.closeCamera()
                    imageProcessor?.close()
                },
                onSizeChanged = { size -> viewFinderSize = size },
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

            // Heading chip — pulsing text per state, same strings as before
            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                Surface(shape = RoundedCornerShapeCompat(), color = Color.White.copy(alpha = 0.9f)) {
                    Text(
                        text = headingTextFor(captureState, viewModel),
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                        color = Color.Black
                    )
                }
            }

            // The oval overlay — centered
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CaptureOverlay(
                    state = captureState.toOverlayVisualState(),
                    progressAnimationDurationMs = (imageProcessor?.DELAY_IN_ACCUMULATION_OF_FRAMES?.minus(250))
                        ?.coerceIn(0L, 20_000L) ?: 1000L,
                    cutoutBoundsHolder = cutoutBoundsHolder
                )
            }


            if (showLiveScores) {
                Box(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp), contentAlignment = Alignment.BottomStart) {
                    LiveQualityScoresPanel(scoresFlow = viewModel.captureStateManager.stage1QualityScores)
                }
            }

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
                    val newProcessor = imageProcessorFactory.create(
                        coroutineScope = coroutineScope,
                        provider = imageProcessorProvider,
                        controller = imageProcessorController,
                        listener = imageProcessorListener
                    )
                    imageProcessor = newProcessor
                    cameraController.setOnImageAvailableListener(newProcessor)   // was passing the stale imageProcessor read
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
                onGoBack = { finish(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) }
            )
        }
    }
}

@Composable
fun BottomCameraController(
    isManualCapture: Boolean, isTorchOn: Boolean, isCaptureEnabled: Boolean,
    isModeToggleEnabled: Boolean, version: String, txnId: String,
    onCaptureClick: () -> Unit, onTorchClick: () -> Unit, onModeChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).padding(20.dp)) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { onModeChange(false) }, enabled = isModeToggleEnabled) { Text("Auto") }
            Button(onClick = { onModeChange(true) }, enabled = isModeToggleEnabled) { Text("Manual") }
        }
        Button(onClick = onCaptureClick, enabled = isCaptureEnabled) { Text("Capture") }
        IconButton(onClick = onTorchClick) { Icon(Icons.Default.FlashOn, "Torch") }
        Text("Ver: $version | TxnId: $txnId", color = Color.White.copy(alpha = 0.5f), fontSize = 9.sp)
    }
}

@Composable
fun BottomSheetResult(captureState: CaptureState, onRetake: () -> Unit, onGoBack: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShapeCompat()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(if (captureState is CaptureState.Success) "Success" else "Failed")
            Row {
                Button(onClick = onRetake) { Text("Retake") }
                TextButton(onClick = onGoBack) { Text("Go Back") }
            }
        }
    }
}

private const val LIVE_SCORE_THROTTLE_MS = 150L

@OptIn(FlowPreview::class)
@Composable
fun LiveQualityScoresPanel(
    scoresFlow: StateFlow<LiveQualityScores?>,
    modifier: Modifier = Modifier
) {
    // Isolated collection + throttling — this composable recomposes at
    // ~150ms max, NOT ~30fps, and only THIS composable recomposes, not
    // the whole CameraScreen (the flow is passed in, not collected above).
    var scores by remember { mutableStateOf<LiveQualityScores?>(null) }
    LaunchedEffect(scoresFlow) {
        scoresFlow.filterNotNull().sample(LIVE_SCORE_THROTTLE_MS).collectLatest { scores = it }
    }

    scores?.let { s ->
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            LiveScoreRow(s.blur)
            LiveScoreRow(s.brightness)
            LiveScoreRow(s.glare)
            LiveScoreRow(s.fingerDetected)
        }
    }
}

@Composable
private fun LiveScoreRow(score: LiveCheckScore) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (score.passed) Color(0xFF16A34A) else Color.Transparent)
        )
        val valueText = if (score.acceptedMax == Float.MAX_VALUE) {
            "%.2f (min: %.2f)".format(score.currentValue, score.acceptedMin)
        } else {
            "%.2f (%.2f, %.2f)".format(score.currentValue, score.acceptedMin, score.acceptedMax)
        }
        Text(text = "${score.label}: $valueText", color = Color.White, fontSize = 10.sp)
    }
}