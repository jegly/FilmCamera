package com.photoncam.camera

import androidx.camera.core.CameraSelector

/**
 * Represents a physical camera lens on the device.
 *
 * @param id        Camera2 camera ID (unique per lens).
 * @param label     Display label shown in the UI, e.g. "0.6×", "1×", "3×".
 * @param selector  CameraX selector that targets this specific lens.
 */
data class LensInfo(
    val id: String,
    val label: String,
    val selector: CameraSelector,
)
