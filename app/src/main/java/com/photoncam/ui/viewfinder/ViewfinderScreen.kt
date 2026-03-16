package com.photoncam.ui.viewfinder

import android.content.Intent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import android.view.WindowManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.photoncam.camera.CameraParams
import com.photoncam.camera.LensInfo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmStock
import com.photoncam.processing.DateImprintColor
import com.photoncam.processing.DateImprintFont
import com.photoncam.processing.DateImprintPosition
import com.photoncam.processing.DateImprintSize
import com.photoncam.processing.DateImprintStyle
import com.photoncam.ui.filmselect.FilmSelectorSheet

// ── Camera body palette ───────────────────────────────────────────────────────
private val Body          = Color(0xFF181818)
private val BodyLeather   = Color(0xFF1E1A17)  // warm dark brown-black
private val BodyMetal     = Color(0xFF242424)
private val TopPlate      = Color(0xFF222222)
private val ChromeHi      = Color(0xFF8C8C8C)
private val ChromeLo      = Color(0xFF505050)
private val AmberLcd      = Color(0xFFFF9900)
private val LcdBg         = Color(0xFF080808)
private val BtnTop        = Color(0xFF2E2E2E)
private val BtnBot        = Color(0xFF141414)
private val BtnBorder     = Color(0xFF383838)
private val SepLine       = Color(0xFF2C2C2C)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ViewfinderScreen(viewModel: ViewfinderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    val previewView = remember { PreviewView(context) }
    // Use selectedLens?.id (String) as key — CameraSelector doesn't implement equals()
    // so using the full LensInfo would fail to detect lens changes reliably.
    LaunchedEffect(lifecycleOwner, uiState.selectedLens?.id) {
        if (cameraPermission.status.isGranted) {
            viewModel.bindCamera(lifecycleOwner, previewView)
        }
    }

    // Screen flash: max brightness when active (front-camera flash simulation)
    val activity = LocalContext.current as? Activity
    LaunchedEffect(uiState.screenFlashActive) {
        val lp = activity?.window?.attributes ?: return@LaunchedEffect
        lp.screenBrightness = if (uiState.screenFlashActive) 1f
                              else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }

    // Re-apply zoom ratio when the app returns to foreground.
    // LaunchedEffect(selectedLens?.id) won't re-fire on resume since the key hasn't changed,
    // so CameraX restores the preview but zoom stays at 1x without this observer.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reapplyZoom()
                viewModel.reapplyEv()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val viewableUri = uiState.lastCapturedUri ?: uiState.latestGalleryUri

    val onShare: () -> Unit = {
        viewableUri?.let { uri ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/jpeg"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share photo",
                )
            )
        }
    }

    val onView: () -> Unit = {
        viewableUri?.let { uri ->
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/jpeg")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Body),
    ) {
        // Leatherette texture layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dotSpacing = 10.dp.toPx()
            val dotR = 1.dp.toPx()
            var row = 0
            var dy = 0f
            while (dy < size.height) {
                val offsetX = if (row % 2 == 0) 0f else dotSpacing / 2f
                var dx = offsetX
                while (dx < size.width) {
                    drawCircle(
                        color = Color(0x12FFFFFF),
                        radius = dotR,
                        center = Offset(dx, dy),
                    )
                    dx += dotSpacing
                }
                dy += dotSpacing * 0.87f
                row++
            }
        }

        // ── Main landscape layout ─────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxSize()) {

            // Top plate strip
            CameraTopPlate(film = uiState.selectedFilm)

            // Main body
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                // ── LEFT: viewfinder area (60%) ───────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1.6f)
                        .fillMaxHeight()
                        .background(BodyLeather),
                ) {
                    // Viewfinder window — inset from body edges
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.88f)
                            .fillMaxHeight(0.90f),
                    ) {
                        ViewfinderWindow(
                            uiState = uiState,
                            previewView = previewView,
                            cameraGranted = cameraPermission.status.isGranted,
                            onTap = { x, y ->
                                viewModel.tapToFocus(x, y, previewView.meteringPointFactory)
                            },
                        )
                    }

                    // Rivet decorations
                    Rivet(modifier = Modifier.align(Alignment.TopStart).padding(6.dp))
                    Rivet(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                    Rivet(modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
                    Rivet(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                }

                // Thin separator line
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(SepLine))

                // ── RIGHT: controls panel (40%) ───────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(BodyMetal)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Flash (left) / D-pad / Shutter right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        FlashMiniButton(
                            enabled = uiState.flashEnabled,
                            isFrontCamera = uiState.selectedLens?.id == "camera_front",
                            onClick = viewModel::toggleFlash,
                        )
                        DPadControl(
                            accentColor = uiState.selectedFilm.accentColor,
                            canEvUp = uiState.evRange?.let { uiState.evIndex < it.upper } ?: false,
                            canEvDown = uiState.evRange?.let { uiState.evIndex > it.lower } ?: false,
                            hasMultipleLenses = uiState.availableLenses.size > 1,
                            onEvUp = { viewModel.adjustExposure(+1) },
                            onEvDown = { viewModel.adjustExposure(-1) },
                            onPrevLens = viewModel::selectPrevLens,
                            onNextLens = viewModel::selectNextLens,
                        )
                        ShutterButton(
                            accentColor = uiState.selectedFilm.accentColor,
                            isCapturing = uiState.isCapturing,
                            processingCount = uiState.processingCount,
                            onShutter = viewModel::capture,
                        )
                    }

                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(SepLine))

                    // LCD readout (EV + PROC + SHOT)
                    LcdReadout(uiState = uiState)

                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(SepLine))

                    // Lens selector pills
                    LensSelectorRow(
                        lenses = uiState.availableLenses,
                        selected = uiState.selectedLens,
                        accentColor = uiState.selectedFilm.accentColor,
                        onSelect = viewModel::selectLens,
                    )

                    // Zoom slider — visible only on the 1× lens
                    if (uiState.selectedLens?.id == "zoom_main") {
                        ZoomSliderRow(
                            zoomRatio = uiState.mainZoomRatio,
                            accentColor = uiState.selectedFilm.accentColor,
                            onZoomChange = viewModel::setMainZoomRatio,
                            onZoomChangeFinished = viewModel::commitMainZoomRatio,
                        )
                    }

                    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(SepLine))

                    // Action buttons grid
                    ActionButtonsGrid(
                        uiState = uiState,
                        viewableUri = viewableUri,
                        onFilmTap = viewModel::toggleFilmSelector,
                        onSettingsTap = viewModel::toggleSettingsMenu,
                        onView = onView,
                        onShare = onShare,
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Overlays ──────────────────────────────────────────────────────────

        // Front-camera screen flash — pure white fill, instant on, slow fade-out after shutter
        AnimatedVisibility(
            visible = uiState.screenFlashActive,
            enter = fadeIn(animationSpec = tween(0)),
            exit = fadeOut(animationSpec = tween(350)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White))
        }

        AnimatedVisibility(
            visible = uiState.showFilmSelector,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            FilmSelectorSheet(
                films = FilmCatalog.all,
                selected = uiState.selectedFilm,
                favoriteFilmIds = uiState.favoriteFilmIds,
                onSelect = viewModel::selectFilm,
                onToggleFavorite = viewModel::toggleFavorite,
                onDismiss = viewModel::toggleFilmSelector,
            )
        }

        // Input-blocking scrim: sits above camera content, below the settings sheet.
        // Consumes all pointer events in the Initial pass so nothing underneath
        // (zoom slider, lens pills, viewfinder tap) is reachable while settings is open.
        if (uiState.showSettingsMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.forEach { it.consume() }
                            }
                        }
                    },
            )
        }

        AnimatedVisibility(
            visible = uiState.showSettingsMenu,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            SettingsMenuSheet(
                uiState = uiState,
                onSetFocusDuration = viewModel::setFocusDuration,
                onToggleLightLeak = viewModel::toggleLightLeak,
                onDateMenuTap = viewModel::toggleDateImprintMenu,
                onToggleHistogram = viewModel::toggleHistogram,
                onToggleCameraParams = viewModel::toggleCameraParams,
                onToggleLevel = viewModel::toggleLevel,
                onSetGridMode = viewModel::setGridMode,
                onSave = viewModel::saveAndCloseSettings,
            )
        }

        AnimatedVisibility(
            visible = uiState.showDateImprintMenu,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            DateImprintMenuSheet(
                uiState = uiState,
                onDismiss = viewModel::toggleDateImprintMenu,
                onSetEnabled = viewModel::setDateImprintEnabled,
                onSetStyle = viewModel::setDateImprintStyle,
                onSetColor = viewModel::setDateImprintColor,
                onSetFont = viewModel::setDateImprintFont,
                onSetSize = viewModel::setDateImprintSize,
                onSetPosition = viewModel::setDateImprintPosition,
                onSetGlow = viewModel::setDateImprintGlow,
                onSetBlur = viewModel::setDateImprintBlur,
                onSetOpacity = viewModel::setDateImprintOpacity,
                onSetBlurRepeat = viewModel::setDateImprintBlurRepeat,
            )
        }

        uiState.error?.let { msg ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = viewModel::dismissError) { Text("Dismiss") } },
            ) { Text(msg) }
        }
    }
}

// ── Top plate ─────────────────────────────────────────────────────────────────

@Composable
private fun CameraTopPlate(film: FilmStock) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF2E2E2E), TopPlate))
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "PHOTONCAM",
            color = ChromeHi,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Text(
            text = "${film.brand.displayName.uppercase()}  ${film.name.uppercase()}  ISO${film.iso}",
            color = ChromeLo,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            letterSpacing = 1.sp,
        )
    }
}

// ── Viewfinder window ─────────────────────────────────────────────────────────

@Composable
private fun ViewfinderWindow(
    uiState: ViewfinderUiState,
    previewView: PreviewView,
    cameraGranted: Boolean,
    onTap: (x: Float, y: Float) -> Unit,
) {
    // Outer chrome bezel
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(ChromeHi, ChromeLo, ChromeHi, ChromeLo)
                )
            )
            .padding(3.dp),
    ) {
        // Inner rubber gasket
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0C0C0C))
                .padding(1.dp),
        ) {
            // Actual viewfinder screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> onTap(offset.x, offset.y) }
                    },
            ) {
                if (cameraGranted) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Camera permission required",
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Film name badge — top-right only
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(bottomStart = 3.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = uiState.selectedFilm.name,
                        color = uiState.selectedFilm.accentColor.copy(alpha = 0.92f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    )
                }

                // EV indicator — bottom-left
                val evValue = uiState.evIndex * uiState.evStep
                if (evValue != 0.0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .clip(RoundedCornerShape(topEnd = 3.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (evValue > 0) "+${"%.1f".format(evValue)}" else "${"%.1f".format(evValue)}",
                            color = AmberLcd.copy(alpha = 0.9f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                }

                // Histogram overlay — bottom-right
                if (uiState.histogramEnabled) {
                    uiState.histogramData?.let { data ->
                        HistogramOverlay(
                            data = data,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
                        )
                    }
                }

                // Camera parameters overlay — bottom-center
                if (uiState.cameraParamsEnabled) {
                    uiState.cameraParams?.let { params ->
                        CameraParamsOverlay(
                            params = params,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 6.dp),
                        )
                    }
                }

                // Grid overlay
                if (uiState.gridMode != GridMode.OFF) {
                    GridOverlay(mode = uiState.gridMode, modifier = Modifier.fillMaxSize())
                }

                // Level overlay — center of viewfinder
                if (uiState.levelEnabled) {
                    LevelOverlay(
                        angle = uiState.levelAngle,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Tap-to-focus indicator
                var lastFocusPoint by remember { mutableStateOf<FocusPoint?>(null) }
                uiState.focusPoint?.let { lastFocusPoint = it }
                AnimatedVisibility(
                    visible = uiState.focusPoint != null,
                    enter = fadeIn(tween(80)),
                    exit = fadeOut(tween(350)),
                ) {
                    lastFocusPoint?.let { point ->
                        FocusBox(point = point, accentColor = uiState.selectedFilm.accentColor)
                    }
                }
            }
        }
    }
}

// ── Tap-to-focus box ──────────────────────────────────────────────────────────

@Composable
private fun FocusBox(point: FocusPoint, accentColor: Color) {
    // Scale in from 1.5× to 1× on each new tap position
    val scale = remember(point.x, point.y) { Animatable(1.5f) }
    LaunchedEffect(point.x, point.y) {
        scale.animateTo(1.0f, animationSpec = tween(200))
    }
    // Turn white once AF converges
    val color by animateColorAsState(
        targetValue = if (point.focused) Color.White else accentColor,
        animationSpec = tween(150),
        label = "focusColor",
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = point.x
        val cy = point.y
        val half = 30.dp.toPx() * scale.value
        val arm  = half * 0.36f
        val sw   = 1.8.dp.toPx()

        // Four corner brackets (two lines each)
        fun corner(ox: Float, oy: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(cx + ox, cy + oy), Offset(cx + ox + dx, cy + oy), sw)
            drawLine(color, Offset(cx + ox, cy + oy), Offset(cx + ox, cy + oy + dy), sw)
        }
        corner(-half, -half,  arm,  arm)   // top-left
        corner( half, -half, -arm,  arm)   // top-right
        corner(-half,  half,  arm, -arm)   // bottom-left
        corner( half,  half, -arm, -arm)   // bottom-right
    }
}

// ── Decorative rivet ──────────────────────────────────────────────────────────

@Composable
private fun Rivet(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(7.dp)) {
        drawCircle(color = Color(0xFF3A3A3A))
        drawCircle(
            color = Color(0xFF505050),
            radius = size.minDimension / 2f * 0.6f,
        )
        drawCircle(
            color = Color(0xFF606060),
            radius = size.minDimension / 2f * 0.25f,
            center = Offset(size.width * 0.35f, size.height * 0.35f),
        )
    }
}

// ── LCD readout ───────────────────────────────────────────────────────────────

@Composable
private fun LcdReadout(uiState: ViewfinderUiState) {
    val evValue = uiState.evIndex * uiState.evStep
    val evText = when {
        evValue > 0.0 -> "+${"%.1f".format(evValue)}"
        evValue < 0.0 -> "${"%.1f".format(evValue)}"
        else -> "±0.0"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(LcdBg)
            .border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(3.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LcdCell("EV", evText)
            // Processing indicator
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("PROC", color = Color(0xFF606060), fontFamily = FontFamily.Monospace, fontSize = 6.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) { i ->
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < uiState.processingCount.coerceAtMost(3))
                                        AmberLcd.copy(alpha = 0.8f)
                                    else Color(0xFF1A1A1A)
                                ),
                        )
                    }
                }
            }
            LcdCell("SHOT", uiState.totalShotsTaken.toString().padStart(4, '0'),
                valueColor = AmberLcd)
        }
    }
}

@Composable
private fun LcdCell(label: String, value: String, valueColor: Color = AmberLcd) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF606060), fontFamily = FontFamily.Monospace,
            fontSize = 6.sp, letterSpacing = 1.sp)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace,
            fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

// ── Zoom slider (1× lens only) ────────────────────────────────────────────────

@Composable
private fun ZoomSliderRow(
    zoomRatio: Float,
    accentColor: Color,
    onZoomChange: (Float) -> Unit,
    onZoomChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ZOOM",
                color = Color(0xFF555555),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = "${"%.1f".format(zoomRatio)}×",
                color = accentColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Slider(
            value = zoomRatio,
            onValueChange = onZoomChange,
            onValueChangeFinished = onZoomChangeFinished,
            valueRange = 1f..8f,
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor.copy(alpha = 0.8f),
                inactiveTrackColor = Color(0xFF333333),
            ),
        )
    }
}

// ── Lens selector ─────────────────────────────────────────────────────────────

@Composable
private fun LensSelectorRow(
    lenses: List<LensInfo>,
    selected: LensInfo?,
    accentColor: Color,
    onSelect: (LensInfo) -> Unit,
) {
    if (lenses.isEmpty()) {
        // Single-lens device — show static label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(BtnBot)
                    .border(1.dp, BtnBorder, RoundedCornerShape(3.dp))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            ) {
                Text(
                    text = selected?.label ?: "LENS",
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lenses.forEach { lens ->
            val isSelected = lens.id == selected?.id
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (isSelected)
                            Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.25f), accentColor.copy(alpha = 0.10f)))
                        else
                            Brush.verticalGradient(listOf(BtnTop, BtnBot))
                    )
                    .border(
                        1.dp,
                        if (isSelected) accentColor.copy(alpha = 0.7f) else BtnBorder,
                        RoundedCornerShape(3.dp),
                    )
                    .clickable { onSelect(lens) }
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lens.label,
                    color = if (isSelected) accentColor else Color(0xFF666666),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── Film canister mini-preview ────────────────────────────────────────────────

@Composable
private fun FilmCanisterMini(film: FilmStock, modifier: Modifier = Modifier) {
    val accent = film.accentColor
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cr = 2.dp.toPx()

        // Body
        val bL = w * 0.12f; val bR = w * 0.88f
        val bT = h * 0.28f; val bB = h * 0.94f
        drawRoundRect(
            color = accent.copy(alpha = 0.20f),
            topLeft = Offset(bL, bT),
            size = Size(bR - bL, bB - bT),
            cornerRadius = CornerRadius(cr),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.55f),
            topLeft = Offset(bL, bT),
            size = Size(bR - bL, bB - bT),
            cornerRadius = CornerRadius(cr),
            style = Stroke(width = 0.8.dp.toPx()),
        )
        // Narrow label band (accent fill)
        val bandT = bT + (bB - bT) * 0.25f
        val bandB = bT + (bB - bT) * 0.65f
        drawRect(
            color = accent.copy(alpha = 0.30f),
            topLeft = Offset(bL + 1f, bandT),
            size = Size(bR - bL - 2f, bandB - bandT),
        )
        // Vertical stripe detail
        for (i in listOf(0.35f, 0.65f)) {
            drawLine(
                color = accent.copy(alpha = 0.15f),
                start = Offset(bL + (bR - bL) * i, bT + 2.dp.toPx()),
                end = Offset(bL + (bR - bL) * i, bB - 2.dp.toPx()),
                strokeWidth = 0.5.dp.toPx(),
            )
        }
        // Top cap
        val cW = (bR - bL) * 0.62f
        val cL = (w - cW) / 2f
        drawRoundRect(
            color = Color(0xFF252525),
            topLeft = Offset(cL, h * 0.06f),
            size = Size(cW, bT - h * 0.06f + 1f),
            cornerRadius = CornerRadius(cr * 0.5f),
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.35f),
            topLeft = Offset(cL, h * 0.06f),
            size = Size(cW, bT - h * 0.06f + 1f),
            cornerRadius = CornerRadius(cr * 0.5f),
            style = Stroke(width = 0.6.dp.toPx()),
        )
        // Leader slot
        drawLine(
            color = Color(0xFF0D0D0D),
            start = Offset(w * 0.33f, bT + 1.5.dp.toPx()),
            end = Offset(w * 0.67f, bT + 1.5.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun FilmButton(
    film: FilmStock,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF323232), Color(0xFF181818))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(
                    film.accentColor.copy(alpha = 0.50f),
                    film.accentColor.copy(alpha = 0.22f),
                )),
                RoundedCornerShape(5.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            FilmCanisterMini(
                film = film,
                modifier = Modifier
                    .width(18.dp)
                    .height(26.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = film.brand.displayName.uppercase(),
                    color = film.accentColor.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 6.5.sp,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = film.name,
                    color = film.accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun ActionButtonsGrid(
    uiState: ViewfinderUiState,
    viewableUri: android.net.Uri?,
    onFilmTap: () -> Unit,
    onSettingsTap: () -> Unit,
    onView: () -> Unit,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Row 1: FILM (with canister) + SETTINGS — same line
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            FilmButton(
                film = uiState.selectedFilm,
                modifier = Modifier.weight(1.6f),
                onClick = onFilmTap,
            )
            CamButton(
                label = "SETTINGS",
                accentColor = uiState.selectedFilm.accentColor,
                highlighted = false,
                modifier = Modifier.weight(1f),
                onClick = onSettingsTap,
            )
        }
        // Row 2: VIEW + SHARE
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CamButton(
                label = if (viewableUri != null) "VIEW  ◉" else "VIEW",
                accentColor = uiState.selectedFilm.accentColor,
                highlighted = viewableUri != null,
                enabled = viewableUri != null,
                modifier = Modifier.weight(1f),
                onClick = onView,
            )
            CamButton(
                label = if (viewableUri != null) "SHARE  ▶" else "SHARE",
                accentColor = uiState.selectedFilm.accentColor,
                highlighted = viewableUri != null,
                enabled = viewableUri != null,
                modifier = Modifier.weight(1f),
                onClick = onShare,
            )
        }
    }
}

@Composable
private fun CamButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    accentColor: Color = AmberLcd,
    enabled: Boolean = true,
) {
    val textColor = when {
        !enabled    -> Color(0xFF1E1E1E)
        highlighted -> accentColor
        else        -> Color(0xFF686868)
    }
    val topColor = if (highlighted && enabled) Color(0xFF323232) else Color(0xFF2E2E2E)
    val botColor = if (highlighted && enabled) Color(0xFF181818) else Color(0xFF151515)
    val borderColor = if (highlighted && enabled) accentColor.copy(alpha = 0.45f)
                      else Color(0xFF3C3C3C)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                Brush.verticalGradient(listOf(topColor, botColor))
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(borderColor.copy(alpha = 0.9f), borderColor.copy(alpha = 0.4f))
                ),
                shape = RoundedCornerShape(5.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.5.sp,
            fontWeight = if (highlighted && enabled) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── Flash mini button (sits left of D-pad) ────────────────────────────────────

@Composable
private fun FlashMiniButton(
    enabled: Boolean,
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val onColor  = Color(0xFF4CAF50)
    val offColor = Color(0xFF883333)
    val color    = if (enabled) onColor else offColor

    Column(
        modifier = modifier
            .width(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = if (enabled) 0.14f else 0.06f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.3f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = "⚡", color = color, fontSize = 9.sp)
        // tiny indicator dot
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (enabled) 0.9f else 0.3f)),
        )
        // SCR label when front camera
        if (isFrontCamera) {
            Text(
                text = "S",
                color = color.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── D-pad ─────────────────────────────────────────────────────────────────────

@Composable
private fun DPadControl(
    accentColor: Color,
    canEvUp: Boolean,
    canEvDown: Boolean,
    hasMultipleLenses: Boolean,
    onEvUp: () -> Unit,
    onEvDown: () -> Unit,
    onPrevLens: () -> Unit,
    onNextLens: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(90.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = size.minDimension / 2f - 2.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF2C2C2C), Color(0xFF1A1A1A)),
                    center = Offset(cx, cy),
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color(0xFF404040),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            // Knurling arc
            drawArc(color = Color(0xFF4A4A4A), startAngle = 200f, sweepAngle = 100f,
                useCenter = false, style = Stroke(width = 2.5.dp.toPx()),
                size = Size(r * 1.6f, r * 1.6f),
                topLeft = Offset(cx - r * 0.8f, cy - r * 0.8f))
            drawCircle(color = Color(0xFF1C1C1C), radius = 20.dp.toPx(), center = Offset(cx, cy))
        }

        Box(modifier = Modifier.align(Alignment.TopCenter).size(width = 40.dp, height = 30.dp)
            .clickable(enabled = canEvUp, onClick = onEvUp), contentAlignment = Alignment.Center) {
            Text("▲", color = if (canEvUp) Color(0xFFCCCCCC) else Color(0xFF282828), fontSize = 11.sp)
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter).size(width = 40.dp, height = 30.dp)
            .clickable(enabled = canEvDown, onClick = onEvDown), contentAlignment = Alignment.Center) {
            Text("▼", color = if (canEvDown) Color(0xFFCCCCCC) else Color(0xFF282828), fontSize = 11.sp)
        }
        Box(modifier = Modifier.align(Alignment.CenterStart).size(width = 30.dp, height = 40.dp)
            .clickable(enabled = hasMultipleLenses, onClick = onPrevLens), contentAlignment = Alignment.Center) {
            Text("◄", color = if (hasMultipleLenses) Color(0xFFCCCCCC) else Color(0xFF282828), fontSize = 10.sp)
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).size(width = 30.dp, height = 40.dp)
            .clickable(enabled = hasMultipleLenses, onClick = onNextLens), contentAlignment = Alignment.Center) {
            Text("►", color = if (hasMultipleLenses) Color(0xFFCCCCCC) else Color(0xFF282828), fontSize = 10.sp)
        }

        // Decorative center cap
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1E1E1E))))
                .border(1.dp, Color(0xFF505050), CircleShape),
        )
    }
}

// ── Shutter button ────────────────────────────────────────────────────────────

@Composable
private fun ShutterButton(
    accentColor: Color,
    isCapturing: Boolean,
    processingCount: Int,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shutterEnabled = !isCapturing
    val shutterScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.86f else 1f,
        animationSpec = tween(80),
        label = "shutter_scale",
    )

    Box(
        modifier = modifier
            .scale(shutterScale)
            .size(76.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        if (shutterEnabled) Color(0xFFEEEEEE) else Color(0xFF2E2E2E),
                        if (shutterEnabled) Color(0xFFBBBBBB) else Color(0xFF1C1C1C),
                    )
                )
            )
            .border(
                3.dp,
                if (shutterEnabled) accentColor.copy(alpha = 0.8f) else Color(0xFF1E1E1E),
                CircleShape,
            )
            .clickable(enabled = shutterEnabled, onClick = onShutter),
        contentAlignment = Alignment.Center,
    ) {
        if (processingCount > 0 && !isCapturing) {
            // Small dot — background processing in progress
            Box(
                modifier = Modifier.size(8.dp).clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.7f)),
            )
        }
    }
}

// ── Date Imprint Menu Sheet ───────────────────────────────────────────────────

@Composable
private fun DateImprintMenuSheet(
    uiState: ViewfinderUiState,
    onDismiss: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetStyle: (DateImprintStyle) -> Unit,
    onSetColor: (DateImprintColor) -> Unit,
    onSetFont: (DateImprintFont) -> Unit,
    onSetSize: (DateImprintSize) -> Unit,
    onSetPosition: (DateImprintPosition) -> Unit,
    onSetGlow: (Int) -> Unit,
    onSetBlur: (Int) -> Unit,
    onSetOpacity: (Int) -> Unit,
    onSetBlurRepeat: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .background(Color(0xFF141414))
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Handle bar
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF555555))
                    .clickable(onClick = onDismiss),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "DATE STAMP",
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
                Switch(
                    checked = uiState.dateImprintEnabled,
                    onCheckedChange = onSetEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AmberLcd,
                        checkedTrackColor = AmberLcd.copy(alpha = 0.35f),
                        uncheckedThumbColor = Color(0xFF555555),
                        uncheckedTrackColor = Color(0xFF222222),
                    ),
                )
            }

            HorizontalDivider(color = Color(0xFF242424))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Format
                DateMenuSection(label = "FORMAT") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(DateImprintStyle.entries) { style ->
                            DateChip(
                                label = style.formatPreview(),
                                selected = uiState.dateImprintStyle == style && uiState.dateImprintEnabled,
                                enabled = uiState.dateImprintEnabled,
                                accentColor = uiState.selectedFilm.accentColor,
                                onClick = { onSetStyle(style) },
                            )
                        }
                    }
                }

                // Color
                DateMenuSection(label = "COLOR") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(DateImprintColor.entries) { color ->
                            val chipColor = try {
                                Color(android.graphics.Color.parseColor(color.hex))
                            } catch (_: Exception) { AmberLcd }
                            DateChip(
                                label = color.label,
                                selected = uiState.dateImprintColor == color && uiState.dateImprintEnabled,
                                enabled = uiState.dateImprintEnabled,
                                accentColor = chipColor,
                                onClick = { onSetColor(color) },
                            )
                        }
                    }
                }

                // Font + Size on same row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("FONT", color = Color(0xFF444444), fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DateImprintFont.entries.forEach { font ->
                                DateChip(
                                    label = font.label,
                                    selected = uiState.dateImprintFont == font && uiState.dateImprintEnabled,
                                    enabled = uiState.dateImprintEnabled,
                                    accentColor = uiState.selectedFilm.accentColor,
                                    onClick = { onSetFont(font) },
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("SIZE", color = Color(0xFF444444), fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp, letterSpacing = 2.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DateImprintSize.entries.forEach { size ->
                                DateChip(
                                    label = size.label,
                                    selected = uiState.dateImprintSize == size && uiState.dateImprintEnabled,
                                    enabled = uiState.dateImprintEnabled,
                                    accentColor = uiState.selectedFilm.accentColor,
                                    onClick = { onSetSize(size) },
                                )
                            }
                        }
                    }
                }

                // Position
                DateMenuSection(label = "POSITION") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DateImprintPosition.entries.forEach { pos ->
                            DateChip(
                                label = pos.label,
                                selected = uiState.dateImprintPosition == pos && uiState.dateImprintEnabled,
                                enabled = uiState.dateImprintEnabled,
                                accentColor = uiState.selectedFilm.accentColor,
                                onClick = { onSetPosition(pos) },
                            )
                        }
                    }
                }

                // Glow
                DateMenuSection(label = "GLOW  ${uiState.dateImprintGlow}%") {
                    androidx.compose.material3.Slider(
                        value = uiState.dateImprintGlow.toFloat(),
                        onValueChange = { onSetGlow(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = uiState.dateImprintEnabled,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = uiState.selectedFilm.accentColor,
                            activeTrackColor = uiState.selectedFilm.accentColor,
                            inactiveTrackColor = Color(0xFF333333),
                            disabledThumbColor = Color(0xFF444444),
                            disabledActiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Blur
                DateMenuSection(label = "BLUR  ${uiState.dateImprintBlur}%") {
                    androidx.compose.material3.Slider(
                        value = uiState.dateImprintBlur.toFloat(),
                        onValueChange = { onSetBlur(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = uiState.dateImprintEnabled,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = uiState.selectedFilm.accentColor,
                            activeTrackColor = uiState.selectedFilm.accentColor,
                            inactiveTrackColor = Color(0xFF333333),
                            disabledThumbColor = Color(0xFF444444),
                            disabledActiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Opacity
                DateMenuSection(label = "OPACITY  ${uiState.dateImprintOpacity}%") {
                    androidx.compose.material3.Slider(
                        value = uiState.dateImprintOpacity.toFloat(),
                        onValueChange = { onSetOpacity(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 19,
                        enabled = uiState.dateImprintEnabled,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = uiState.selectedFilm.accentColor,
                            activeTrackColor = uiState.selectedFilm.accentColor,
                            inactiveTrackColor = Color(0xFF333333),
                            disabledThumbColor = Color(0xFF444444),
                            disabledActiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Blur repeat
                DateMenuSection(label = "BLUR LAYERS  ${uiState.dateImprintBlurRepeat}") {
                    androidx.compose.material3.Slider(
                        value = uiState.dateImprintBlurRepeat.toFloat(),
                        onValueChange = { onSetBlurRepeat(it.toInt()) },
                        valueRange = 0f..20f,
                        steps = 19,
                        enabled = uiState.dateImprintEnabled,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = uiState.selectedFilm.accentColor,
                            activeTrackColor = uiState.selectedFilm.accentColor,
                            inactiveTrackColor = Color(0xFF333333),
                            disabledThumbColor = Color(0xFF444444),
                            disabledActiveTrackColor = Color(0xFF333333),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF242424))

            // SAVE / CLOSE button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A)))
                        )
                        .border(
                            1.dp,
                            AmberLcd.copy(alpha = 0.5f),
                            RoundedCornerShape(5.dp),
                        )
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "SAVE  ✓",
                        color = AmberLcd,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateMenuSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color(0xFF444444), fontFamily = FontFamily.Monospace,
            fontSize = 8.sp, letterSpacing = 2.sp)
        content()
    }
}

@Composable
private fun DateChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (selected) accentColor.copy(alpha = 0.18f) else Color(0xFF1C1C1C)
            )
            .border(
                1.dp,
                if (selected) accentColor.copy(alpha = 0.7f) else Color(0xFF333333),
                RoundedCornerShape(4.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> Color(0xFF2A2A2A)
                selected -> accentColor
                else     -> Color(0xFF666666)
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── Histogram overlay ─────────────────────────────────────────────────────────

@Composable
private fun HistogramOverlay(data: FloatArray, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(112.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        // Background
        drawRect(Color(0xCC000000))

        val padH = 4.dp.toPx()
        val padV = 4.dp.toPx()
        val drawW = size.width - padH * 2
        val drawH = size.height - padV * 2
        val barW = drawW / 256f

        // Filled area histogram (mountain silhouette)
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(padH, size.height - padV)
        for (i in 0 until 256) {
            val x = padH + i * barW
            val y = size.height - padV - data[i] * drawH
            path.lineTo(x, y)
        }
        path.lineTo(padH + drawW, size.height - padV)
        path.close()
        drawPath(path, Color(0xCCFFFFFF))

        // Baseline
        drawLine(
            color = Color(0x44FFFFFF),
            start = androidx.compose.ui.geometry.Offset(padH, size.height - padV),
            end = androidx.compose.ui.geometry.Offset(size.width - padH, size.height - padV),
            strokeWidth = 0.5.dp.toPx(),
        )
    }
}

// ── Camera parameters overlay ─────────────────────────────────────────────────

private fun formatShutterSpeed(ns: Long): String {
    val seconds = ns / 1_000_000_000.0
    return if (seconds >= 1.0) {
        "${"%.1f".format(seconds)}s"
    } else {
        val denom = (1.0 / seconds + 0.5).toInt()
        "1/$denom"
    }
}

@Composable
private fun CameraParamsOverlay(params: CameraParams, modifier: Modifier = Modifier) {
    val text = "${formatShutterSpeed(params.shutterNs)}  f/${"%.1f".format(params.aperture)}  ISO ${params.iso}"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xCC000000))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            color = Color(0xCCFFFFFF),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

// ── Grid overlay ──────────────────────────────────────────────────────────────

@Composable
private fun GridOverlay(mode: GridMode, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val lineColor = Color(0x55FFFFFF)
        val strokeW   = 0.7.dp.toPx()

        when (mode) {
            GridMode.THIRDS -> {
                for (i in 1..2) {
                    drawLine(lineColor, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), strokeW)
                    drawLine(lineColor, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeW)
                }
            }
            GridMode.SQUARE -> {
                for (i in 1..3) {
                    drawLine(lineColor, Offset(w * i / 4f, 0f), Offset(w * i / 4f, h), strokeW)
                    drawLine(lineColor, Offset(0f, h * i / 4f), Offset(w, h * i / 4f), strokeW)
                }
            }
            GridMode.NINES -> {
                for (i in 1..8) {
                    val thin = if (i % 3 == 0) lineColor.copy(alpha = 0.45f) else lineColor.copy(alpha = 0.25f)
                    drawLine(thin, Offset(w * i / 9f, 0f), Offset(w * i / 9f, h), strokeW)
                    drawLine(thin, Offset(0f, h * i / 9f), Offset(w, h * i / 9f), strokeW)
                }
                // Emphasize the rule-of-thirds lines within the 9×9
                for (i in listOf(3, 6)) {
                    drawLine(lineColor, Offset(w * i / 9f, 0f), Offset(w * i / 9f, h), strokeW)
                    drawLine(lineColor, Offset(0f, h * i / 9f), Offset(w, h * i / 9f), strokeW)
                }
            }
            GridMode.GOLDEN -> {
                // Golden ratio: 1/(1+φ) ≈ 0.3820
                val phi = 0.3820f
                for (r in listOf(phi, 1f - phi)) {
                    drawLine(lineColor, Offset(w * r, 0f), Offset(w * r, h), strokeW)
                    drawLine(lineColor, Offset(0f, h * r), Offset(w, h * r), strokeW)
                }
            }
            GridMode.DIAGONAL -> {
                // Two main corner-to-corner diagonals
                drawLine(lineColor, Offset(0f, 0f), Offset(w, h), strokeW)
                drawLine(lineColor, Offset(w, 0f), Offset(0f, h), strokeW)
                // Four rule-of-thirds diagonal helpers (corner to 1/3 edges)
                val dim = lineColor.copy(alpha = 0.30f)
                drawLine(dim, Offset(0f, 0f), Offset(w, h / 3f), strokeW)
                drawLine(dim, Offset(0f, 0f), Offset(w / 3f, h), strokeW)
                drawLine(dim, Offset(w, 0f), Offset(0f, h / 3f), strokeW)
                drawLine(dim, Offset(w, 0f), Offset(w * 2f / 3f, h), strokeW)
                drawLine(dim, Offset(0f, h), Offset(w, h * 2f / 3f), strokeW)
                drawLine(dim, Offset(0f, h), Offset(w / 3f, 0f), strokeW)
                drawLine(dim, Offset(w, h), Offset(0f, h * 2f / 3f), strokeW)
                drawLine(dim, Offset(w, h), Offset(w * 2f / 3f, 0f), strokeW)
            }
            GridMode.OFF -> {}
        }
    }
}

// ── Level overlay ─────────────────────────────────────────────────────────────

@Composable
private fun LevelOverlay(angle: Float, modifier: Modifier = Modifier) {
    // angle = atan2(gx, gy): 0° = portrait upright, +90° = landscape CW
    // The bar draws at -angle so it counter-rotates and stays parallel to the ground
    val mod90 = abs(angle % 90f)
    val isLocked = mod90 < 2f || mod90 > 88f

    val color by animateColorAsState(
        targetValue = if (isLocked) Color(0xFF4CAF50) else Color(0xCCFFFFFF),
        animationSpec = tween(200),
        label = "levelColor",
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val halfLen = 70.dp.toPx()
        val gap     = 10.dp.toPx()
        val strokeW = 1.5.dp.toPx()
        val tickLen = 6.dp.toPx()
        val dotR    = 3.dp.toPx()

        // Counter-rotate: draw at +angle so bar compensates device tilt and stays with the horizon
        val rad  = Math.toRadians((angle + 90.0))
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()

        // Left arm
        drawLine(color,
            start = Offset(cx - halfLen * cosA, cy - halfLen * sinA),
            end   = Offset(cx - gap * cosA,     cy - gap * sinA),
            strokeWidth = strokeW,
        )
        // Right arm
        drawLine(color,
            start = Offset(cx + gap * cosA,     cy + gap * sinA),
            end   = Offset(cx + halfLen * cosA, cy + halfLen * sinA),
            strokeWidth = strokeW,
        )
        // End ticks (perpendicular to the bar)
        listOf(-1f, 1f).forEach { side ->
            val tx = cx + side * halfLen * cosA
            val ty = cy + side * halfLen * sinA
            drawLine(color,
                start = Offset(tx - tickLen * sinA / 2, ty + tickLen * cosA / 2),
                end   = Offset(tx + tickLen * sinA / 2, ty - tickLen * cosA / 2),
                strokeWidth = strokeW,
            )
        }
        // Centre dot
        drawCircle(color, radius = dotR, center = Offset(cx, cy))
    }
}

// ── Settings Menu Sheet ───────────────────────────────────────────────────────

@Composable
private fun SettingsMenuSheet(
    uiState: ViewfinderUiState,
    onSetFocusDuration: (Int) -> Unit,
    onToggleLightLeak: () -> Unit,
    onDateMenuTap: () -> Unit,
    onToggleHistogram: () -> Unit,
    onToggleCameraParams: () -> Unit,
    onToggleLevel: () -> Unit,
    onSetGridMode: (GridMode) -> Unit,
    onSave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SETTINGS",
                    color = Color(0xFF888888),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                )
                // Save button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(uiState.selectedFilm.accentColor.copy(alpha = 0.15f))
                        .border(1.dp, uiState.selectedFilm.accentColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .clickable(onClick = onSave)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "SAVE",
                        color = uiState.selectedFilm.accentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF242424))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Light leaks ───────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LIGHT LEAKS",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    Switch(
                        checked = uiState.lightLeakEnabled,
                        onCheckedChange = { onToggleLightLeak() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = uiState.selectedFilm.accentColor,
                            checkedTrackColor = uiState.selectedFilm.accentColor.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color(0xFF555555),
                            uncheckedTrackColor = Color(0xFF222222),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── Histogram ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "HISTOGRAM",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    Switch(
                        checked = uiState.histogramEnabled,
                        onCheckedChange = { onToggleHistogram() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = uiState.selectedFilm.accentColor,
                            checkedTrackColor = uiState.selectedFilm.accentColor.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color(0xFF555555),
                            uncheckedTrackColor = Color(0xFF222222),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── Camera parameters ─────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "CAMERA PARAMS",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    Switch(
                        checked = uiState.cameraParamsEnabled,
                        onCheckedChange = { onToggleCameraParams() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = uiState.selectedFilm.accentColor,
                            checkedTrackColor = uiState.selectedFilm.accentColor.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color(0xFF555555),
                            uncheckedTrackColor = Color(0xFF222222),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── Level ─────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "LEVEL",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    Switch(
                        checked = uiState.levelEnabled,
                        onCheckedChange = { onToggleLevel() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = uiState.selectedFilm.accentColor,
                            checkedTrackColor = uiState.selectedFilm.accentColor.copy(alpha = 0.35f),
                            uncheckedThumbColor = Color(0xFF555555),
                            uncheckedTrackColor = Color(0xFF222222),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── Grid ──────────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "GRID",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(GridMode.entries) { mode ->
                            DateChip(
                                label = mode.label,
                                selected = uiState.gridMode == mode,
                                enabled = true,
                                accentColor = uiState.selectedFilm.accentColor,
                                onClick = { onSetGridMode(mode) },
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── Date imprint ──────────────────────────────────────────────
                val dateSummary = if (!uiState.dateImprintEnabled) "DATE STAMP  OFF"
                else "DATE  ${uiState.dateImprintStyle.formatPreview()}  ${uiState.dateImprintColor.label}  ${uiState.dateImprintPosition.label}"
                CamButton(
                    label = dateSummary,
                    accentColor = uiState.selectedFilm.accentColor,
                    highlighted = uiState.dateImprintEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDateMenuTap,
                )

                HorizontalDivider(color = Color(0xFF222222))

                // ── Focus duration ────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "FOCUS DURATION",
                            color = Color(0xFF555555),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            // stored 0 = infinite; slider position 31 = ∞
                            text = if (uiState.focusDurationSeconds == 0) "∞" else "${uiState.focusDurationSeconds}s",
                            color = uiState.selectedFilm.accentColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    // Slider: positions 1–30 = seconds, position 31 = ∞ (stored as 0)
                    val sliderValue = if (uiState.focusDurationSeconds == 0) 31f else uiState.focusDurationSeconds.toFloat()
                    Slider(
                        value = sliderValue,
                        onValueChange = { pos ->
                            val seconds = if (pos.toInt() >= 31) 0 else pos.toInt()
                            onSetFocusDuration(seconds)
                        },
                        valueRange = 1f..31f,
                        steps = 29,
                        modifier = Modifier.fillMaxWidth().height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = uiState.selectedFilm.accentColor,
                            activeTrackColor = uiState.selectedFilm.accentColor.copy(alpha = 0.8f),
                            inactiveTrackColor = Color(0xFF333333),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF222222))

                // ── About ─────────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ABOUT",
                        color = Color(0xFF555555),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        letterSpacing = 2.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "VERSION",
                            color = Color(0xFF444444),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                        Text(
                            text = com.photoncam.BuildConfig.VERSION_NAME,
                            color = Color(0xFF666666),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "BUILD DATE",
                            color = Color(0xFF444444),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                        Text(
                            text = com.photoncam.BuildConfig.BUILD_DATE,
                            color = Color(0xFF666666),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

