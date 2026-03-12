package com.photoncam.ui.viewfinder

import android.content.Intent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmStock
import com.photoncam.ui.filmselect.FilmSelectorSheet

private val BodyColor = Color(0xFF1A1A1A)
private val BodyColorDark = Color(0xFF141414)
private val AmberLcd = Color(0xFFFFAA00)
private val ChromeLight = Color(0xFF9A9A9A)
private val ChromeShadow = Color(0xFF4A4A4A)
private val ButtonBg = Color(0xFF252525)
private val ButtonBorder = Color(0xFF383838)

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

    // ── Camera body ────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BodyColor),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── LCD screen with chrome bezel ───────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            ) {
                // Chrome bezel layers
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ChromeLight, ChromeShadow, ChromeLight),
                            )
                        )
                        .padding(3.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF111111))
                            .padding(1.dp),
                    ) {
                        // Screen surface
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.Black),
                        ) {
                            if (cameraPermission.status.isGranted) {
                                val previewView = remember { PreviewView(context) }
                                LaunchedEffect(lifecycleOwner, uiState.selectedLens) {
                                    viewModel.bindCamera(lifecycleOwner, previewView)
                                }
                                AndroidView(
                                    factory = { previewView },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Camera permission required",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }

                            // Film name overlay (top-left of LCD)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "${uiState.selectedFilm.brand.displayName.uppercase()} ${uiState.selectedFilm.name}",
                                    color = uiState.selectedFilm.accentColor.copy(alpha = 0.9f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }
                }
            }

            // ── Amber LCD readout strip ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Lens
                LcdText(
                    label = "LENS",
                    value = uiState.selectedLens?.label ?: "—",
                )
                // EV
                val evValue = uiState.evIndex * uiState.evStep
                val evText = when {
                    evValue > 0.0 -> "+${"%.1f".format(evValue)}"
                    evValue < 0.0 -> "${"%.1f".format(evValue)}"
                    else -> "±0.0"
                }
                LcdText(label = "EV", value = evText)
                // Frames
                LcdText(
                    label = "FRAMES",
                    value = "${uiState.framesRemaining.toString().padStart(2, '0')}/36",
                    valueColor = if (uiState.framesRemaining <= 6) Color(0xFFFF4444) else AmberLcd,
                )
            }

            // ── Camera controls body ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(BodyColorDark)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: small physical buttons + film pill
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    // Film selector pill
                    FilmPill(
                        film = uiState.selectedFilm,
                        onClick = viewModel::toggleFilmSelector,
                    )
                    Spacer(Modifier.height(4.dp))
                    // Small function buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallCameraButton(
                            label = "DATE",
                            highlighted = uiState.dateImprintEnabled,
                            accentColor = uiState.selectedFilm.accentColor,
                            onClick = viewModel::toggleDateImprint,
                        )
                        SmallCameraButton(
                            label = "SHARE",
                            highlighted = uiState.lastCapturedUri != null,
                            accentColor = uiState.selectedFilm.accentColor,
                            enabled = uiState.lastCapturedUri != null,
                            onClick = {
                                uiState.lastCapturedUri?.let { uri ->
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
                            },
                        )
                    }
                }

                // Right: D-pad navigation ring
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    DPadControl(
                        accentColor = uiState.selectedFilm.accentColor,
                        isCapturing = uiState.isCapturing,
                        isProcessing = uiState.isProcessing,
                        rollFinished = uiState.rollFinished,
                        canEvUp = uiState.evRange?.let { uiState.evIndex < it.upper } ?: false,
                        canEvDown = uiState.evRange?.let { uiState.evIndex > it.lower } ?: false,
                        hasMultipleLenses = uiState.availableLenses.size > 1,
                        onEvUp = { viewModel.adjustExposure(+1) },
                        onEvDown = { viewModel.adjustExposure(-1) },
                        onPrevLens = viewModel::selectPrevLens,
                        onNextLens = viewModel::selectNextLens,
                        onShutter = viewModel::capture,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "▲▼ EV  ◄► LENS  ● SHOOT",
                        color = Color(0xFF3A3A3A),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }

        // ── Overlays ───────────────────────────────────────────────────────────

        AnimatedVisibility(
            visible = uiState.rollFinished,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RollFinishedOverlay(onReload = viewModel::reloadRoll)
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
                onSelect = viewModel::selectFilm,
                onDismiss = viewModel::toggleFilmSelector,
            )
        }

        uiState.error?.let { msg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                },
            ) { Text(msg) }
        }
    }
}

// ── Amber LCD readout ──────────────────────────────────────────────────────────

@Composable
private fun LcdText(
    label: String,
    value: String,
    valueColor: Color = AmberLcd,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFF4A4A4A),
            fontFamily = FontFamily.Monospace,
            fontSize = 7.sp,
            letterSpacing = 1.sp,
        )
        Text(
            text = value,
            color = valueColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

// ── Film pill ─────────────────────────────────────────────────────────────────

@Composable
private fun FilmPill(film: FilmStock, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(ButtonBg)
            .border(1.dp, film.accentColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column {
            Text(
                text = film.brand.displayName.uppercase(),
                color = Color(0xFF555555),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = film.name,
                    color = film.accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Text("▾", color = film.accentColor.copy(alpha = 0.5f), fontSize = 9.sp)
            }
        }
    }
}

// ── Small physical-looking camera button ──────────────────────────────────────

@Composable
private fun SmallCameraButton(
    label: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    accentColor: Color = Color(0xFFFF8C00),
    enabled: Boolean = true,
) {
    val textColor = when {
        !enabled -> Color(0xFF333333)
        highlighted -> accentColor
        else -> Color(0xFF888888)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2E2E2E), Color(0xFF1E1E1E))
                )
            )
            .border(1.dp, ButtonBorder, RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── D-pad navigation ring ─────────────────────────────────────────────────────

@Composable
private fun DPadControl(
    accentColor: Color,
    isCapturing: Boolean,
    isProcessing: Boolean,
    rollFinished: Boolean,
    canEvUp: Boolean,
    canEvDown: Boolean,
    hasMultipleLenses: Boolean,
    onEvUp: () -> Unit,
    onEvDown: () -> Unit,
    onPrevLens: () -> Unit,
    onNextLens: () -> Unit,
    onShutter: () -> Unit,
) {
    val shutterEnabled = !isCapturing && !isProcessing && !rollFinished
    val shutterScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutter_scale",
    )

    Box(
        modifier = Modifier.size(116.dp),
        contentAlignment = Alignment.Center,
    ) {
        // D-pad ring — Canvas drawn
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Outer ring fill
            drawCircle(color = Color(0xFF282828), radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))

            // Top highlight arc (light rim)
            drawArc(
                color = Color(0xFF484848),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
            )
            // Bottom shadow arc
            drawArc(
                color = Color(0xFF141414),
                startAngle = 30f,
                sweepAngle = 120f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
            )

            // Inner cutout ring (lighter) to separate center button
            val innerR = 24.dp.toPx()
            drawCircle(
                color = Color(0xFF1E1E1E),
                radius = innerR + 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(cx, cy),
            )
        }

        // UP — EV+
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 48.dp, height = 38.dp)
                .clickable(enabled = canEvUp, onClick = onEvUp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▲",
                color = if (canEvUp) Color(0xFFCCCCCC) else Color(0xFF353535),
                fontSize = 13.sp,
            )
        }

        // DOWN — EV-
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 48.dp, height = 38.dp)
                .clickable(enabled = canEvDown, onClick = onEvDown),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "▼",
                color = if (canEvDown) Color(0xFFCCCCCC) else Color(0xFF353535),
                fontSize = 13.sp,
            )
        }

        // LEFT — prev lens
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 38.dp, height = 48.dp)
                .clickable(enabled = hasMultipleLenses, onClick = onPrevLens),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "◄",
                color = if (hasMultipleLenses) Color(0xFFCCCCCC) else Color(0xFF353535),
                fontSize = 12.sp,
            )
        }

        // RIGHT — next lens
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 38.dp, height = 48.dp)
                .clickable(enabled = hasMultipleLenses, onClick = onNextLens),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "►",
                color = if (hasMultipleLenses) Color(0xFFCCCCCC) else Color(0xFF353535),
                fontSize = 12.sp,
            )
        }

        // CENTER — OK / Shutter
        Box(
            modifier = Modifier
                .scale(shutterScale)
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (shutterEnabled) Color(0xFFE8E8E8) else Color(0xFF3A3A3A),
                            if (shutterEnabled) Color(0xFFBBBBBB) else Color(0xFF252525),
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            if (shutterEnabled) accentColor else Color(0xFF2A2A2A),
                            if (shutterEnabled) accentColor.copy(alpha = 0.5f) else Color(0xFF1A1A1A),
                        )
                    ),
                    shape = CircleShape,
                )
                .clickable(enabled = shutterEnabled, onClick = onShutter),
            contentAlignment = Alignment.Center,
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = accentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (rollFinished) "✕" else "OK",
                    color = if (shutterEnabled) Color(0xFF333333) else Color(0xFF555555),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── Roll finished overlay ─────────────────────────────────────────────────────

@Composable
private fun RollFinishedOverlay(onReload: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "END OF ROLL",
                color = AmberLcd,
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Text(
                text = "36 exposures",
                color = Color(0xFF666666),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AmberLcd)
                    .clickable(onClick = onReload)
                    .padding(horizontal = 28.dp, vertical = 11.dp),
            ) {
                Text(
                    text = "RELOAD ROLL",
                    color = Color.Black,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
