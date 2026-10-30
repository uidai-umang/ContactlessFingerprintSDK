package app.gov.uidai.capture.ui.camera

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.BuildConfig
import app.gov.uidai.capture.domain.model.LiveCheckScore
import app.gov.uidai.capture.domain.model.LiveQualityScores
import app.gov.uidai.capture.domain.model.ProcessingStage
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.model.CaptureState
import app.gov.uidai.capture.ui.camera.model.Error
import app.gov.uidai.capture.ui.camera.model.Stage2ResultValue
import app.gov.uidai.capture.ui.camera.model.Warning
import app.gov.uidai.capture.ui.camera.slap.TorchIcon
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.factory.ImageProcessorFactory
import app.gov.uidai.capture.utils.CameraUtils
import app.gov.uidai.capture.utils.KotlinUtils.RoundedCornerShapeCompat
import app.gov.uidai.capture.utils.KotlinUtils.getDeviceRotationCompat
import app.gov.uidai.capture.utils.KotlinUtils.headingTextFor
import app.gov.uidai.registration.ui.theme.Spacing
import `in`.gov.uidai.utility.constants.ResultCode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample

/** Handed to the capture loop in memory -- no Intent / file hand-off. */
data class CaptureResult(
    val resultCode: Int,
    val finalImage: Bitmap? = null,
    val blurScore: Float = 0f,
    val brightnessScore: Float = 0f,
    val glareScore: Float = 0f
)

@Composable
fun CameraScreen(
    fingerType: String,
    captureId: Int,
    cameraController: CameraController,
    imageProcessorFactory: ImageProcessorFactory,
    preferenceStore: PreferenceStore,
    onFinish: (CaptureResult) -> Unit,
    onPopBackStack: () -> Unit,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val coroutineScope = rememberCoroutineScope()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val captureUIState by viewModel.captureUIState.collectAsStateWithLifecycle()
    val cutoutBoundsHolder = remember { CutoutBoundsHolder() }
    var viewFinderSize by remember { mutableStateOf(Size(0, 0)) }
    var lastKnownImageSize by remember { mutableStateOf<Size?>(null) }

    var imageProcessor by remember { mutableStateOf<ImageProcessor?>(null) }

    var showReviewScreen by remember { mutableStateOf(false) }
    var pendingCaptureResult by remember { mutableStateOf<CaptureResult?>(null) }
    var reviewBitmap by remember { mutableStateOf<Bitmap?>(null) }


    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // NEW -- permission request flow for the camera permission
    var showPermanentlyDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            val canShowRationale = activity?.let {
                androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                    it, android.Manifest.permission.CAMERA
                )
            } ?: true
            if (!canShowRationale) {
                showPermanentlyDeniedDialog = true
            } else {
                // Simply popping back on denial, per requirement -- no
                // forced re-request loop here since Activity-level
                // checkAndRequestPermissions already handles first-ask.
                onPopBackStack()
            }
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
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

            override fun getYoloFingerDistanceMM(
                fingerBoxUpright: RectF,
                uprightImageSize: Size,
                rotationDegrees: Int
            ): Float = cameraController.getSingleYoloDistanceMM(
                fingerBoxUpright,
                uprightImageSize,
                rotationDegrees
            )
        }
    }

    val imageProcessorListener = remember {
        object : ImageProcessor.Listener {
            override fun onFingerMaskResult(mask: Bitmap?, rotation: Int) {}
            override fun onStartAccumulation() =
                viewModel.captureStateManager.reportIsAccumulationHappening(true)

            override fun onStage1Error() =
                viewModel.captureStateManager.reportIsAccumulationHappening(false)

            override fun onStage1Result(
                passed: Boolean,
                warnings: List<Warning>,
                passedChecks: List<ProcessingStage>
            ) = viewModel.captureStateManager.reportStage1Result(passed, warnings, passedChecks)

            override fun onStage1ResultValues(values: LiveQualityScores) =
                viewModel.captureStateManager.reportStage1ResultValues(values)

            override fun onStartStage2Processing() =
                viewModel.captureStateManager.reportIsStage2Processing(true)

            override fun onStopStage2Processing() =
                viewModel.captureStateManager.reportIsStage2Processing(false)

            override fun onStage2ProcessingStageUpdate(stage: ProcessingStage) =
                viewModel.captureStateManager.reportStage2ProcessingStage(stage)

            override fun onStage2ResultValues(value: Stage2ResultValue) =
                viewModel.captureStateManager.reportStage2ResultValues(value)

            override fun onStage2Result(passed: Boolean, errors: List<Error>) =
                viewModel.captureStateManager.reportStage2Result(passed, errors)
        }
    }

    fun finish(result: CaptureResult) = onFinish(result)

    fun restartCapture() {
        imageProcessor?.close()
        val newProcessor = imageProcessorFactory.create(
            coroutineScope = coroutineScope,
            provider = imageProcessorProvider,
            controller = imageProcessorController,
            listener = imageProcessorListener
        )
        imageProcessor = newProcessor
        cameraController.setOnImageAvailableListener(newProcessor)
        cameraController.retakeCapture()
    }

    BackHandler { finish(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) }

    // Next capture in the sequence: reset camera/processor state in place
    // instead of leaving the screen. The first capture needs no reset.
    var lastCaptureId by remember { mutableStateOf(captureId) }
    LaunchedEffect(captureId) {
        if (captureId == lastCaptureId) return@LaunchedEffect
        lastCaptureId = captureId
        showReviewScreen = false
        reviewBitmap = null
        pendingCaptureResult = null
        viewModel.reset()
        restartCapture()
    }

    LaunchedEffect(captureState) {
        if (captureState is CaptureState.Success) {
            val segmentedFrame = imageProcessor?.getFinalFrame()
            if (segmentedFrame == null) {
                showReviewScreen = false
                finish(CaptureResult(resultCode = ResultCode.CAPTURE_FAILED))
                return@LaunchedEffect
            }
            if (BuildConfig.DEBUG) {
                showReviewScreen = true
                reviewBitmap = null
            }
            val finalBitmap = viewModel.processImageAfterSuccess(segmentedFrame)
            val result = CaptureResult(
                resultCode = ResultCode.CAPTURE_SUCCESS,
                finalImage = finalBitmap,
                blurScore = segmentedFrame.blurScore,
                brightnessScore = segmentedFrame.brightnessScore,
                glareScore = segmentedFrame.glareScore
            )
            if (BuildConfig.DEBUG) {
                pendingCaptureResult = result
                reviewBitmap = segmentedFrame.croppedBitmap
            } else {
                finish(result)   // release: straight back to the sequence driver
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
                        onTapToFocus = { tapOffset, previewViewSize ->
                            // Tap arrives in Compose-Box pixel coordinates. handleTapToFocus
                            // expects a point in the same "cropped image" coordinate space
                            // getCutoutRectInImageCoordinates already establishes -- so we
                            // reuse imageProcessorProvider's own conversion rather than
                            // inventing a second one.
                            val imageSize = lastKnownImageSize ?: return@CameraPreview
                            val cutoutRect = imageProcessorProvider.getCutoutRectInImageCoordinates(
                                imageSize, imageProcessorProvider.totalRotation
                            )
                            val tapPointInCroppedImage =
                                CameraUtils.convertScreenTapToCroppedImageCoordinates(
                                    tapX = tapOffset.x, tapY = tapOffset.y,
                                    viewFinderSize = previewViewSize, imageSize = imageSize,
                                    totalRotation = imageProcessorProvider.totalRotation,
                                    cutoutRectInFullImage = cutoutRect
                                )
                            cameraController.handleTapToFocus(
                                tapPointInCroppedImage,
                                cutoutRect,
                                imageSize
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LaunchedEffect(Unit) {
                        // permission request wiring — same rememberLauncherForActivityResult
                        // pattern PermissionManager used, hoisted at the call site
                    }
                }

                CaptureOverlay(
                    state = captureState.toOverlayVisualState(),
                    progressAnimationDurationMs = (imageProcessor?.DELAY_IN_ACCUMULATION_OF_FRAMES?.minus(
                        250
                    ))
                        ?.coerceIn(0L, 20_000L) ?: 1000L,
                    cutoutBoundsHolder = cutoutBoundsHolder
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    // Top bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.L)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                                .then(Modifier), // clickable { finish(CaptureResult(ResultCode.CAPTURE_USER_ABORT)) }
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    // Heading chip — pulsing text per state, same strings as before
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.Xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShapeCompat(),
                            color = Color.Black.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = headingTextFor(captureState),
                                modifier = Modifier.padding(
                                    horizontal = Spacing.Xxl,
                                    vertical = Spacing.S
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = captureState.toOverlayVisualState().color
                            )
                        }
                    }

                    Spacer(modifier = Modifier.size(Spacing.L))

                    QualityStatusBadges(
                        scoresFlow = viewModel.captureStateManager.stage1QualityScores,
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(
                                Alignment.CenterHorizontally
                            )
                    )
                }



                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.L),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (captureState is CaptureState.AutoCaptureSuccess) {
                        Surface(
                            shape = RoundedCornerShapeCompat(),
                            color = Color.Black.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "You can remove your finger",
                                modifier = Modifier.padding(
                                    horizontal = Spacing.Xxl,
                                    vertical = Spacing.S
                                ),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = captureState.toOverlayVisualState().color
                            )
                        }
                        Spacer(modifier = Modifier.height(Spacing.Xxxl))
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                Color.White.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = Spacing.L, vertical = Spacing.S)
                    ) {
                        Text(
                            text = fingerType.replace("_", " "),
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                TorchIcon(
                    isTorchOn = captureUIState.isTorchOn,
                    onTorchClick = { viewModel.updateTorchState(); cameraController.updateTorchState() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = Spacing.L, end = Spacing.L)
                )
            }
        }

        if (showReviewScreen) {
            CaptureReviewScreen(
                bitmap = reviewBitmap,
                blurScore = pendingCaptureResult?.blurScore ?: 0f,
                brightnessScore = pendingCaptureResult?.brightnessScore ?: 0f,
                glareScore = pendingCaptureResult?.glareScore ?: 0f,
                onAccept = {
                    pendingCaptureResult?.let { finish(it) }
                },
                onReject = {
                    Log.w("RETRY_LOOP", "onReject fired @ ${SystemClock.uptimeMillis()}")
                    showReviewScreen = false
                    reviewBitmap = null
                    pendingCaptureResult = null
                    viewModel.reset()
                    restartCapture()
                }
            )
        }

        // Bottom sheet — shown for Failed only, as Success is now handled by CaptureReviewScreen
        val sheetState = captureState
        if (sheetState is CaptureState.Failed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter)
            ) {
                BottomSheetResult(
                    captureState = sheetState,
                    onRetake = {
                        Log.w("RETRY_LOOP", "onRetry fired @ ${SystemClock.uptimeMillis()}")
                        viewModel.reset()
                        restartCapture()
                    },
                    onGoBack = { finish(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) }
                )
            }
        }

        if (showPermanentlyDeniedDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { /* must choose */ },
                title = { Text("Camera Permission Required") },
                text = {
                    Text(
                        "Camera access has been denied. Please enable it in " +
                                "Settings to continue using the fingerprint capture SDK."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showPermanentlyDeniedDialog = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        showPermanentlyDeniedDialog = false
                        onPopBackStack()
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetResult(captureState: CaptureState, onRetake: () -> Unit, onGoBack: () -> Unit) {
    val isSuccess = captureState is CaptureState.Success
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // NEW — pull the real title/description from the reported Error,
    // instead of hardcoded generic text. Falls back to the old generic
    // strings only if captureState isn't Failed (i.e. the Success case).
    val titleText = when (captureState) {
        is CaptureState.Failed -> stringResource(captureState.error.titleRes)
        else -> "Capture Successful"
    }
    val descriptionText = (captureState as? CaptureState.Failed)?.let {
        stringResource(it.error.descriptionRes)
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isSuccess) {
            } else {
                onGoBack()
            }
        },
        sheetState = sheetState,
        containerColor = Color.White,
        contentColor = Color(0xFF1A56A0),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF1A56A0).copy(alpha = 0.4f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    2.dp,
                    Color(0xFF1A56A0),
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                )
                .padding(Spacing.Xl)
                .padding(bottom = Spacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFF1A56A0),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(Spacing.S))
            Text(
                text = titleText,
                color = Color(0xFF1A56A0),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            // NEW — description line, only shown for failures where one exists
            descriptionText?.let {
                Spacer(modifier = Modifier.height(Spacing.Xxs))
                Text(
                    text = it,
                    color = Color(0xFF1A56A0).copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(Spacing.L))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M)
            ) {
                if (!isSuccess) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF1A56A0)),
                        border = BorderStroke(1.dp, Color(0xFF1A56A0))
                    ) { Text("Retake") }
                }
                Button(
                    onClick = {
                        if (isSuccess) {
                        } else {
                            onGoBack()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A56A0))
                ) { Text(if (isSuccess) "Done" else "Go Back") }
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
                .padding(horizontal = Spacing.S, vertical = Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xxs)
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
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (score.passed) Color(0xFF16A34A) else Color.Transparent)
        )

        if (score.showValues) {
            val valueText = if (score.acceptedMax == Float.MAX_VALUE) {
                "%.2f (min: %.2f)".format(score.currentValue, score.acceptedMin)
            } else {
                "%.2f (%.2f, %.2f)".format(score.currentValue, score.acceptedMin, score.acceptedMax)
            }
            Text(text = "${score.label}: $valueText", color = Color.White, fontSize = 10.sp)
        } else {
            Text(text = score.label, color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun CaptureReviewScreen(
    bitmap: Bitmap?,
    blurScore: Float,
    brightnessScore: Float,
    glareScore: Float,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(5) }

    LaunchedEffect(bitmap) {
        if (bitmap == null) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        onAccept()
    }

    // Zoom/pan state for the reviewed image only
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val minScale = 1f
    val maxScale = 5f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                            // Only accumulate pan while genuinely zoomed in --
                            // avoids the image drifting off-screen at 1x.
                            offset = if (newScale > 1f) {
                                offset + pan
                            } else {
                                Offset.Zero
                            }
                            scale = newScale
                        }
                    }
            ) {
                if (bitmap == null) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .align(Alignment.Center),
                        strokeWidth = 4.dp
                    )
                } else {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                    // Reset-zoom affordance -- only visible once actually zoomed,
                    // since double-tap-to-reset is easy to miss otherwise.
                    if (scale > 1f) {
                        IconButton(
                            onClick = { scale = 1f; offset = Offset.Zero },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(Spacing.M)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomOut,
                                contentDescription = "Reset zoom",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A))
                    .padding(Spacing.Xl)
            ) {
                Text(
                    "Review Capture",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.S))
                Text(
                    "Blur: %.2f".format(blurScore),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    "Brightness: %.2f".format(brightnessScore),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Text(
                    "Glare: %.2f".format(glareScore),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(Spacing.L))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color.White)
                        ) { Text("Retry") }
                        Spacer(modifier = Modifier.height(Spacing.Xxs))
                        Text(
                            text = "Retry in ${secondsLeft}s",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                    ) { Text("Accept") }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    isValid: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0x991E2022),
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    color = if (isValid) Color(0xFF00E676) else Color(0xFFFF5252),
                    shape = CircleShape
                )
        )
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(FlowPreview::class)
@Composable
fun QualityStatusBadges(
    scoresFlow: StateFlow<LiveQualityScores?>,
    modifier: Modifier = Modifier
) {
    // Own collector, so the ~30fps score updates recompose only this, not CameraScreen.
    var scores by remember { mutableStateOf<LiveQualityScores?>(null) }
    LaunchedEffect(scoresFlow) {
        scoresFlow.filterNotNull().sample(LIVE_SCORE_THROTTLE_MS).collectLatest { scores = it }
    }
    val s = scores ?: LiveQualityScores.defaultScores()

    Column(
        modifier = modifier.padding(horizontal = Spacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            Spacing.S, Alignment.Top
        )
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(text = s.blur.label, isValid = s.blur.passed)
            StatusBadge(text = s.glare.label, isValid = s.glare.passed)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusBadge(text = s.fingerDetected.label, isValid = s.fingerDetected.passed)
            StatusBadge(text = s.brightness.label, isValid = s.brightness.passed)
        }
    }
}
