package com.photoncam.film

import androidx.compose.ui.graphics.Color
import com.photoncam.R

/**
 * Curated list of all available film stocks.
 * LUT resource IDs reference .cube files in res/raw/.
 */
object FilmCatalog {

    val all: List<FilmStock> = listOf(
        // ── Kodak ─────────────────────────────────────────────────────────────
        FilmStock(
            id = "kodak_portra_400",
            brand = FilmBrand.KODAK,
            name = "Portra 400",
            iso = 400,
            lutResId = R.raw.lut_kodak_portra400,
            grainAmount = 0.18f,
            grainSize = 1.1f,
            lightLeakProbability = 0.07f,
            colorTempShift = +150,
            highlightRolloff = 0.55f,
            accentColor = Color(0xFFE8C87A),
            description = "Warm, flattering skin tones. The portrait standard.",
        ),
        FilmStock(
            id = "kodak_ektar_100",
            brand = FilmBrand.KODAK,
            name = "Ektar 100",
            iso = 100,
            lutResId = R.raw.lut_kodak_ektar100,
            grainAmount = 0.06f,
            grainSize = 0.7f,
            lightLeakProbability = 0.04f,
            colorTempShift = +80,
            highlightRolloff = 0.35f,
            accentColor = Color(0xFFE8A52A),
            description = "Ultra-fine grain, hyper-saturated landscapes.",
        ),
        FilmStock(
            id = "kodak_gold_200",
            brand = FilmBrand.KODAK,
            name = "Gold 200",
            iso = 200,
            lutResId = R.raw.lut_kodak_gold200,
            grainAmount = 0.14f,
            grainSize = 1.0f,
            lightLeakProbability = 0.10f,
            colorTempShift = +200,
            highlightRolloff = 0.40f,
            accentColor = Color(0xFFFFD700),
            description = "Sunny, golden-hour warmth. The family holiday roll.",
        ),

        // ── Fujifilm ──────────────────────────────────────────────────────────
        FilmStock(
            id = "fuji_provia_100f",
            brand = FilmBrand.FUJI,
            name = "Provia 100F",
            iso = 100,
            lutResId = R.raw.lut_fuji_provia100f,
            grainAmount = 0.07f,
            grainSize = 0.7f,
            lightLeakProbability = 0.04f,
            colorTempShift = -50,
            highlightRolloff = 0.30f,
            accentColor = Color(0xFF5BA4CF),
            description = "Neutral, accurate slide film. Faithful reproduction.",
        ),
        FilmStock(
            id = "fuji_velvia_50",
            brand = FilmBrand.FUJI,
            name = "Velvia 50",
            iso = 50,
            lutResId = R.raw.lut_fuji_velvia50,
            grainAmount = 0.05f,
            grainSize = 0.6f,
            lightLeakProbability = 0.03f,
            colorTempShift = -120,
            highlightRolloff = 0.20f,
            accentColor = Color(0xFF2D7D46),
            description = "Punchy greens, electric reds. Landscape obsession.",
        ),
        FilmStock(
            id = "fuji_superia_400",
            brand = FilmBrand.FUJI,
            name = "Superia 400",
            iso = 400,
            lutResId = R.raw.lut_fuji_superia400,
            grainAmount = 0.20f,
            grainSize = 1.2f,
            lightLeakProbability = 0.09f,
            colorTempShift = -80,
            highlightRolloff = 0.45f,
            accentColor = Color(0xFF4EAEDD),
            description = "Cool shadows, vivid highlights. Everyday versatility.",
        ),

        // ── Ilford ────────────────────────────────────────────────────────────
        FilmStock(
            id = "ilford_hp5",
            brand = FilmBrand.ILFORD,
            name = "HP5 Plus",
            iso = 400,
            isBlackAndWhite = true,
            lutResId = R.raw.lut_ilford_hp5,
            grainAmount = 0.22f,
            grainSize = 1.3f,
            lightLeakProbability = 0.06f,
            colorTempShift = 0,
            highlightRolloff = 0.50f,
            accentColor = Color(0xFFB0B0B0),
            description = "Classic reportage grain. Push to 3200 in your mind.",
        ),
        FilmStock(
            id = "ilford_delta_3200",
            brand = FilmBrand.ILFORD,
            name = "Delta 3200",
            iso = 3200,
            isBlackAndWhite = true,
            lutResId = R.raw.lut_ilford_delta3200,
            grainAmount = 0.45f,
            grainSize = 2.0f,
            lightLeakProbability = 0.05f,
            colorTempShift = 0,
            highlightRolloff = 0.60f,
            accentColor = Color(0xFF787878),
            description = "Extreme grain, dramatic shadows. Low light legend.",
        ),
        FilmStock(
            id = "ilford_fp4",
            brand = FilmBrand.ILFORD,
            name = "FP4 Plus",
            iso = 125,
            isBlackAndWhite = true,
            lutResId = R.raw.lut_ilford_fp4,
            grainAmount = 0.09f,
            grainSize = 0.8f,
            lightLeakProbability = 0.04f,
            colorTempShift = 0,
            highlightRolloff = 0.35f,
            accentColor = Color(0xFFD0D0D0),
            description = "Fine grain, wide latitude. The studio workhorse.",
        ),

        // ── Lomography ────────────────────────────────────────────────────────
        FilmStock(
            id = "lomo_chrome_purple",
            brand = FilmBrand.LOMOGRAPHY,
            name = "LomoChrome Purple",
            iso = 400,
            lutResId = R.raw.lut_lomo_chrome_purple,
            grainAmount = 0.28f,
            grainSize = 1.5f,
            lightLeakProbability = 0.20f,
            colorTempShift = -200,
            highlightRolloff = 0.35f,
            accentColor = Color(0xFFB06ABF),
            description = "Greens become purple. Reality optional.",
        ),
        FilmStock(
            id = "lomo_xpro",
            brand = FilmBrand.LOMOGRAPHY,
            name = "Cross Process",
            iso = 200,
            lutResId = R.raw.lut_lomo_xpro,
            grainAmount = 0.25f,
            grainSize = 1.4f,
            lightLeakProbability = 0.25f,
            colorTempShift = +300,
            highlightRolloff = 0.25f,
            accentColor = Color(0xFFFF6B35),
            description = "C-41 soup in E-6 chemistry. Unpredictable art.",
        ),
    )

    val default: FilmStock get() = all.first { it.id == "kodak_portra_400" }

    fun findById(id: String): FilmStock? = all.firstOrNull { it.id == id }

    fun byBrand(brand: FilmBrand): List<FilmStock> = all.filter { it.brand == brand }
}
