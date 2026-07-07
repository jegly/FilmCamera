package com.jegly.filmcamera.ui.viewfinder

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.view.ViewGroup
import android.util.Range
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jegly.filmcamera.camera.CameraParams
import com.jegly.filmcamera.camera.LensInfo
import com.jegly.filmcamera.camera.LutPreviewQuality
import com.jegly.filmcamera.camera.ShootingMode
import com.jegly.filmcamera.film.FilmStock
import com.jegly.filmcamera.ui.filmselect.FilmSelectorSheet
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

// ── Colours ───────────────────────────────────────────────────────────────────
private val Black70  = Color(0xB3000000)
private val White90  = Color(0xE6FFFFFF)
private val AccentRed = Color(0xFFE53935)

@Composable
fun ViewfinderScreen(viewModel: ViewfinderViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentLutBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
    }

    var previewUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    // Permission launcher for Camera and Audio
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        previewUri = result.data?.data
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    LaunchedEffect(state.lutPreviewEnabled, state.lutPreviewBitmap) {
        currentLutBitmap = if (state.lutPreviewEnabled) state.lutPreviewBitmap else null
    }

    // Screen-flash overlay
    if (state.screenFlashActive) {
        Box(Modifier.fillMaxSize().background(Color.White))
        return
    }

    BackHandler(enabled = state.showFilmSelector || state.showSettingsMenu || state.showTuningSheet) {
        if (state.showFilmSelector) viewModel.toggleFilmSelector()
        if (state.showSettingsMenu) viewModel.toggleSettingsMenu()
        if (state.showTuningSheet)  viewModel.toggleTuningSheet()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        // ── Viewfinder ────────────────────────────────────────────────────────
        // The camera always renders into the PreviewView. When GPU acceleration is
        // on, a CameraX CameraEffect grades the preview stream on the way in, so the
        // PreviewView already shows the film look (and video records it too). When
        // off, we overlay a CPU-graded bitmap for the live LUT preview.
        AndroidView(
            factory = {
                viewModel.restoreStandardPreview(previewView)
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.lutPreviewEnabled && !state.gpuAcceleration) {
            currentLutBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // ── Tap to focus / double-tap to flip / pinch to zoom layer ──
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { viewModel.flipCamera() },
                        onTap = { offset ->
                            val factory = SurfaceOrientedMeteringPointFactory(
                                size.width.toFloat(), size.height.toFloat()
                            )
                            viewModel.tapToFocus(offset.x, offset.y, factory)
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) viewModel.pinchZoom(zoom)
                    }
                }
        )

        if (state.gridMode != GridMode.OFF) {
            GridOverlay(state.gridMode)
        }

        if (state.levelEnabled) {
            LevelIndicator(state.levelAngle)
        }

        state.focusPoint?.let { fp ->
            FocusRing(fp.x, fp.y, fp.focused)
        }

        state.longExposureProgress?.let { (cur, tot) ->
            LongExposureProgress(cur, tot)
        }

        if (state.isTimerRunning && state.timerCountdown > 0) {
            TimerCountdown(state.timerCountdown)
        }

        // ── Top bar ───────────────────────────────────────────────────────────
        TopBar(
            state = state,
            onFlash = viewModel::toggleFlash,
            onSettings = viewModel::toggleSettingsMenu,
            onTuning = viewModel::toggleTuningSheet,
            onAeLock = viewModel::toggleAeLock,
            onPro = viewModel::toggleProSheet,
            onTimer = { viewModel.setTimerSeconds(
                when (state.timerSeconds) { 0 -> 3; 3 -> 10; else -> 0 }
            ) },
        )

        // ── Overlays ──
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 60.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.histogramEnabled && state.histogramData != null) {
                HistogramOverlay(state.histogramData!!)
            }
            if (state.cameraParamsEnabled && state.cameraParams != null) {
                CameraParamsOverlay(state.cameraParams!!)
            }
        }

        // ── Bottom controls ───────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EvBar(
                evIndex = state.evIndex,
                evRange = state.evRange,
                evStep = state.evStep,
                onAdjust = viewModel::adjustExposure,
            )

            Spacer(Modifier.height(12.dp))

            if (state.availableLenses.size > 1) {
                LensSelector(
                    lenses = state.availableLenses,
                    selected = state.selectedLens,
                    onSelect = viewModel::selectLens,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (state.selectedLens?.id == "zoom_main" && state.availableLenses.isNotEmpty()) {
                ZoomSlider(
                    value = state.mainZoomRatio,
                    onValueChange = viewModel::setMainZoomRatio,
                    onValueChangeFinished = viewModel::commitMainZoomRatio,
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GalleryThumb(
                    uri = state.latestGalleryUri,
                    processingCount = state.processingCount,
                    // Tap: review the last shot in-app. Long-press: open the system gallery.
                    onClick = { state.latestGalleryUri?.let { previewUri = it } },
                    onLongClick = {
                        galleryPickerLauncher.launch(
                            Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                        )
                    },
                )

                ShutterButton(
                    shootingMode = state.shootingMode,
                    isCapturing = state.isCapturing,
                    isRecording = state.isRecording,
                    isTimerRunning = state.isTimerRunning,
                    onClick = viewModel::capture,
                )

                FilmButton(
                    film = state.selectedFilm,
                    onClick = viewModel::toggleFilmSelector,
                    onSwipeNext = viewModel::selectNextFilm,
                    onSwipePrev = viewModel::selectPrevFilm,
                )
            }

            Spacer(Modifier.height(12.dp))

            ModeSelector(
                current = state.shootingMode,
                onSelect = viewModel::setShootingMode,
            )
        }

        if (state.isRecording) {
            RecordingBadge(state.recordingDurationMs)
        }

        state.error?.let { msg ->
            ErrorBanner(msg, onDismiss = viewModel::dismissError)
        }

        // ── OVERLAYS (FULL SCREEN) ──
        if (state.showFilmSelector) {
            FilmSelectorOverlay(
                state = state,
                onSelect = viewModel::selectFilm,
                onToggleFav = viewModel::toggleFavorite,
                onImport = viewModel::importLut,
                onDismiss = viewModel::toggleFilmSelector,
            )
        }

        if (state.showSettingsMenu) {
            SettingsSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = viewModel::toggleSettingsMenu,
            )
        }

        if (state.showTuningSheet) {
            TuningSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = viewModel::toggleTuningSheet,
            )
        }

        if (state.showProSheet) {
            ProSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = viewModel::toggleProSheet,
            )
        }

        previewUri?.let { uri ->
            PhotoPreviewOverlay(
                uri = uri,
                onDismiss = { previewUri = null },
            )
        }

    }
}

@Composable
private fun PhotoPreviewOverlay(
    uri: Uri,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(onClick = onDismiss),
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageURI(uri)
                }
            },
            update = { image ->
                image.setImageURI(uri)
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false) {},
        )

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .align(Alignment.TopEnd)
                .size(44.dp)
                .background(Black70, CircleShape),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close preview",
                tint = Color.White,
            )
        }
    }
}

// ── Settings & Tuning Implementations ──

@Composable
private fun SettingsSheet(state: ViewfinderUiState, viewModel: ViewfinderViewModel, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF161616), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
                .clickable(enabled = false) { },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("CAMERA SETTINGS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
            
            SettingsToggle("Live LUT Preview", state.lutPreviewEnabled) { viewModel.toggleLutPreview() }
            SettingsToggle("GPU Acceleration", state.gpuAcceleration) { viewModel.setGpuAcceleration(!state.gpuAcceleration) }
            Text(
                if (state.gpuAcceleration) "Real-time OpenGL grading. Automatically falls back to CPU if unsupported."
                else "CPU grading of preview frames. Enable GPU for a smoother, real-time film look.",
                color = Color.Gray, fontSize = 9.sp,
            )
            if (!state.gpuAcceleration) {
                Text("LUT PREVIEW QUALITY", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LutPreviewQuality.entries.forEach { quality ->
                        val sel = state.lutPreviewQuality == quality
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (sel) Color.White else Color(0xFF2A2A2A))
                                .clickable { viewModel.setLutPreviewQuality(quality) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                quality.label,
                                color = if (sel) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            SettingsToggle("Date Watermark", state.dateImprintEnabled) { viewModel.setDateImprintEnabled(!state.dateImprintEnabled) }
            SettingsToggle("Luminance Histogram", state.histogramEnabled) { viewModel.toggleHistogram() }
            SettingsToggle("Exposure Parameters", state.cameraParamsEnabled) { viewModel.toggleCameraParams() }
            SettingsToggle("Horizon Level", state.levelEnabled) { viewModel.toggleLevel() }
            SettingsToggle("Save RAW (DNG)", state.saveDng) { viewModel.setSaveDng(!state.saveDng) }
            
            Spacer(Modifier.height(8.dp))
            Text("GRID OVERLAY", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GridMode.entries.forEach { mode ->
                    val sel = state.gridMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (sel) Color.White else Color(0xFF2A2A2A))
                            .clickable { viewModel.setGridMode(mode) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(mode.label, color = if (sel) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CLOSE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TuningSheet(state: ViewfinderUiState, viewModel: ViewfinderViewModel, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF161616), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
                .clickable(enabled = false) { },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FILM TUNING: ${state.selectedFilm.name.uppercase()}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)
            
            TuningSlider("LUT Intensity", (state.filmTuning.lutIntensity * 100).toInt(), 0..100) { viewModel.setLutIntensity(it) }
            TuningSlider("Grain Amount", (state.filmTuning.grainAmount * 100).toInt(), 0..100) { viewModel.setGrainAmount(it) }
            TuningSlider("Grain Size", ((state.filmTuning.grainSize - 1f) / 3f * 100).toInt(), 0..100) { viewModel.setGrainSize(it) }
            TuningSlider("Light Leak", (state.filmTuning.lightLeakIntensity * 100).toInt(), 0..100) { viewModel.setLightLeakIntensity(it) }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.commitTuning(); onDismiss() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("APPLY & SAVE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProSheet(state: ViewfinderUiState, viewModel: ViewfinderViewModel, onDismiss: () -> Unit) {
    val caps = state.manualCaps
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF161616), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .navigationBarsPadding()
                .clickable(enabled = false) { },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("PRO CONTROLS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 2.sp)

            if (caps == null || (!caps.supportsManualExposure && !caps.supportsManualFocus)) {
                Text("Manual controls aren't supported on this camera.", color = Color.Gray, fontSize = 12.sp)
            }

            if (caps?.supportsManualExposure == true) {
                val isoRange = caps.isoRange!!
                val expRange = caps.exposureNsRange!!
                SettingsToggle("Manual Exposure", state.manualExposureEnabled) {
                    viewModel.setManualExposureEnabled(!state.manualExposureEnabled)
                }
                if (state.manualExposureEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ISO", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${state.manualIso}", color = Color.Gray, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Slider(
                        value = state.manualIso.toFloat().coerceIn(isoRange.lower.toFloat(), isoRange.upper.toFloat()),
                        onValueChange = { viewModel.setManualIso(it.roundToInt()) },
                        valueRange = isoRange.lower.toFloat()..isoRange.upper.toFloat(),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                    )
                    val minNs = expRange.lower.toDouble().coerceAtLeast(1.0)
                    val maxNs = expRange.upper.toDouble().coerceAtLeast(minNs + 1.0)
                    val t = (ln(state.manualShutterNs.toDouble().coerceIn(minNs, maxNs) / minNs) / ln(maxNs / minNs)).toFloat()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SHUTTER", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(formatShutter(state.manualShutterNs), color = Color.Gray, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Slider(
                        value = t.coerceIn(0f, 1f),
                        onValueChange = { tt -> viewModel.setManualShutter((minNs * Math.pow(maxNs / minNs, tt.toDouble())).toLong()) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                    )
                }
            }

            if (caps?.supportsManualFocus == true) {
                SettingsToggle("Manual Focus", state.manualFocusEnabled) {
                    viewModel.setManualFocusEnabled(!state.manualFocusEnabled)
                }
                if (state.manualFocusEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("FOCUS", color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                state.manualFocusNorm <= 0.001f -> "∞"
                                state.manualFocusNorm >= 0.999f -> "Macro"
                                else -> "${(state.manualFocusNorm * 100).roundToInt()}%"
                            },
                            color = Color.Gray, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                    }
                    Slider(
                        value = state.manualFocusNorm,
                        onValueChange = { viewModel.setManualFocus(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("CLOSE", fontWeight = FontWeight.Bold) }
        }
    }
}

private fun formatShutter(ns: Long): String {
    val seconds = ns / 1_000_000_000.0
    return if (seconds >= 1.0) "%.1fs".format(seconds)
    else "1/${(1.0 / seconds).roundToInt()}"
}

@Composable
private fun FilmSelectorOverlay(
    state: ViewfinderUiState,
    onSelect: (com.jegly.filmcamera.film.FilmStock) -> Unit,
    onToggleFav: (String) -> Unit,
    onImport: (Uri, String) -> Unit,
    onDismiss: () -> Unit
) {
    // Built-in catalog plus any user-imported LUTs (brand "Custom").
    val allFilms = remember(state.customFilms) {
        com.jegly.filmcamera.film.FilmCatalog.all + state.customFilms
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = (it.lastPathSegment ?: "LUT")
                .substringAfterLast('/').substringBeforeLast('.').ifEmpty { "LUT" }
            onImport(it, name)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            }
    ) {
        Button(
            onClick = { importLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Import LUT (.cube)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color(0xFF161616))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {})
                }
        ) {
            FilmSelectorSheet(
                films = allFilms,
                selected = state.selectedFilm,
                favoriteFilmIds = state.favoriteFilmIds,
                onSelect = onSelect,
                onToggleFavorite = onToggleFav,
                onDismiss = onDismiss
            )
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 4.dp)
    ) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        Switch(
            checked = checked, 
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color.Gray)
        )
    }
}

@Composable
private fun TuningSlider(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("$value%", color = Color.Gray, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )
    }
}

// ── Components ────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    state: ViewfinderUiState,
    onFlash: () -> Unit,
    onSettings: () -> Unit,
    onTuning: () -> Unit,
    onAeLock: () -> Unit,
    onPro: () -> Unit,
    onTimer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TopBarIcon(
            icon = if (state.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
            tint = if (state.flashEnabled) Color(0xFFFFD600) else White90,
            onClick = onFlash
        )
        TopBarIcon(
            icon = if (state.aeLocked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            tint = if (state.aeLocked) Color(0xFFFFD600) else White90,
            onClick = onAeLock
        )
        TopBarIcon(
            icon = when (state.timerSeconds) {
                3 -> Icons.Filled.Timer3
                10 -> Icons.Filled.Timer10
                else -> Icons.Filled.Timer
            },
            tint = if (state.timerSeconds > 0) Color(0xFFFFD600) else White90,
            onClick = onTimer
        )
        TopBarIcon(
            icon = if (state.manualExposureEnabled || state.manualFocusEnabled) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
            tint = if (state.manualExposureEnabled || state.manualFocusEnabled) Color(0xFFFFD600) else White90,
            onClick = onPro,
        )
        TopBarIcon(icon = Icons.Outlined.Tune, onClick = onTuning)
        TopBarIcon(icon = Icons.Outlined.Settings, onClick = onSettings)
    }
}

@Composable
private fun TopBarIcon(icon: ImageVector, tint: Color = White90, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp).background(Black70, CircleShape)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun GalleryThumb(uri: Uri?, processingCount: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF222222))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            Box(Modifier.fillMaxSize().background(Color.White.copy(0.1f)).border(1.dp, Color.White.copy(0.2f), CircleShape))
        } else {
            Icon(Icons.Default.PhotoLibrary, null, tint = Color.Gray)
        }
        if (processingCount > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(AccentRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = processingCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ShutterButton(shootingMode: ShootingMode, isCapturing: Boolean, isRecording: Boolean, isTimerRunning: Boolean, onClick: () -> Unit) {
    val size by animateFloatAsState(if (isCapturing || isRecording) 72f else 80f)
    val innerSize by animateFloatAsState(if (isRecording) 32f else 64f)
    val cornerRadius by animateFloatAsState(if (isRecording) 8f else 100f)
    Box(
        modifier = Modifier.size(size.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clickable(enabled = !isCapturing && !isTimerRunning, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(innerSize.dp).clip(RoundedCornerShape(cornerRadius.dp)).background(if (shootingMode == ShootingMode.VIDEO) AccentRed else Color.White))
    }
}

@Composable
private fun FilmButton(film: FilmStock, onClick: () -> Unit, onSwipeNext: () -> Unit, onSwipePrev: () -> Unit) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { _, amount -> dx += amount },
                    onDragEnd = { if (dx <= -40f) onSwipeNext() else if (dx >= 40f) onSwipePrev() },
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(48.dp).background(Color(0xFF333333), RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(text = film.id.take(3).uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = film.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModeSelector(current: ShootingMode, onSelect: (ShootingMode) -> Unit) {
    Row(modifier = Modifier.background(Black70, RoundedCornerShape(20.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ShootingMode.entries.forEach { mode ->
            val selected = current == mode
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) Color.White.copy(0.2f) else Color.Transparent).clickable { onSelect(mode) }.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(text = mode.name, color = if (selected) Color.White else Color.Gray, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun EvBar(evIndex: Int, evRange: Range<Int>?, evStep: Double, onAdjust: (Int) -> Unit) {
    if (evRange == null) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(Black70, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp)) {
        IconButton(onClick = { onAdjust(-1) }) { Icon(Icons.Default.Remove, null, tint = Color.White) }
        val displayValue = "%.1f".format(evIndex * evStep)
        Text(text = if (evIndex > 0) "+$displayValue" else displayValue, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onAdjust(1) }) { Icon(Icons.Default.Add, null, tint = Color.White) }
    }
}

@Composable
private fun LensSelector(lenses: List<LensInfo>, selected: LensInfo?, onSelect: (LensInfo) -> Unit) {
    Row(modifier = Modifier.background(Black70, RoundedCornerShape(24.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        lenses.forEach { lens ->
            val isSelected = selected?.id == lens.id
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (isSelected) Color.White else Color.Transparent).clickable { onSelect(lens) }, contentAlignment = Alignment.Center) {
                Text(text = lens.label, color = if (isSelected) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ZoomSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit) {
    Row(modifier = Modifier.width(240.dp).background(Black70, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("1x", color = Color.White, fontSize = 10.sp)
        Slider(value = value, onValueChange = onValueChange, onValueChangeFinished = onValueChangeFinished, valueRange = 1f..8f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.Gray))
        Text("8x", color = Color.White, fontSize = 10.sp)
    }
}

@Composable
private fun HistogramOverlay(data: FloatArray) {
    Canvas(modifier = Modifier.size(120.dp, 60.dp).background(Black70, RoundedCornerShape(4.dp)).border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(4.dp))) {
        val step = size.width / 256f
        for (i in 0 until 255) {
            drawLine(color = Color.White.copy(0.7f), start = Offset(i * step, size.height), end = Offset(i * step, size.height - (data[i] * size.height)), strokeWidth = step)
        }
    }
}

@Composable
private fun CameraParamsOverlay(params: CameraParams) {
    Column(modifier = Modifier.background(Black70, RoundedCornerShape(4.dp)).padding(6.dp)) {
        Text(params.shutterSpeedStr, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(params.isoStr, color = Color.White, fontSize = 11.sp)
        Text(params.apertureStr, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun FocusRing(x: Float, y: Float, focused: Boolean) {
    val scale by animateFloatAsState(if (focused) 0.8f else 1.2f)
    val color = if (focused) Color(0xFFFFD600) else Color.White
    val density = LocalDensity.current
    Box(modifier = Modifier.offset(x = with(density) { x.toDp() } - 40.dp, y = with(density) { y.toDp() } - 40.dp).size(80.dp).rotate(if (focused) 0f else 45f).alpha(0.8f)) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color = color, radius = (size.minDimension / 2) * scale, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            val s = 10.dp.toPx()
            drawLine(color, Offset(0f, 0f), Offset(s, 0f), 2.dp.toPx())
            drawLine(color, Offset(0f, 0f), Offset(0f, s), 2.dp.toPx())
            drawLine(color, Offset(size.width, 0f), Offset(size.width - s, 0f), 2.dp.toPx())
            drawLine(color, Offset(size.width, 0f), Offset(size.width, s), 2.dp.toPx())
            drawLine(color, Offset(0f, size.height), Offset(s, size.height), 2.dp.toPx())
            drawLine(color, Offset(0f, size.height), Offset(0f, size.height - s), 2.dp.toPx())
            drawLine(color, Offset(size.width, size.height), Offset(size.width - s, size.height), 2.dp.toPx())
            drawLine(color, Offset(size.width, size.height), Offset(size.width, size.height - s), 2.dp.toPx())
        }
    }
}

@Composable
private fun LongExposureProgress(current: Int, total: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(progress = current.toFloat() / total, color = Color.White, strokeWidth = 6.dp, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("Exposing... $current/$total", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TimerCountdown(seconds: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = seconds.toString(), color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Black, modifier = Modifier.alpha(0.8f))
    }
}

@Composable
private fun RecordingBadge(durationMs: Long) {
    val secs = (durationMs / 1000) % 60
    val mins = (durationMs / 60000)
    val time = "%02d:%02d".format(mins, secs)
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp), contentAlignment = Alignment.TopCenter) {
        Row(modifier = Modifier.background(AccentRed, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color.White, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(time, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun LevelIndicator(angle: Float) {
    val normalizedAngle = when { angle > 180 -> angle - 360; angle < -180 -> angle + 360; else -> angle }
    val isLevel = abs(normalizedAngle) < 1f
    val color = if (isLevel) Color(0xFF4CAF50) else Color.White.copy(0.5f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp, 200.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            drawLine(color, Offset(center.x - 10.dp.toPx(), center.y), Offset(center.x + 10.dp.toPx(), center.y), 1.dp.toPx())
            drawLine(color, Offset(center.x, center.y - 10.dp.toPx()), Offset(center.x, center.y + 10.dp.toPx()), 1.dp.toPx())
            rotate(normalizedAngle) {
                drawLine(color, Offset(20.dp.toPx(), center.y), Offset(60.dp.toPx(), center.y), 2.dp.toPx())
                drawLine(color, Offset(size.width - 60.dp.toPx(), center.y), Offset(size.width - 20.dp.toPx(), center.y), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun GridOverlay(mode: GridMode) {
    Canvas(Modifier.fillMaxSize()) {
        val color = Color.White.copy(0.3f)
        val stroke = 1.dp.toPx()
        when (mode) {
            GridMode.THIRDS -> {
                drawLine(color, Offset(size.width/3, 0f), Offset(size.width/3, size.height), stroke)
                drawLine(color, Offset(2*size.width/3, 0f), Offset(2*size.width/3, size.height), stroke)
                drawLine(color, Offset(0f, size.height/3), Offset(size.width, size.height/3), stroke)
                drawLine(color, Offset(0f, 2*size.height/3), Offset(size.width, 2*size.height/3), stroke)
            }
            GridMode.SQUARE -> {
                for (i in 1..3) {
                    drawLine(color, Offset(i*size.width/4, 0f), Offset(i*size.width/4, size.height), stroke)
                    drawLine(color, Offset(0f, i*size.height/4), Offset(size.width, i*size.height/4), stroke)
                }
            }
            GridMode.GOLDEN -> {
                val ratio = 0.382f
                drawLine(color, Offset(size.width*ratio, 0f), Offset(size.width*ratio, size.height), stroke)
                drawLine(color, Offset(size.width*(1-ratio), 0f), Offset(size.width*(1-ratio), size.height), stroke)
                drawLine(color, Offset(0f, size.height*ratio), Offset(size.width, size.height*ratio), stroke)
                drawLine(color, Offset(0f, size.height*(1-ratio)), Offset(size.width, size.height*(1-ratio)), stroke)
            }
            GridMode.NINES -> {
                for (i in 1..8) {
                    drawLine(color, Offset(i*size.width/9, 0f), Offset(i*size.width/9, size.height), stroke)
                    drawLine(color, Offset(0f, i*size.height/9), Offset(size.width, i*size.height/9), stroke)
                }
            }
            GridMode.DIAGONAL -> {
                // Two main diagonals plus the "diagonal method" lines dropped at 45°
                // from each corner — a classic composition guide.
                drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke)
                drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke)
                val d = minOf(size.width, size.height)
                drawLine(color, Offset(0f, 0f), Offset(d, d), stroke)
                drawLine(color, Offset(size.width, 0f), Offset(size.width - d, d), stroke)
                drawLine(color, Offset(0f, size.height), Offset(d, size.height - d), stroke)
                drawLine(color, Offset(size.width, size.height), Offset(size.width - d, size.height - d), stroke)
            }
            else -> {}
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        Surface(color = AccentRed, shape = RoundedCornerShape(8.dp), tonalElevation = 4.dp, modifier = Modifier.clickable { onDismiss() }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Error, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(message, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
