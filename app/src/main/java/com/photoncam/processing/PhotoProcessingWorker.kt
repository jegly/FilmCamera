package com.photoncam.processing

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.photoncam.film.FilmCatalog
import com.photoncam.utils.GalleryExporter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

/**
 * WorkManager worker that runs the full image processing pipeline.
 * Runs as a foreground service so Android cannot kill it even if the app is closed.
 * Input/output data uses string keys defined in the companion object.
 */
@HiltWorker
class PhotoProcessingWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val imageProcessor: ImageProcessor,
    private val galleryExporter: GalleryExporter,
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Photo Processing",
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("PhotonCam")
            .setContentText("Developing film…")
            .setOngoing(true)
            .setSilent(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        setForeground(getForegroundInfo())

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
        if (!rawFile.exists()) return Result.failure()

        val film = FilmCatalog.findById(filmId) ?: return Result.failure()

        return imageProcessor.process(
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
        ).fold(
            onSuccess = { processedFile ->
                galleryExporter.saveToGallery(processedFile).fold(
                    onSuccess = { uri ->
                        rawFile.delete()
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
        const val WORK_TAG = "photoncam_photo_processing"
        private const val CHANNEL_ID = "photoncam_processing"
        private const val NOTIFICATION_ID = 1001
    }
}
