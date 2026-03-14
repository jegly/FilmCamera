package com.photoncam.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.util.LruCache
import androidx.annotation.RawRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies a 3D LUT to a [Bitmap]. Supports two input formats:
 *
 * 1. **.cube** — standard 3D LUT text format (32³ or 64³):
 *      - Lines starting with '#' are comments.
 *      - "LUT_SIZE N" declares an N³ cube.
 *      - Subsequent "R G B" triples in [0,1], ordered B-fastest.
 *
 * 2. **HaldCLUT PNG** — 2D PNG encoding a 3D LUT (level 8 = 512×512 common):
 *      - Image dimension d = L³ (e.g. d=512 → L=8, steps=L²=64 per channel).
 *      - Pixel at (x,y) → color (ri, gi, bi) where:
 *          bx = bi % L,  by = bi / L,  x = bx*steps + ri,  y = by*steps + gi
 *      - Auto-detected: if the raw resource decodes as a Bitmap it is treated as HaldCLUT.
 */
@Singleton
class LutProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * LRU cache of loaded LUT data, capped at 3 entries (~9 MB for 64³ HaldCLUTs).
     * Older entries are evicted automatically when a new film is selected.
     */
    private val cache = LruCache<Int, FloatArray>(3)

    fun applyLut(bitmap: Bitmap, @RawRes lutResId: Int): Bitmap {
        val lut = cache[lutResId] ?: loadLut(lutResId).also { cache.put(lutResId, it) }
        val size = Math.cbrt(lut.size / 3.0).toInt()
        return applyLutToBitmap(bitmap, lut, size)
    }

    private fun loadLut(@RawRes resId: Int): FloatArray {
        // Auto-detect format by reading bitmap bounds (no pixel decode, no OOM risk).
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        }

        if (boundsOpts.outWidth <= 0) {
            // Not a bitmap format — must be a .cube text LUT.
            return loadCubeLut(resId)
        }

        return if (boundsOpts.outWidth > 512) {
            // Large Hald CLUT (e.g. level-16 = 4096×4096): subsample in color-space
            // using BitmapRegionDecoder to avoid loading the full image into memory.
            loadHaldClutSubsampled(resId, boundsOpts.outWidth)
        } else {
            val bitmap = runCatching {
                context.resources.openRawResource(resId).use { BitmapFactory.decodeStream(it) }
            }.getOrNull() ?: return loadCubeLut(resId)
            val lut = loadHaldClut(bitmap)
            bitmap.recycle()
            lut
        }
    }

    /**
     * Downsamples a large Hald CLUT PNG (e.g. level-16, 4096×4096) to a level-8
     * equivalent FloatArray (~3 MB) without ever loading the full image into RAM.
     *
     * Strategy: for each of the 64³ level-8 target (ri8, gi8, bi8) entries, compute
     * the corresponding source pixel coordinates by scaling the indices linearly into
     * the source grid, then read only those pixels using BitmapRegionDecoder row-strips.
     *
     * @param resId   raw resource id of the PNG
     * @param srcDim  image width (== height) in pixels, e.g. 4096
     */
    private fun loadHaldClutSubsampled(@RawRes resId: Int, srcDim: Int): FloatArray {
        val srcL     = Math.cbrt(srcDim.toDouble()).toInt()   // e.g. 16 for 4096-px level-16
        val srcSteps = srcL * srcL                            // e.g. 256 color steps per channel
        val dstSteps = 64                                     // target: level-8 (8²=64 steps)

        // lut is B-fastest, same ordering as .cube / loadHaldClut output.
        val lut = FloatArray(dstSteps * dstSteps * dstSteps * 3)

        // Build a sorted map: srcY → list of (srcX, lutWriteIndex) for every needed pixel.
        // This lets us scan the image top-to-bottom in sequential strips.
        val yMap = HashMap<Int, MutableList<Pair<Int, Int>>>(dstSteps * dstSteps)

        for (bi8 in 0 until dstSteps) {
            for (gi8 in 0 until dstSteps) {
                for (ri8 in 0 until dstSteps) {
                    // Map level-8 index to nearest level-N source index (color-space interpolation).
                    val riSrc = ri8 * (srcSteps - 1) / (dstSteps - 1)
                    val giSrc = gi8 * (srcSteps - 1) / (dstSteps - 1)
                    val biSrc = bi8 * (srcSteps - 1) / (dstSteps - 1)

                    // Hald CLUT pixel layout: flat index = riSrc + giSrc×srcSteps + biSrc×srcSteps²
                    val flatIdx = riSrc + giSrc * srcSteps + biSrc * srcSteps * srcSteps
                    val srcX = flatIdx % srcDim
                    val srcY = flatIdx / srcDim

                    val lutIdx = (bi8 * dstSteps * dstSteps + gi8 * dstSteps + ri8) * 3
                    yMap.getOrPut(srcY) { mutableListOf() }.add(Pair(srcX, lutIdx))
                }
            }
        }

        val sortedYs = yMap.keys.sorted()
        if (sortedYs.isEmpty()) return lut

        // Open BitmapRegionDecoder once; process in sequential 16-row strips to limit
        // the per-strip bitmap size (~16 × 4096 × 4 bytes ≈ 256 KB per strip).
        val stripHeight = 16
        val decodeOpts  = BitmapFactory.Options()   // default ARGB_8888

        context.resources.openRawResource(resId).use { stream ->
            val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return lut

            var stripBitmap: Bitmap? = null
            var stripTopY = -1

            for (srcY in sortedYs) {
                // Check if srcY falls within the currently loaded strip.
                if (stripBitmap == null || srcY < stripTopY || srcY >= stripTopY + stripHeight) {
                    stripBitmap?.recycle()
                    stripTopY = (srcY / stripHeight) * stripHeight
                    val rect = Rect(0, stripTopY, srcDim, minOf(stripTopY + stripHeight, srcDim))
                    stripBitmap = decoder.decodeRegion(rect, decodeOpts)
                }

                val strip = stripBitmap ?: continue
                val localY = srcY - stripTopY
                val rowPixels = IntArray(srcDim)
                strip.getPixels(rowPixels, 0, srcDim, 0, localY, srcDim, 1)

                for ((srcX, lutIdx) in yMap[srcY]!!) {
                    val pixel = rowPixels[srcX]
                    lut[lutIdx + 0] = ((pixel shr 16) and 0xFF) / 255f
                    lut[lutIdx + 1] = ((pixel shr 8)  and 0xFF) / 255f
                    lut[lutIdx + 2] = ( pixel          and 0xFF) / 255f
                }
            }
            stripBitmap?.recycle()
        }

        return lut
    }

    /**
     * Converts a HaldCLUT bitmap to a FloatArray in the same B-fastest ordering as .cube.
     * Supports any level L where image width == L³.
     *
     * HaldCLUT layout: pixels are stored in R-fastest order as a flat sequence.
     * For a given (ri, gi, bi), the pixel index is ri + gi*steps + bi*steps², which maps
     * directly to row-major position (y*dim + x) in the image.
     */
    private fun loadHaldClut(bitmap: Bitmap): FloatArray {
        val dim = bitmap.width            // L³ (e.g. 512 for level 8)
        val L = Math.cbrt(dim.toDouble()).toInt()
        val steps = L * L                 // color steps per channel (e.g. 64)

        val pixels = IntArray(dim * dim)
        bitmap.getPixels(pixels, 0, dim, 0, 0, dim, dim)

        val lut = FloatArray(steps * steps * steps * 3)

        for (bi in 0 until steps) {
            for (gi in 0 until steps) {
                for (ri in 0 until steps) {
                    // R-fastest flat index: directly maps to the pixel's row-major position
                    val pixel = pixels[ri + gi * steps + bi * steps * steps]
                    val lutIdx = (bi * steps * steps + gi * steps + ri) * 3
                    lut[lutIdx + 0] = ((pixel shr 16) and 0xFF) / 255f
                    lut[lutIdx + 1] = ((pixel shr 8) and 0xFF) / 255f
                    lut[lutIdx + 2] = (pixel and 0xFF) / 255f
                }
            }
        }
        return lut
    }

    private fun loadCubeLut(@RawRes resId: Int): FloatArray {
        val lines = context.resources.openRawResource(resId)
            .bufferedReader()
            .readLines()

        val values = mutableListOf<Float>()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith('#') || trimmed.isEmpty() -> continue
                trimmed.startsWith("LUT_SIZE") || trimmed.startsWith("TITLE") || trimmed.startsWith("DOMAIN") -> continue
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

        // Write modified pixels back onto the (already mutable) source bitmap.
        // This avoids allocating a second full-resolution copy alongside the pixels array.
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
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
