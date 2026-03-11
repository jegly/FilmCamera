package com.photoncam.film

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
 * @param lutResId    Raw resource id of the .cube LUT file, or null for B&W (handled via desaturation).
 * @param grainAmount 0f–1f normalised grain intensity.
 * @param grainSize   1f = fine, higher = coarser grain.
 * @param lightLeakProbability 0f–1f chance of adding a light leak overlay per shot.
 * @param colorTempShift Kelvin offset applied to white balance (+warm / -cool).
 * @param highlightRolloff How quickly highlights compress (0 = linear, 1 = strong rolloff).
 * @param accentColor UI accent used in film selector and overlays.
 * @param description Short marketing-style description shown in the film picker.
 */
data class FilmStock(
    val id: String,
    val brand: FilmBrand,
    val name: String,
    val iso: Int,
    val isBlackAndWhite: Boolean = false,
    @RawRes val lutResId: Int? = null,
    val grainAmount: Float = 0.15f,
    val grainSize: Float = 1.0f,
    val lightLeakProbability: Float = 0.08f,
    val colorTempShift: Int = 0,
    val highlightRolloff: Float = 0.4f,
    val accentColor: Color = Color.White,
    val description: String = "",
)

enum class FilmBrand(val displayName: String) {
    KODAK("Kodak"),
    FUJI("Fujifilm"),
    ILFORD("Ilford"),
    LOMOGRAPHY("Lomography"),
}
