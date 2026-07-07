package com.jegly.filmcamera.film

/**
 * Per-session tuning overrides applied on top of a [FilmStock]'s base values.
 * Every field drives a GLSL uniform in the shader pipeline, so changes take
 * effect immediately without reprocessing any pixels on the CPU.
 *
 * @param lutIntensity   0.0 = bypass LUT entirely (raw), 1.0 = full film look
 * @param grainAmount    0.0 = no grain, 1.0 = maximum grain
 * @param grainSize      1.0 = fine grain, 4.0 = coarse chunky grain
 * @param lightLeakIntensity  0.0 = disabled, 1.0 = full probability from FilmStock
 */
data class FilmTuning(
    val lutIntensity: Float = 1.0f,
    val grainAmount: Float = 1.0f,
    val grainSize: Float = 1.0f,
    val lightLeakIntensity: Float = 1.0f,
) {
    companion object {
        val DEFAULT = FilmTuning()

        fun fromSliders(
            lutSlider: Float,    // 0–100 slider value
            grainSlider: Float,  // 0–100
            grainSizeSlider: Float, // 0–100 mapped to 1–4
            lightLeakSlider: Float, // 0–100
        ) = FilmTuning(
            lutIntensity        = (lutSlider / 100f).coerceIn(0f, 1f),
            grainAmount         = (grainSlider / 100f).coerceIn(0f, 1f),
            grainSize           = 1f + (grainSizeSlider / 100f) * 3f,
            lightLeakIntensity  = (lightLeakSlider / 100f).coerceIn(0f, 1f),
        )
    }

    /** Return slider value (0–100) for LUT intensity */
    val lutSlider: Int get() = (lutIntensity * 100f).toInt()
    /** Return slider value (0–100) for grain amount */
    val grainSlider: Int get() = (grainAmount * 100f).toInt()
    /** Return slider value (0–100) for grain size */
    val grainSizeSlider: Int get() = ((grainSize - 1f) / 3f * 100f).toInt()
    /** Return slider value (0–100) for light leak intensity */
    val lightLeakSlider: Int get() = (lightLeakIntensity * 100f).toInt()
}
