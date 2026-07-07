package com.jegly.filmcamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Simulates long exposure by capturing [frameCount] frames and averaging
 * them pixel-by-pixel. This produces the characteristic motion-blur /
 * light-trail effect of long exposure film photography.
 *
 * Pixel 6–10 can sustain burst capture at ~5–10 fps in JPEG mode,
 * so 15 frames = roughly 1.5–3 seconds of "exposure".
 */
@Singleton
class LongExposureCapture @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Capture [frameCount] JPEG frames via [imageCapture] and average them.
     * Returns a File containing the accumulated JPEG in the app's cache dir.
     */
    suspend fun capture(
        imageCapture: ImageCapture,
        executor: Executor,
        frameCount: Int = 15,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            // Accumulate RGB channels as Long arrays to avoid overflow
            var accumR: LongArray? = null
            var accumG: LongArray? = null
            var accumB: LongArray? = null
            var width = 0
            var height = 0

            repeat(frameCount) { i ->
                onProgress(i + 1, frameCount)

                val bitmap = captureOneBitmap(imageCapture, executor)
                    .getOrThrow()

                val w = bitmap.width
                val h = bitmap.height

                if (accumR == null) {
                    width = w; height = h
                    accumR = LongArray(w * h)
                    accumG = LongArray(w * h)
                    accumB = LongArray(w * h)
                }

                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                bitmap.recycle()

                for (j in pixels.indices) {
                    val px = pixels[j]
                    accumR!![j] += (px shr 16) and 0xFF
                    accumG!![j] += (px shr  8) and 0xFF
                    accumB!![j] +=  px         and 0xFF
                }
            }

            // Average and compose final bitmap
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val outPixels = IntArray(width * height)
            for (j in outPixels.indices) {
                val r = (accumR!![j] / frameCount).toInt().coerceIn(0, 255)
                val g = (accumG!![j] / frameCount).toInt().coerceIn(0, 255)
                val b = (accumB!![j] / frameCount).toInt().coerceIn(0, 255)
                outPixels[j] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            result.setPixels(outPixels, 0, width, 0, 0, width, height)

            // Save to cache
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val outFile = File(dir, "LONGEXP_$ts.jpg")
            FileOutputStream(outFile).use { result.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            result.recycle()

            outFile
        }
    }

    private suspend fun captureOneBitmap(
        imageCapture: ImageCapture,
        executor: Executor,
    ): Result<Bitmap> = suspendCancellableCoroutine { cont ->
        imageCapture.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = image.toBitmap()
                    image.close()
                    cont.resume(Result.success(bmp))
                }
                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }
}
