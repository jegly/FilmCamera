package com.jegly.filmcamera.film

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores user-imported .cube / HaldCLUT LUT files under filesDir/luts and exposes
 * them as [FilmStock]s (brand [FilmBrand.CUSTOM]). Each stored file's name doubles
 * as the film's display name, so no separate index is needed.
 *
 * Imported films use neutral defaults for grain/contrast so the imported LUT is the
 * dominant look. The GPU effect and the CPU photo path both load the LUT by file.
 */
@Singleton
class CustomLutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File by lazy { File(context.filesDir, "luts").apply { mkdirs() } }

    private val _films = MutableStateFlow(loadFilms())
    val films: StateFlow<List<FilmStock>> = _films.asStateFlow()

    private fun loadFilms(): List<FilmStock> =
        dir.listFiles()
            ?.filter { it.isFile && it.length() > 0 }
            ?.sortedBy { it.name.lowercase() }
            ?.map { fileToFilm(it) }
            ?: emptyList()

    private fun fileToFilm(file: File): FilmStock {
        val displayName = file.nameWithoutExtension.replace('_', ' ').trim().ifEmpty { "Custom LUT" }
        return FilmStock(
            id = "custom_${file.name}",
            brand = FilmBrand.CUSTOM,
            name = displayName,
            iso = 400,
            category = FilmCategory.COLOR,
            lutResId = null,
            lutFilePath = file.absolutePath,
            grainAmount = 0.12f,
            lightLeakProbability = 0f,
            saturation = 1.0f,
            contrast = 1.0f,
            description = "Imported LUT",
        )
    }

    fun findById(id: String): FilmStock? = _films.value.firstOrNull { it.id == id }

    /**
     * Copy the LUT pointed to by [uri] into app storage under [displayName].
     * Returns the created [FilmStock] on success.
     */
    suspend fun import(uri: Uri, displayName: String): Result<FilmStock> = withContext(Dispatchers.IO) {
        runCatching {
            val safeBase = displayName.trim().ifEmpty { "lut" }
                .replace(Regex("[^A-Za-z0-9 _-]"), "").replace(' ', '_').take(48)
            var dest = File(dir, "$safeBase.cube")
            var i = 1
            while (dest.exists()) { dest = File(dir, "${safeBase}_$i.cube"); i++ }

            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot open $uri")

            if (dest.length() == 0L) { dest.delete(); error("Imported LUT is empty") }

            val film = fileToFilm(dest)
            _films.value = loadFilms()
            film
        }
    }

    fun delete(id: String) {
        _films.value.firstOrNull { it.id == id }?.lutFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        _films.value = loadFilms()
    }
}
