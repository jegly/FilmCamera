package com.jegly.filmcamera.camera

/**
 * Live camera exposure parameters displayed in the viewfinder overlay.
 * Values are read from CaptureResult on each frame when the overlay is enabled.
 */
data class CameraParams(
    val shutterSpeedStr: String = "",   // e.g. "1/250"
    val isoStr: String = "",            // e.g. "ISO 400"
    val apertureStr: String = "",       // e.g. "f/1.8"
)
