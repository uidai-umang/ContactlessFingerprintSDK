package app.gov.uidai.capture.ui.camera.slap

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.RectF
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gov.uidai.capture.ui.camera.CameraController
import app.gov.uidai.capture.ui.camera.CameraPreview
import app.gov.uidai.capture.ui.camera.CaptureResult
import app.gov.uidai.capture.usecase.slap.SlapLiveState
import app.gov.uidai.capture.utils.KotlinUtils.getDeviceRotationCompat
import app.gov.uidai.capture.utils.extension.toBase64
import `in`.gov.uidai.utility.constants.ResultCode

private val PageBackground = Color(0xFF0A0D14)
private val IdleBorder = Color(0xFF253447)
private val IdleFill = Color(0xFF151F2E)
private val CheckingColor = Color(0xFFEAB308)
private val ReadyColor = Color(0xFF16A34A)

private enum class SlapButtonState { IDLE, CHECKING, READY }

@Composable
fun SlapCaptureRoute(
    handType: String,
    onFinish: (CaptureResult) -> Unit,
    onPopBackStack: () -> Unit,
    viewModel: SlapCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    var viewFinderSize by remember { mutableStateOf(Size(0, 0)) }

    LaunchedEffect(handType) { viewModel.setExpectedHandType(handType) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
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
            if (!canShowRationale) showPermanentlyDeniedDialog = true else onPopBackStack()
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) permissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    BackHandler { onFinish(CaptureResult(resultCode = ResultCode.CAPTURE_USER_ABORT)) }

    LaunchedEffect(capturedBitmap) {
        val bitmap = capturedBitmap ?: return@LaunchedEffect
        val encoded = bitmap.toBase64()
        onFinish(
            CaptureResult(
                resultCode = ResultCode.CAPTURE_SUCCESS,
                finalImage = encoded,
                fullImage = encoded,
                croppedImage = encoded
            )
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(PageBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (hasCameraPermission) {
                    CameraPreview(
                        previewSize = viewModel.cameraController.getPreviewSize(),
                        onSurfaceReady = { holder ->
                            val controller = viewModel.cameraController
                            controller.currentSurface = holder.surface
                            controller.setUIInfoProvider(object : CameraController.UIInfoProvider {
                                override val viewFinderSurface: Surface get() = controller.currentSurface!!
                                override val viewFinderSize: Size get() = viewFinderSize
                                // No guide-overlay/cutout box in this simple design --
                                // the full viewfinder stands in for it (used by
                                // CameraController only for AF-distance heuristics).
                                override val overlayViewCutoutRectCoordinates: RectF
                                    get() = RectF(0f, 0f, viewFinderSize.width.toFloat(), viewFinderSize.height.toFloat())
                                override val deviceRotation: Int get() = getDeviceRotationCompat(context)
                            })
                            controller.initializeCamera()
                            val listener = viewModel.getOrCreateListener {
                                (controller.getSensorRotation() - getDeviceRotationCompat(context) + 360) % 360
                            }
                            controller.setOnImageAvailableListener(listener)
                        },
                        onSurfaceDestroyed = {
                            viewModel.cameraController.setOnImageAvailableListener(null)
                            viewModel.cameraController.closeCamera()
                        },
                        onSizeChanged = { size -> viewFinderSize = size },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Fingertip markers -- redrawn at the latest positions every
                // processed frame, no smoothing/animation.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (liveState.uprightFrameWidth > 0 && liveState.uprightFrameHeight > 0) {
                        val scaleX = size.width / liveState.uprightFrameWidth.toFloat()
                        val scaleY = size.height / liveState.uprightFrameHeight.toFloat()
                        val squareSizePx = 18.dp.toPx()
                        val markerColor = if (liveState.isReady) ReadyColor else CheckingColor
                        liveState.fingertips.forEach { point ->
                            val cx = point.x * scaleX
                            val cy = point.y * scaleY
                            drawRect(
                                color = markerColor,
                                topLeft = Offset(cx - squareSizePx / 2, cy - squareSizePx / 2),
                                size = ComposeSize(squareSizePx, squareSizePx),
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.9f)) {
                            Text(
                                text = liveState.statusMessage,
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 9.dp),
                                color = if (liveState.isReady) ReadyColor else Color.Black
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${handType} hand · 4-finger slap",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SlapBottomBar(
                buttonState = when {
                    liveState.isReady -> SlapButtonState.READY
                    liveState.statusMessage == "Hold steady" -> SlapButtonState.CHECKING
                    else -> SlapButtonState.IDLE
                },
                statusMessage = liveState.statusMessage,
                isTorchOn = isTorchOn,
                onTorchClick = { viewModel.toggleTorch() }
            )
        }

        if (showPermanentlyDeniedDialog) {
            AlertDialog(
                onDismissRequest = { },
                title = { Text("Camera Permission Required") },
                text = { Text("Camera access has been denied. Please enable it in Settings to continue using the fingerprint capture SDK.") },
                confirmButton = {
                    TextButton(onClick = {
                        showPermanentlyDeniedDialog = false
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showPermanentlyDeniedDialog = false
                        onPopBackStack()
                    }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun SlapBottomBar(
    buttonState: SlapButtonState,
    statusMessage: String,
    isTorchOn: Boolean,
    onTorchClick: () -> Unit
) {
    val (borderColor, fillColor, dotColor) = when (buttonState) {
        SlapButtonState.IDLE -> Triple(IdleBorder, IdleFill, null)
        SlapButtonState.CHECKING -> Triple(CheckingColor, IdleFill, CheckingColor)
        SlapButtonState.READY -> Triple(ReadyColor, IdleFill, ReadyColor)
    }
    val labelColor = when (buttonState) {
        SlapButtonState.IDLE -> Color.White.copy(alpha = 0.5f)
        SlapButtonState.CHECKING -> CheckingColor
        SlapButtonState.READY -> ReadyColor
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(PageBackground).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(42.dp)) // spacer to balance the torch button on the right

            // Auto-only for v1 -- this circle is a status indicator, not a
            // button. TODO: wire a manual-capture tap target here if/when
            // manual mode is added for slap capture.
            Box(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                if (buttonState == SlapButtonState.READY) {
                    // Soft glow approximation -- a larger, low-alpha circle behind the button.
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(ReadyColor.copy(alpha = 0.25f))
                    )
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(fillColor),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = borderColor, style = Stroke(width = 3.dp.toPx()))
                    }
                    dotColor?.let {
                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(it))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.07f))
                    .clickable(onClick = onTorchClick),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawCircle(color = Color.White.copy(alpha = 0.1f), style = Stroke(width = 1.dp.toPx()))
                }
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "Torch",
                    tint = if (isTorchOn) Color(0xFFEAB308) else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = statusMessage,
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
