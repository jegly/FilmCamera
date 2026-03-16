package com.photoncam.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.photoncam.film.FilmCatalog
import com.photoncam.processing.DateImprintColor
import com.photoncam.processing.DateImprintFont
import com.photoncam.processing.DateImprintPosition
import com.photoncam.processing.DateImprintSize
import com.photoncam.processing.DateImprintStyle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "photoncam_settings")

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
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            filmId = prefs[Keys.filmId] ?: FilmCatalog.default.id,
            lightLeakEnabled = prefs[Keys.lightLeakEnabled] ?: true,
            selectedLensId = prefs[Keys.selectedLensId],
            evIndex = prefs[Keys.evIndex] ?: 0,
            dateImprintEnabled = prefs[Keys.dateImprintEnabled] ?: true,
            dateImprintStyle = prefs[Keys.dateImprintStyle]
                ?.let { runCatching { DateImprintStyle.valueOf(it) }.getOrNull() }
                ?: DateImprintStyle.CLASSIC,
            dateImprintColor = prefs[Keys.dateImprintColor]
                ?.let { runCatching { DateImprintColor.valueOf(it) }.getOrNull() }
                ?: DateImprintColor.AMBER,
            dateImprintFont = prefs[Keys.dateImprintFont]
                ?.let { runCatching { DateImprintFont.valueOf(it) }.getOrNull() }
                ?: DateImprintFont.LED,
            dateImprintSize = prefs[Keys.dateImprintSize]
                ?.let { runCatching { DateImprintSize.valueOf(it) }.getOrNull() }
                ?: DateImprintSize.MEDIUM,
            dateImprintPosition = prefs[Keys.dateImprintPosition]
                ?.let { runCatching { DateImprintPosition.valueOf(it) }.getOrNull() }
                ?: DateImprintPosition.BOTTOM_RIGHT,
            dateImprintGlow = prefs[Keys.dateImprintGlow] ?: 100,
            dateImprintBlur = prefs[Keys.dateImprintBlur] ?: 50,
            dateImprintOpacity = prefs[Keys.dateImprintOpacity] ?: 50,
            dateImprintBlurRepeat = prefs[Keys.dateImprintBlurRepeat] ?: 3,
            totalShotsTaken = prefs[Keys.totalShotsTaken] ?: 0,
            favoriteFilmIds = prefs[Keys.favoriteFilmIds]
                ?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
            flashEnabled = prefs[Keys.flashEnabled] ?: false,
        )
    }

    suspend fun save(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.filmId] = settings.filmId
            prefs[Keys.lightLeakEnabled] = settings.lightLeakEnabled
            if (settings.selectedLensId != null) {
                prefs[Keys.selectedLensId] = settings.selectedLensId
            } else {
                prefs.remove(Keys.selectedLensId)
            }
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
        }
    }
}
