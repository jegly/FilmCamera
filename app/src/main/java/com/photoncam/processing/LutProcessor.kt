package com.photoncam.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a 3D LUT (.cube format, 32³ or 64³) to a [Bitmap].
 *
 * The .cube file format:
 *   - Lines starting with '#' are comments.
 *   - "LUT_SIZE N" declares an N³ cube.
 *   - Subsequent lines are "R G B" triples in [0,1] range, ordered B-fastest.
 */
@Singleton
class LutProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** In-memory LUT cache keyed by resource id. */
    private val cache = mutableMapOf<Int, FloatArray>()

    fun applyLut(bitmap: Bitmap, @RawRes lutResId: Int): Bitmap {
        val lut = cache.getOrPut(lutResId) { loadLut(lutResId) }
        val size = Math.cbrt(lut.size / 3.0).toInt()
        return applyLutToBitmap(bitmap, lut, size)
    }

    private fun loadLut(@RawRes resId: Int): FloatArray {
        val lines = context.resources.openRawResource(resId)
            .bufferedReader()
            .readLines()

        var lutSize = 0
        val values = mutableListOf<Float>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith('#') || trimmed.isEmpty() -> continue
                trimmed.startsWith("LUT_SIZE") -> lutSize = trimmed.split("\\s+".toRegex())[1].toInt()
                trimmed.startsWith("TITLE") || trimmed.startsWith("DOMAIN") -> continue
                else -> {
                    val parts = trimmed.split("\\s+".toRegex())
                    if (parts.size >= 3) {
                        values.add(parts[0].toFloat())
                        values.add(parts[1].toFloat())
                        values.add(parts[2].toFloat())
                    }
                }
            }
        }

        return values.toFloatArray()
    }

    private fun applyLutToBitmap(bitmap: Bitmap, lut: FloatArray, size: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val scale = (size - 1).toFloat()

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            // Trilinear interpolation
            val ri = (r * scale).toInt().coerceIn(0, size - 2)
            val gi = (g * scale).toInt().coerceIn(0, size - 2)
            val bi = (b * scale).toInt().coerceIn(0, size - 2)

            val rf = r * scale - ri
            val gf = g * scale - gi
            val bf = b * scale - bi

            val (nr, ng, nb) = trilinear(lut, size, ri, gi, bi, rf, gf, bf)

            pixels[i] = (pixel and 0xFF000000.toInt()) or
                ((nr * 255).toInt().coerceIn(0, 255) shl 16) or
                ((ng * 255).toInt().coerceIn(0, 255) shl 8) or
                (nb * 255).toInt().coerceIn(0, 255)
        }

        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        return result
    }

    private fun trilinear(
        lut: FloatArray,
        size: Int,
        ri: Int, gi: Int, bi: Int,
        rf: Float, gf: Float, bf: Float,
    ): Triple<Float, Float, Float> {
        fun idx(r: Int, g: Int, b: Int) = (b * size * size + g * size + r) * 3

        val c000 = idx(ri, gi, bi)
        val c100 = idx(ri + 1, gi, bi)
        val c010 = idx(ri, gi + 1, bi)
        val c110 = idx(ri + 1, gi + 1, bi)
        val c001 = idx(ri, gi, bi + 1)
        val c101 = idx(ri + 1, gi, bi + 1)
        val c011 = idx(ri, gi + 1, bi + 1)
        val c111 = idx(ri + 1, gi + 1, bi + 1)

        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        fun channel(off: Int): Float {
            val v0 = lerp(lerp(lut[c000 + off], lut[c100 + off], rf), lerp(lut[c010 + off], lut[c110 + off], rf), gf)
            val v1 = lerp(lerp(lut[c001 + off], lut[c101 + off], rf), lerp(lut[c011 + off], lut[c111 + off], rf), gf)
            return lerp(v0, v1, bf)
        }

        return Triple(channel(0), channel(1), channel(2))
    }
}
