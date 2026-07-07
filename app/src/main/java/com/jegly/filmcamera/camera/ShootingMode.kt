package com.jegly.filmcamera.camera

enum class ShootingMode {
    PHOTO,
    VIDEO,
    LONG_EXPOSURE;

    val label: String get() = when (this) {
        PHOTO         -> "Photo"
        VIDEO         -> "Video"
        LONG_EXPOSURE -> "Long Exp"
    }
}
