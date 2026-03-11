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
import androidx.compose.material3.MaterialTheme
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
import com.photoncam.film.FilmCatalog
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

        if (cameraPermission.status.isGranted) {
            // ── Live preview ──────────────────────────────────────────────────
            val previewView = remember { PreviewView(context) }
            LaunchedEffect(lifecycleOwner) {
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

        // ── Top bar (rangefinder top plate) ───────────────────────────────────
        CameraTopPlate(
            filmName = uiState.selectedFilm.name,
            brand = uiState.selectedFilm.brand.displayName,
            framesRemaining = uiState.framesRemaining,
            dateImprintEnabled = uiState.dateImprintEnabled,
            onFilmTap = viewModel::toggleFilmSelector,
            onDateImprintTap = viewModel::toggleDateImprint,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // ── Bottom bar (controls) ─────────────────────────────────────────────
        BottomControls(
            isCapturing = uiState.isCapturing,
            isProcessing = uiState.isProcessing,
            rollFinished = uiState.rollFinished,
            accentColor = uiState.selectedFilm.accentColor,
            lastCapturedUri = uiState.lastCapturedUri,
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
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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

@Composable
private fun CameraTopPlate(
    filmName: String,
    brand: String,
    framesRemaining: Int,
    dateImprintEnabled: Boolean,
    onFilmTap: () -> Unit,
    onDateImprintTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A).copy(alpha = 0.92f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Film stock name — tappable
        Column(
            modifier = Modifier.clickable(onClick = onFilmTap),
        ) {
            Text(
                text = brand.uppercase(),
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
            )
            Text(
                text = filmName,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Frame counter
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "FRAMES",
                color = Color(0xFF888888),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp,
            )
            Text(
                text = framesRemaining.toString().padStart(2, '0'),
                color = Color(0xFFFF8C00),
                fontFamily = FontFamily.Monospace,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Date imprint toggle
        IconButton(onClick = onDateImprintTap) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = "Date imprint",
                tint = if (dateImprintEnabled) Color(0xFFFF8C00) else Color(0xFF555555),
            )
        }
    }
}

@Composable
private fun BottomControls(
    isCapturing: Boolean,
    isProcessing: Boolean,
    rollFinished: Boolean,
    accentColor: Color,
    lastCapturedUri: android.net.Uri?,
    onShutter: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shutterScale by animateFloatAsState(
        targetValue = if (isCapturing) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutter_scale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A).copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Share button (enabled when last capture exists)
        IconButton(
            onClick = onShare,
            enabled = lastCapturedUri != null,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share",
                tint = if (lastCapturedUri != null) Color.White else Color(0xFF444444),
            )
        }

        // Shutter button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .scale(shutterScale)
                .size(80.dp)
                .clip(CircleShape)
                .background(if (rollFinished) Color(0xFF444444) else Color(0xFFDDDDDD))
                .border(3.dp, accentColor, CircleShape)
                .clickable(
                    enabled = !isCapturing && !isProcessing && !rollFinished,
                    onClick = onShutter,
                ),
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = accentColor,
                    strokeWidth = 3.dp,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (rollFinished) Color(0xFF333333) else Color(0xFFCCCCCC)),
                )
            }
        }

        // Spacer to balance layout
        Spacer(modifier = Modifier.size(48.dp))
    }
}

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
