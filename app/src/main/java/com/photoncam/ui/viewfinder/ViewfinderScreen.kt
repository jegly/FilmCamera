package com.photoncam.ui.viewfinder

import android.content.Intent
import android.util.Range
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
import com.photoncam.camera.LensInfo
import com.photoncam.film.FilmCatalog
import com.photoncam.film.FilmStock
import com.photoncam.ui.filmselect.FilmSelectorSheet

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ViewfinderScreen(viewModel: ViewfinderViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Live preview — top 2/3 ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f),
            ) {
                if (cameraPermission.status.isGranted) {
                    val previewView = remember { PreviewView(context) }

                    // Re-bind when lifecycleOwner changes OR user switches lens
                    LaunchedEffect(lifecycleOwner, uiState.selectedLens) {
                        viewModel.bindCamera(lifecycleOwner, previewView)
                    }

                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Camera permission required", color = Color.White)
                    }
                }

                // Status overlay on top of preview
                CameraStatusBar(
                    filmName = uiState.selectedFilm.name,
                    brand = uiState.selectedFilm.brand.displayName,
                    framesRemaining = uiState.framesRemaining,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Thin separator line between preview and controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFF333333)),
            )

            // ── Controls panel — bottom 1/3 ────────────────────────────────────
            ControlsPanel(
                uiState = uiState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onFilmTap = viewModel::toggleFilmSelector,
                onDateImprintTap = viewModel::toggleDateImprint,
                onEvDown = { viewModel.adjustExposure(-1) },
                onEvUp = { viewModel.adjustExposure(+1) },
                onLensSelect = viewModel::selectLens,
                onShutter = viewModel::capture,
                onShare = {
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

        // ── Roll finished overlay ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.rollFinished,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            RollFinishedOverlay(onReload = viewModel::reloadRoll)
        }

        // ── Film selector sheet ───────────────────────────────────────────────
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

        // ── Error snackbar ────────────────────────────────────────────────────
        uiState.error?.let { errorMsg ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                },
            ) { Text(errorMsg) }
        }
    }
}

// ── Status bar overlay on preview ─────────────────────────────────────────────

@Composable
private fun CameraStatusBar(
    filmName: String,
    brand: String,
    framesRemaining: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${brand.uppercase()} $filmName",
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "FRAMES",
                color = Color.White.copy(alpha = 0.5f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = framesRemaining.toString().padStart(2, '0'),
                color = Color(0xFFFF8C00),
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Controls panel ────────────────────────────────────────────────────────────

@Composable
private fun ControlsPanel(
    uiState: ViewfinderUiState,
    onFilmTap: () -> Unit,
    onDateImprintTap: () -> Unit,
    onEvDown: () -> Unit,
    onEvUp: () -> Unit,
    onLensSelect: (LensInfo) -> Unit,
    onShutter: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color(0xFF151515))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Row 1: Lens selector (only shown if multiple lenses available)
        if (uiState.availableLenses.size > 1) {
            LensSelectorRow(
                lenses = uiState.availableLenses,
                selectedLens = uiState.selectedLens,
                onLensSelect = onLensSelect,
            )
        }

        // Row 2: EV controls + date toggle + share
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EvControls(
                evIndex = uiState.evIndex,
                evStep = uiState.evStep,
                evRange = uiState.evRange,
                onEvDown = onEvDown,
                onEvUp = onEvUp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDateImprintTap, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = "Date imprint",
                        tint = if (uiState.dateImprintEnabled) Color(0xFFFF8C00) else Color(0xFF444444),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onShare,
                    enabled = uiState.lastCapturedUri != null,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = if (uiState.lastCapturedUri != null) Color.White else Color(0xFF3A3A3A),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // Row 3: Film pill + shutter button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmPill(
                film = uiState.selectedFilm,
                onClick = onFilmTap,
            )

            ShutterButton(
                isCapturing = uiState.isCapturing,
                isProcessing = uiState.isProcessing,
                rollFinished = uiState.rollFinished,
                accentColor = uiState.selectedFilm.accentColor,
                onClick = onShutter,
            )
        }
    }
}

@Composable
private fun LensSelectorRow(
    lenses: List<LensInfo>,
    selectedLens: LensInfo?,
    onLensSelect: (LensInfo) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        lenses.forEach { lens ->
            val isSelected = lens.id == selectedLens?.id
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) Color(0xFFFF8C00) else Color(0xFF2A2A2A))
                    .clickable { onLensSelect(lens) }
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = lens.label,
                    color = if (isSelected) Color.Black else Color(0xFFAAAAAA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun EvControls(
    evIndex: Int,
    evStep: Double,
    evRange: Range<Int>?,
    onEvDown: () -> Unit,
    onEvUp: () -> Unit,
) {
    val canDecrease = evRange != null && evIndex > evRange.lower
    val canIncrease = evRange != null && evIndex < evRange.upper
    val evValue = evIndex * evStep
    val evText = when {
        evValue > 0.0 -> "+${"%.1f".format(evValue)}"
        evValue < 0.0 -> "${"%.1f".format(evValue)}"
        else -> "0.0"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Minus
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (canDecrease) Color(0xFF2A2A2A) else Color(0xFF1E1E1E))
                .clickable(enabled = canDecrease, onClick = onEvDown),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                color = if (canDecrease) Color.White else Color(0xFF3A3A3A),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                lineHeight = 20.sp,
            )
        }

        // EV display
        Column(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "EV",
                color = Color(0xFF555555),
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = evText,
                color = if (evIndex == 0) Color(0xFF888888) else Color(0xFFFF8C00),
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Plus
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (canIncrease) Color(0xFF2A2A2A) else Color(0xFF1E1E1E))
                .clickable(enabled = canIncrease, onClick = onEvUp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (canIncrease) Color.White else Color(0xFF3A3A3A),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun FilmPill(film: FilmStock, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF222222))
            .border(1.dp, film.accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = film.brand.displayName.uppercase(),
                color = Color(0xFF666666),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = film.name,
                    color = film.accentColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "▾",
                    color = film.accentColor.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    isProcessing: Boolean,
    rollFinished: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isCapturing) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutter_scale",
    )
    val enabled = !isCapturing && !isProcessing && !rollFinished

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .scale(scale)
            .size(72.dp)
            .clip(CircleShape)
            .background(if (rollFinished) Color(0xFF333333) else Color(0xFFDDDDDD))
            .border(3.dp, if (enabled) accentColor else Color(0xFF444444), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = accentColor,
                strokeWidth = 3.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (rollFinished) Color(0xFF2A2A2A) else Color(0xFFCCCCCC)),
            )
        }
    }
}

// ── Roll finished overlay ──────────────────────────────────────────────────────

@Composable
private fun RollFinishedOverlay(onReload: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "END OF ROLL",
                color = Color(0xFFFF8C00),
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
            )
            Text(
                text = "36 exposures",
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFF8C00))
                    .clickable(onClick = onReload)
                    .padding(horizontal = 32.dp, vertical = 12.dp),
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
