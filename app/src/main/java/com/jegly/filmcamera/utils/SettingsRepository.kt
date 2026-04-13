package com.jegly.filmcamera.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jegly.filmcamera.film.FilmCatalog
import com.jegly.filmcamera.processing.DateImprintColor
import com.jegly.filmcamera.processing.DateImprintFont
import com.jegly.filmcamera.processing.DateImprintPosition
import com.jegly.filmcamera.processing.DateImprintSize
import com.jegly.filmcamera.processing.DateImprintStyle
import com.jegly.filmcamera.camera.ShootingMode
import com.jegly.filmcamera.camera.LutPreviewQuality
import com.jegly.filmcamera.film.FilmTuning
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "filmcamera_settings")

data class AppSettings(
    val filmId: String,
    val lightLeakEnabled: Boolean,
    val selectedLensId: String?,
    val evIndex: Int,
    val dateImprintEnabled: Boolean,
    val dateImprintStyle: DateImprintStyle,
    val dateImprintColor: DateImprintColor,
    val dateImprintFont: DateImprintFont,
    val dateImprintSize: DateImprintSize,
    val dateImprintPosition: DateImprintPosition,
    val dateImprintGlow: Int = 100,
    val dateImprintBlur: Int = 50,
    val dateImprintOpacity: Int = 50,
    val dateImprintBlurRepeat: Int = 3,
    val totalShotsTaken: Int = 0,
    val favoriteFilmIds: Set<String> = emptySet(),
    val flashEnabled: Boolean = false,
    val mainZoomRatio: Float = 1.0f,
    val focusDurationSeconds: Int = 5,
    val histogramEnabled: Boolean = false,
    val lutPreviewEnabled: Boolean = false,
    val lutPreviewQuality: LutPreviewQuality = LutPreviewQuality.BALANCED,
    val cameraParamsEnabled: Boolean = false,
    val levelEnabled: Boolean = false,
    val gridMode: String = "OFF",
    val filmTuning: FilmTuning = FilmTuning.DEFAULT,
    val shootingMode: ShootingMode = ShootingMode.PHOTO,
    val timerSeconds: Int = 0,
    val saveDng: Boolean = false,
    val longExposureFrames: Int = 15,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val filmId = stringPreferencesKey("film_id")
        val lightLeakEnabled = booleanPreferencesKey("light_leak_enabled")
        val selectedLensId = stringPreferencesKey("selected_lens_id")
        val evIndex = intPreferencesKey("ev_index")
        val dateImprintEnabled = booleanPreferencesKey("date_imprint_enabled")
        val dateImprintStyle = stringPreferencesKey("date_imprint_style")
        val dateImprintColor = stringPreferencesKey("date_imprint_color")
        val dateImprintFont = stringPreferencesKey("date_imprint_font")
        val dateImprintSize = stringPreferencesKey("date_imprint_size")
        val dateImprintPosition = stringPreferencesKey("date_imprint_position")
        val dateImprintGlow = intPreferencesKey("date_imprint_glow")
        val dateImprintBlur = intPreferencesKey("date_imprint_blur_amount")
        val dateImprintOpacity = intPreferencesKey("date_imprint_opacity")
        val dateImprintBlurRepeat = intPreferencesKey("date_imprint_blur_repeat")
        val totalShotsTaken = intPreferencesKey("total_shots_taken")
        val favoriteFilmIds = stringPreferencesKey("favorite_film_ids")
        val flashEnabled = booleanPreferencesKey("flash_enabled")
        val mainZoomRatio = floatPreferencesKey("main_zoom_ratio")
        val focusDurationSeconds = intPreferencesKey("focus_duration_seconds")
        val histogramEnabled = booleanPreferencesKey("histogram_enabled")
        val lutPreviewEnabled = booleanPreferencesKey("lut_preview_enabled")
        val lutPreviewQuality = stringPreferencesKey("lut_preview_quality")
        val cameraParamsEnabled = booleanPreferencesKey("camera_params_enabled")
        val levelEnabled = booleanPreferencesKey("level_enabled")
        val gridMode = stringPreferencesKey("grid_mode")
        val lutIntensity = floatPreferencesKey("lut_intensity")
        val tuningGrainAmount = floatPreferencesKey("tuning_grain_amount")
        val tuningGrainSize = floatPreferencesKey("tuning_grain_size")
        val tuningLightLeakIntensity = floatPreferencesKey("tuning_light_leak_intensity")
        val shootingMode = stringPreferencesKey("shooting_mode")
        val timerSeconds = intPreferencesKey("timer_seconds")
        val saveDng = booleanPreferencesKey("save_dng")
        val longExposureFrames = intPreferencesKey("long_exposure_frames")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            filmId = prefs[Keys.filmId] ?: FilmCatalog.default.id,
            lightLeakEnabled = prefs[Keys.lightLeakEnabled] ?: true,
            selectedLensId = prefs[Keys.selectedLensId],
            evIndex = prefs[Keys.evIndex] ?: 0,
            dateImprintEnabled = prefs[Keys.dateImprintEnabled] ?: true,
            dateImprintStyle = prefs[Keys.dateImprintStyle]?.let { runCatching { DateImprintStyle.valueOf(it) }.getOrNull() } ?: DateImprintStyle.CLASSIC,
            dateImprintColor = prefs[Keys.dateImprintColor]?.let { runCatching { DateImprintColor.valueOf(it) }.getOrNull() } ?: DateImprintColor.AMBER,
            dateImprintFont = prefs[Keys.dateImprintFont]?.let { runCatching { DateImprintFont.valueOf(it) }.getOrNull() } ?: DateImprintFont.LED,
            dateImprintSize = prefs[Keys.dateImprintSize]?.let { runCatching { DateImprintSize.valueOf(it) }.getOrNull() } ?: DateImprintSize.MEDIUM,
            dateImprintPosition = prefs[Keys.dateImprintPosition]?.let { runCatching { DateImprintPosition.valueOf(it) }.getOrNull() } ?: DateImprintPosition.BOTTOM_RIGHT,
            dateImprintGlow = prefs[Keys.dateImprintGlow] ?: 100,
            dateImprintBlur = prefs[Keys.dateImprintBlur] ?: 50,
            dateImprintOpacity = prefs[Keys.dateImprintOpacity] ?: 50,
            dateImprintBlurRepeat = prefs[Keys.dateImprintBlurRepeat] ?: 3,
            totalShotsTaken = prefs[Keys.totalShotsTaken] ?: 0,
            favoriteFilmIds = prefs[Keys.favoriteFilmIds]?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            flashEnabled = prefs[Keys.flashEnabled] ?: false,
            mainZoomRatio = prefs[Keys.mainZoomRatio] ?: 1.0f,
            focusDurationSeconds = prefs[Keys.focusDurationSeconds] ?: 5,
            histogramEnabled = prefs[Keys.histogramEnabled] ?: false,
            lutPreviewEnabled = prefs[Keys.lutPreviewEnabled] ?: false,
            lutPreviewQuality = prefs[Keys.lutPreviewQuality]
                ?.let { runCatching { LutPreviewQuality.valueOf(it) }.getOrNull() }
                ?: LutPreviewQuality.BALANCED,
            cameraParamsEnabled = prefs[Keys.cameraParamsEnabled] ?: false,
            levelEnabled = prefs[Keys.levelEnabled] ?: false,
            gridMode = prefs[Keys.gridMode] ?: "OFF",
            filmTuning = FilmTuning(
                lutIntensity = prefs[Keys.lutIntensity] ?: 1.0f,
                grainAmount = prefs[Keys.tuningGrainAmount] ?: 1.0f,
                grainSize = prefs[Keys.tuningGrainSize] ?: 1.0f,
                lightLeakIntensity = prefs[Keys.tuningLightLeakIntensity] ?: 1.0f,
            ),
            shootingMode = prefs[Keys.shootingMode]?.let { runCatching { ShootingMode.valueOf(it) }.getOrNull() } ?: ShootingMode.PHOTO,
            timerSeconds = prefs[Keys.timerSeconds] ?: 0,
            saveDng = prefs[Keys.saveDng] ?: false,
            longExposureFrames = prefs[Keys.longExposureFrames] ?: 15,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.filmId] = settings.filmId
            prefs[Keys.lightLeakEnabled] = settings.lightLeakEnabled
            if (settings.selectedLensId != null) prefs[Keys.selectedLensId] = settings.selectedLensId else prefs.remove(Keys.selectedLensId)
            prefs[Keys.evIndex] = settings.evIndex
            prefs[Keys.dateImprintEnabled] = settings.dateImprintEnabled
            prefs[Keys.dateImprintStyle] = settings.dateImprintStyle.name
            prefs[Keys.dateImprintColor] = settings.dateImprintColor.name
            prefs[Keys.dateImprintFont] = settings.dateImprintFont.name
            prefs[Keys.dateImprintSize] = settings.dateImprintSize.name
            prefs[Keys.dateImprintPosition] = settings.dateImprintPosition.name
            prefs[Keys.dateImprintGlow] = settings.dateImprintGlow
            prefs[Keys.dateImprintBlur] = settings.dateImprintBlur
            prefs[Keys.dateImprintOpacity] = settings.dateImprintOpacity
            prefs[Keys.dateImprintBlurRepeat] = settings.dateImprintBlurRepeat
            prefs[Keys.totalShotsTaken] = settings.totalShotsTaken
            prefs[Keys.favoriteFilmIds] = settings.favoriteFilmIds.joinToString(",")
            prefs[Keys.flashEnabled] = settings.flashEnabled
            prefs[Keys.mainZoomRatio] = settings.mainZoomRatio
            prefs[Keys.focusDurationSeconds] = settings.focusDurationSeconds
            prefs[Keys.histogramEnabled] = settings.histogramEnabled
            prefs[Keys.lutPreviewEnabled] = settings.lutPreviewEnabled
            prefs[Keys.lutPreviewQuality] = settings.lutPreviewQuality.name
            prefs[Keys.cameraParamsEnabled] = settings.cameraParamsEnabled
            prefs[Keys.levelEnabled] = settings.levelEnabled
            prefs[Keys.gridMode] = settings.gridMode
            prefs[Keys.lutIntensity] = settings.filmTuning.lutIntensity
            prefs[Keys.tuningGrainAmount] = settings.filmTuning.grainAmount
            prefs[Keys.tuningGrainSize] = settings.filmTuning.grainSize
            prefs[Keys.tuningLightLeakIntensity] = settings.filmTuning.lightLeakIntensity
            prefs[Keys.shootingMode] = settings.shootingMode.name
            prefs[Keys.timerSeconds] = settings.timerSeconds
            prefs[Keys.saveDng] = settings.saveDng
            prefs[Keys.longExposureFrames] = settings.longExposureFrames
        }
    }
}
