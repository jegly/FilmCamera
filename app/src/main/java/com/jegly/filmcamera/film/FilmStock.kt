package com.jegly.filmcamera.film

import androidx.annotation.RawRes
import androidx.compose.ui.graphics.Color

/**
 * Describes the visual characteristics of a film emulsion.
 *
 * @param id          Unique stable identifier used as persistence key.
 * @param brand       Manufacturer name (Kodak, Fuji, Ilford, Lomography).
 * @param name        Film name (e.g. "Portra 400").
 * @param iso         Nominal ISO speed — affects simulated grain amount.
 * @param isBlackAndWhite True for monochrome emulsions.
 * @param lutResId    Raw resource id of the LUT file (.cube text or HaldCLUT PNG), or null (parametric-only).
 * @param grainAmount 0f–1f normalised grain intensity.
 * @param grainSize   1f = fine, higher = coarser grain.
 * @param lightLeakProbability 0f–1f chance of adding a light leak overlay per shot.
 * @param colorTempShift Kelvin offset applied to white balance (+warm / -cool).
 * @param highlightRolloff How quickly highlights compress (0 = linear, 1 = strong rolloff).
 * @param accentColor UI accent used in film selector and overlays.
 * @param description Short marketing-style description shown in the film picker.
 */
enum class LutType(val displayName: String) {
    CUBE("CUBE"),
    PNG("HaldCLUT"),
    PRESET("PRESET"),
}

enum class FilmCategory(val displayName: String) {
    COLOR("Color"),
    BLACK_AND_WHITE("B&W"),
    SLIDE("Slide"),
    CROSS_PROCESS("X-Pro"),
    INSTANT("Instant"),
    INFRARED("Infrared"),
    PRINT("Print"),
}

data class FilmStock(
    val id: String,
    val brand: FilmBrand,
    val name: String,
    val iso: Int,
    val category: FilmCategory = FilmCategory.COLOR,
    val isBlackAndWhite: Boolean = category == FilmCategory.BLACK_AND_WHITE || category == FilmCategory.INFRARED,
    @RawRes val lutResId: Int? = null,
    val lutType: LutType = if (lutResId == null) LutType.PRESET else LutType.CUBE,
    val grainAmount: Float = 0.15f,
    val grainSize: Float = 1.0f,
    val lightLeakProbability: Float = 0.08f,
    val colorTempShift: Int = 0,
    val highlightRolloff: Float = 0.4f,
    /** Saturation multiplier: 0 = B&W, 1 = neutral, >1 = boosted. */
    val saturation: Float = 1.0f,
    /** Contrast scale: 1 = neutral, >1 = more punchy. */
    val contrast: Float = 1.0f,
    /** Shadow lift (0–30): brightens dark areas for a milky/lifted look. */
    val shadowLift: Float = 0f,
    val accentColor: Color = Color.White,
    val description: String = "",
)

enum class FilmBrand(val displayName: String) {
    KODAK("Kodak"),
    FUJI("Fujifilm"),
    ILFORD("Ilford"),
    LOMOGRAPHY("Lomography"),
    CINESTILL("CineStill"),
    AGFA("Agfa"),
    ROLLEI("Rollei"),
    POLAROID("Polaroid"),
}
