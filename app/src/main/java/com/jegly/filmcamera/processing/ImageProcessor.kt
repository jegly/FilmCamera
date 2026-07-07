package com.jegly.filmcamera.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.exifinterface.media.ExifInterface
import com.jegly.filmcamera.film.FilmStock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.random.Random

/** Maximum pixel dimension for processing. Images larger than this are subsampled at decode. */
private const val MAX_PROCESS_DIM = 4096

@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lutProcessor: LutProcessor,
    private val grainProcessor: GrainProcessor,
    private val dateImprintProcessor: DateImprintProcessor,
) {
    suspend fun process(
        inputFile: File,
        film: FilmStock,
        dateImprintEnabled: Boolean,
        dateImprintStyle: DateImprintStyle = DateImprintStyle.CLASSIC,
        dateImprintColor: DateImprintColor = DateImprintColor.AMBER,
        dateImprintFont: DateImprintFont = DateImprintFont.LED,
        dateImprintSize: DateImprintSize = DateImprintSize.MEDIUM,
        dateImprintPosition: DateImprintPosition = DateImprintPosition.BOTTOM_RIGHT,
        dateImprintGlow: Int = 100,
        dateImprintBlur: Int = 50,
        dateImprintOpacity: Int = 50,
        dateImprintBlurRepeat: Int = 3,
        lightLeakEnabled: Boolean = true,
    ): Result<File> = withContext(Dispatchers.Default) {
        runCatching {
            var bitmap = decodeSampled(inputFile, MAX_PROCESS_DIM)

            // 0. EXIF rotation — BitmapFactory ignores orientation metadata; rotate
            //    the pixels before any processing so all subsequent steps see upright pixels.
            bitmap = applyExifRotation(bitmap, inputFile)

            // 1. Date imprint — burned onto the raw image first so film processing
            //    ages and colours the timestamp along with the rest of the photo.
            if (dateImprintEnabled) {
                bitmap = dateImprintProcessor.burn(
                    bitmap,
                    dateImprintStyle,
                    dateImprintColor,
                    dateImprintFont,
                    dateImprintSize,
                    dateImprintPosition,
                    dateImprintGlow,
                    dateImprintBlur,
                    dateImprintOpacity,
                    dateImprintBlurRepeat,
                )
            }

            // 2. Color grade (LUT or B&W desaturation)
            bitmap = if (film.isBlackAndWhite) {
                var bw = bitmap.toBlackAndWhite()
                applyFilmLut(bw, film)?.let { bw = it }
                bw
            } else {
                applyFilmLut(bitmap, film) ?: bitmap
            }

            // 3. Per-film color grade: saturation + contrast + shadow lift (in-place)
            applyColorGrade(bitmap, film.saturation, film.contrast, film.shadowLift)

            // 4. Highlight rolloff curve (in-place)
            applyHighlightRolloff(bitmap, film.highlightRolloff)

            // 5. Grain (in-place)
            // BUG FIX: seed with file lastModified XOR nanoTime so rapid-fire shots
            // get different grain patterns even if captured within the same millisecond.
            val grainSeed = inputFile.lastModified() xor System.nanoTime()
            bitmap = grainProcessor.applyGrain(bitmap, film.grainAmount, film.grainSize, grainSeed)

            // 6. Light leak (needs new bitmap for Canvas shader blending)
            if (lightLeakEnabled && Random.nextFloat() < film.lightLeakProbability) {
                bitmap = applyLightLeak(bitmap)
            }

            // 7. Save
            val outputDir = File(context.cacheDir, "processed").apply { mkdirs() }
            val outputFile = File(outputDir, inputFile.nameWithoutExtension + "_film.jpg")
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            bitmap.recycle()
            outputFile
        }
    }

    /**
     * Applies the film's LUT — an imported file if present, otherwise the built-in
     * raw resource. Returns null when the film has no LUT (parametric-only).
     */
    private fun applyFilmLut(bitmap: Bitmap, film: FilmStock): Bitmap? {
        film.lutFilePath?.let { path ->
            val f = File(path)
            if (f.exists()) return lutProcessor.applyLut(bitmap, f)
        }
        film.lutResId?.let { return lutProcessor.applyLut(bitmap, it) }
        return null
    }

    /**
     * Reads the EXIF orientation tag from [file] and physically rotates/flips [bitmap]
     * to match. Returns a mutable upright bitmap. The input is recycled if a new bitmap
     * is created.
     */
    private fun applyExifRotation(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90    -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180   -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270   -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL                 -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL                   -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE    -> { matrix.postScale(-1f, 1f); matrix.postRotate(90f) }
            ExifInterface.ORIENTATION_TRANSVERSE   -> { matrix.postScale(-1f, 1f); matrix.postRotate(270f) }
            else -> return bitmap  // ORIENTATION_NORMAL or ORIENTATION_UNDEFINED — no-op
        }

        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()

        // createBitmap with a matrix may return an immutable bitmap; downstream processors
        // call setPixels() which requires mutability, so copy if needed.
        return if (rotated.isMutable) rotated else {
            val mutable = rotated.copy(Bitmap.Config.ARGB_8888, true)
            rotated.recycle()
            mutable
        }
    }

    /**
     * Decodes [file] into a mutable Bitmap subsampled so that neither dimension
     * exceeds [maxDim]. Uses power-of-two inSampleSize for fast hardware decode,
     * then a precise scale pass if still oversized.
     */
    private fun decodeSampled(file: File, maxDim: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sampleSize = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / (sampleSize * 2) >= maxDim) sampleSize *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inMutable = true
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: error("Failed to decode ${file.name}")

        val edge = max(bitmap.width, bitmap.height)
        if (edge > maxDim) {
            val ratio = maxDim.toFloat() / edge
            val sw = (bitmap.width * ratio).toInt()
            val sh = (bitmap.height * ratio).toInt()
            val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
            bitmap.recycle()
            bitmap = scaled
        }
        return bitmap
    }

    /**
     * Per-film color grade applied in-place on the pixel array.
     * Order: contrast → shadow lift → saturation.
     * Equivalent to the previous ColorMatrix approach but without allocating a second bitmap.
     */
    private fun applyColorGrade(bitmap: Bitmap, saturation: Float, contrast: Float, shadowLift: Float) {
        if (saturation == 1f && contrast == 1f && shadowLift == 0f) return

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val offset = shadowLift + (1f - contrast) * 128f
        // Saturation weights matching Android's ColorMatrix.setSaturation()
        val invSat = 1f - saturation
        val rW = invSat * 0.213f
        val gW = invSat * 0.715f
        val bW = invSat * 0.072f

        for (i in pixels.indices) {
            val px = pixels[i]
            var r = ((px shr 16) and 0xFF).toFloat()
            var g = ((px shr 8)  and 0xFF).toFloat()
            var b = ( px         and 0xFF).toFloat()

            // Contrast + shadow lift
            r = r * contrast + offset
            g = g * contrast + offset
            b = b * contrast + offset

            // Saturation
            val lum = rW * r + gW * g + bW * b
            r = (lum + saturation * r).coerceIn(0f, 255f)
            g = (lum + saturation * g).coerceIn(0f, 255f)
            b = (lum + saturation * b).coerceIn(0f, 255f)

            pixels[i] = (px and 0xFF000000.toInt()) or
                (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Highlight rolloff applied in-place: `out = in * scale + lift`.
     */
    private fun applyHighlightRolloff(bitmap: Bitmap, strength: Float) {
        if (strength == 0f) return
        val s = strength.coerceIn(0f, 1f)
        val scale = 1f - s * 0.18f
        val lift = s * 14f

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val px = pixels[i]
            val r = ((((px shr 16) and 0xFF) * scale + lift).coerceIn(0f, 255f)).toInt()
            val g = ((((px shr 8)  and 0xFF) * scale + lift).coerceIn(0f, 255f)).toInt()
            val b = ((( px         and 0xFF) * scale + lift).coerceIn(0f, 255f)).toInt()
            pixels[i] = (px and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    /**
     * Light leak overlay using Canvas gradient shaders.
     * Requires a separate destination bitmap since gradients are composited via SCREEN blend.
     * Uses createBitmap (empty allocation) rather than bitmap.copy to skip an unnecessary
     * pixel-copy of the source before immediately overwriting with the draw call.
     */
    private fun applyLightLeak(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        // Draw original first, then the leak overlay on top
        canvas.drawBitmap(bitmap, 0f, 0f, null)

        val leakType = LightLeakType.entries.random()
        val leakPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            alpha = (60 + Random.nextInt(80))
        }

        val w = result.width.toFloat()
        val h = result.height.toFloat()

        when (leakType) {
            LightLeakType.CORNER_TOP_LEFT -> {
                leakPaint.shader = android.graphics.RadialGradient(
                    0f, 0f, w * 0.7f,
                    intArrayOf(0xFFFF8C00.toInt(), 0x00FF8C00.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, leakPaint)
            }
            LightLeakType.EDGE_RIGHT -> {
                leakPaint.shader = android.graphics.LinearGradient(
                    w, 0f, w * 0.55f, 0f,
                    intArrayOf(0xFFFFAA00.toInt(), 0x00FFAA00.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(w * 0.5f, 0f, w, h, leakPaint)
            }
            LightLeakType.HORIZONTAL_BAND -> {
                val y = Random.nextFloat() * h
                leakPaint.shader = android.graphics.LinearGradient(
                    0f, y - h * 0.12f, 0f, y + h * 0.12f,
                    intArrayOf(0x00FF6600.toInt(), 0xFFFF6600.toInt(), 0x00FF6600.toInt()),
                    null, android.graphics.Shader.TileMode.CLAMP,
                )
                canvas.drawRect(0f, 0f, w, h, leakPaint)
            }
        }

        bitmap.recycle()
        return result
    }

    /**
     * Desaturates a bitmap in-place by applying luminance weights per pixel.
     * Avoids allocating a second bitmap.
     */
    private fun Bitmap.toBlackAndWhite(): Bitmap {
        val width = this.width
        val height = this.height
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val px = pixels[i]
            val r = (px shr 16) and 0xFF
            val g = (px shr 8)  and 0xFF
            val b =  px         and 0xFF
            val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b).toInt().coerceIn(0, 255)
            pixels[i] = (px and 0xFF000000.toInt()) or (lum shl 16) or (lum shl 8) or lum
        }
        setPixels(pixels, 0, width, 0, 0, width, height)
        return this
    }

    private enum class LightLeakType {
        CORNER_TOP_LEFT, EDGE_RIGHT, HORIZONTAL_BAND
    }
}
