package com.jegly.filmcamera.film

import androidx.compose.ui.graphics.Color
import com.jegly.filmcamera.R

internal val stocksLomography: List<FilmStock> = listOf(
        FilmStock(
            id = "colorslide_lomography_x_pro_slide_200",
            brand = FilmBrand.LOMOGRAPHY,
            name = "Lomography X-Pro Slide 200",
            iso = 200,
            category = FilmCategory.CROSS_PROCESS,
            lutResId = R.raw.lut_colorslide_lomography_x_mpro_slide_200,
            lutType = LutType.CUBE,
            grainAmount = 0.06f,
            grainSize = 1.09f,
            lightLeakProbability = 0.05f,
            colorTempShift = 0,
            highlightRolloff = 0.40f,
            saturation = 1.00f,
            contrast = 1.15f,
            shadowLift = 4.0f,
            accentColor = Color(0xFFFF6B35),
            description = "Lomography X-Pro Slide 200",
        ),
        FilmStock(
            id = "negative_color_lomography_redscale_100",
            brand = FilmBrand.LOMOGRAPHY,
            name = "Lomography Redscale 100",
            iso = 100,
            category = FilmCategory.COLOR,
            lutResId = R.raw.lut_negative_color_lomography_redscale_100,
            lutType = LutType.CUBE,
            grainAmount = 0.05f,
            grainSize = 1.04f,
            lightLeakProbability = 0.05f,
            colorTempShift = 0,
            highlightRolloff = 0.40f,
            saturation = 1.00f,
            contrast = 0.95f,
            shadowLift = 4.0f,
            accentColor = Color(0xFFFF6B35),
            description = "Lomography Redscale 100",
        ),
)
