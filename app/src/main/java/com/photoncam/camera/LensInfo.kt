package com.photoncam.camera

import androidx.camera.core.CameraSelector

/**
 * Represents a camera lens (physical or zoom-ratio-based).
 *
 * @param id        Stable identifier. Physical cameras use Camera2 ID; zoom-ratio
 *                  virtual lenses use "zoom_wide", "zoom_main", "zoom_tele".
 * @param label     Display label shown in the UI, e.g. "0.6×", "1×", "3×".
 * @param selector  CameraX selector used when rebinding is required.
 * @param zoomRatio When non-null the lens is selected by calling setZoomRatio() on the
 *                  already-bound logical camera — no rebind is needed. Physical camera
 *                  lenses leave this null and trigger a full rebind instead.
 */
data class LensInfo(
    val id: String,
    val label: String,
    val selector: CameraSelector,
    val zoomRatio: Float? = null,
)
