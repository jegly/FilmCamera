package com.jegly.filmcamera.processing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jegly.filmcamera.film.FilmCatalog
import com.jegly.filmcamera.utils.GalleryExporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class PhotoProcessingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val imageProcessor: ImageProcessor,
    private val galleryExporter: GalleryExporter,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val rawFilePath = inputData.getString(KEY_RAW_FILE_PATH) ?: return Result.failure()
        val filmId = inputData.getString(KEY_FILM_ID) ?: return Result.failure()
        val dateEnabled = inputData.getBoolean(KEY_DATE_IMPRINT_ENABLED, true)
        val dateStyle = runCatching {
            DateImprintStyle.valueOf(inputData.getString(KEY_DATE_STYLE) ?: "")
        }.getOrDefault(DateImprintStyle.CLASSIC)
        val dateColor = runCatching {
            DateImprintColor.valueOf(inputData.getString(KEY_DATE_COLOR) ?: "")
        }.getOrDefault(DateImprintColor.AMBER)
        val dateFont = runCatching {
            DateImprintFont.valueOf(inputData.getString(KEY_DATE_FONT) ?: "")
        }.getOrDefault(DateImprintFont.LED)
        val dateSize = runCatching {
            DateImprintSize.valueOf(inputData.getString(KEY_DATE_SIZE) ?: "")
        }.getOrDefault(DateImprintSize.MEDIUM)
        val datePosition = runCatching {
            DateImprintPosition.valueOf(inputData.getString(KEY_DATE_POSITION) ?: "")
        }.getOrDefault(DateImprintPosition.BOTTOM_RIGHT)
        val dateGlow = inputData.getInt(KEY_DATE_GLOW, 70)
        val dateBlur = inputData.getInt(KEY_DATE_BLUR, 0)
        val dateOpacity = inputData.getInt(KEY_DATE_OPACITY, 50)
        val dateBlurRepeat = inputData.getInt(KEY_DATE_BLUR_REPEAT, 3)
        val lightLeak = inputData.getBoolean(KEY_LIGHT_LEAK_ENABLED, true)

        val rawFile = File(rawFilePath)

        // BUG FIX: validate the file exists and is a reasonable size before processing
        if (!rawFile.exists()) return Result.failure()
        if (rawFile.length() > MAX_INPUT_FILE_BYTES) {
            rawFile.delete()
            return Result.failure()
        }

        val film = FilmCatalog.findById(filmId) ?: run {
            rawFile.delete()
            return Result.failure()
        }

        val processingResult = imageProcessor.process(
            inputFile = rawFile,
            film = film,
            dateImprintEnabled = dateEnabled,
            dateImprintStyle = dateStyle,
            dateImprintColor = dateColor,
            dateImprintFont = dateFont,
            dateImprintSize = dateSize,
            dateImprintPosition = datePosition,
            dateImprintGlow = dateGlow,
            dateImprintBlur = dateBlur,
            dateImprintOpacity = dateOpacity,
            dateImprintBlurRepeat = dateBlurRepeat,
            lightLeakEnabled = lightLeak,
        )

        // BUG FIX: always delete raw file regardless of success or failure
        rawFile.delete()

        return processingResult.fold(
            onSuccess = { processedFile ->
                galleryExporter.saveToGallery(processedFile).fold(
                    onSuccess = { uri ->
                        Result.success(workDataOf(KEY_GALLERY_URI to uri.toString()))
                    },
                    onFailure = { Result.failure() },
                )
            },
            onFailure = { Result.failure() },
        )
    }

    companion object {
        const val KEY_RAW_FILE_PATH = "raw_file_path"
        const val KEY_FILM_ID = "film_id"
        const val KEY_DATE_IMPRINT_ENABLED = "date_imprint_enabled"
        const val KEY_DATE_STYLE = "date_style"
        const val KEY_DATE_COLOR = "date_color"
        const val KEY_DATE_FONT = "date_font"
        const val KEY_DATE_SIZE = "date_size"
        const val KEY_DATE_POSITION = "date_position"
        const val KEY_DATE_GLOW = "date_glow"
        const val KEY_DATE_BLUR = "date_blur"
        const val KEY_DATE_OPACITY = "date_opacity"
        const val KEY_DATE_BLUR_REPEAT = "date_blur_repeat"
        const val KEY_LIGHT_LEAK_ENABLED = "light_leak_enabled"
        const val KEY_GALLERY_URI = "gallery_uri"
        const val WORK_TAG = "filmcamera_photo_processing"
        // BUG FIX: reject absurdly large files before decoding (50 MB cap)
        private const val MAX_INPUT_FILE_BYTES = 50L * 1024 * 1024
    }
}
