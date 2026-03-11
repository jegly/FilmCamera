package com.photoncam.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import com.photoncam.film.FilmStock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lutProcessor: LutProcessor,
    private val grainProcessor: GrainProcessor,
    private val dateImprintProcessor: DateImprintProcessor,
) {
    /**
     * Full film emulation pipeline applied on a background dispatcher.
     *
     * Steps:
     * 1. Load raw JPEG from [inputFile]
     * 2. Apply color grading via 3D LUT (or desaturate for B&W)
     * 3. Apply per-channel film curve (highlight rolloff)
     * 4. Apply grain
     * 5. Optionally add a light leak overlay
     * 6. Optionally burn date imprint
     * 7. Save result as JPEG and return the output [File]
     */
    suspend fun process(
        inputFile: File,
        film: FilmStock,
        dateImprintEnabled: Boolean,
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            val options = BitmapFactory.Options().apply { inMutable = true }
            var bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
                ?: error("Failed to decode ${inputFile.name}")

            // 1. Color grade
            bitmap = if (film.isBlackAndWhite) {
                bitmap.toBlackAndWhite()
            } else {
                film.lutResId?.let { resId ->
                    lutProcessor.applyLut(bitmap, resId)
                } ?: bitmap
            }

            // 2. Highlight rolloff curve
            bitmap = applyHighlightRolloff(bitmap, film.highlightRolloff)

            // 3. Grain
            bitmap = grainProcessor.applyGrain(bitmap, film.grainAmount, film.grainSize)

            // 4. Light leak
            if (Random.nextFloat() < film.lightLeakProbability) {
                bitmap = applyLightLeak(bitmap)
            }

            // 5. Date imprint
            if (dateImprintEnabled) {
                bitmap = dateImprintProcessor.burn(bitmap)
            }

            // 6. Save
            val outputDir = File(context.cacheDir, "processed").apply { mkdirs() }
            val outputFile = File(outputDir, inputFile.nameWithoutExtension + "_film.jpg")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()
            outputFile
        }
    }

    private fun applyHighlightRolloff(bitmap: Bitmap, strength: Float): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val cm = buildRolloffColorMatrix(strength)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(result).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return result
    }

    /**
     * Builds a ColorMatrix that compresses highlights and lifts shadows slightly,
     * simulating analogue film's characteristic S-curve.
     */
    private fun buildRolloffColorMatrix(strength: Float): ColorMatrix {
        val s = strength.coerceIn(0f, 1f)
        // Scale down gain in highs and add a small shadow lift
        val scale = 1f - s * 0.12f
        val lift = s * 8f
        return ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, lift,
                0f, scale, 0f, 0f, lift,
                0f, 0f, scale, 0f, lift,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun applyLightLeak(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val leakType = LightLeakType.entries.random()
        val leakPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            alpha = (40 + Random.nextInt(60)) // 40–100 / 255
        }

        val w = result.width.toFloat()
        val h = result.height.toFloat()

        when (leakType) {
            LightLeakType.CORNER_TOP_LEFT -> {
                val shader = android.graphics.RadialGradient(
                    0f, 0f, w * 0.6f,
                    intArrayOf(0xFFFF8C00.toInt(), 0x00FF8C00.toInt()),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                leakPaint.shader = shader
                canvas.drawRect(0f, 0f, w, h, leakPaint)
            }
            LightLeakType.EDGE_RIGHT -> {
                val shader = android.graphics.LinearGradient(
                    w, 0f, w * 0.6f, 0f,
                    intArrayOf(0xFFFFAA00.toInt(), 0x00FFAA00.toInt()),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                leakPaint.shader = shader
                canvas.drawRect(w * 0.5f, 0f, w, h, leakPaint)
            }
            LightLeakType.HORIZONTAL_BAND -> {
                val y = Random.nextFloat() * h
                val shader = android.graphics.LinearGradient(
                    0f, y - h * 0.1f, 0f, y + h * 0.1f,
                    intArrayOf(0x00FF6600.toInt(), 0xFFFF6600.toInt(), 0x00FF6600.toInt()),
                    null,
                    android.graphics.Shader.TileMode.CLAMP,
                )
                leakPaint.shader = shader
                canvas.drawRect(0f, 0f, w, h, leakPaint)
            }
        }

        bitmap.recycle()
        return result
    }

    private fun Bitmap.toBlackAndWhite(): Bitmap {
        val result = copy(Bitmap.Config.ARGB_8888, true)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(result).drawBitmap(this, Rect(0, 0, width, height), Rect(0, 0, width, height), paint)
        recycle()
        return result
    }

    private enum class LightLeakType {
        CORNER_TOP_LEFT, EDGE_RIGHT, HORIZONTAL_BAND
    }
}
