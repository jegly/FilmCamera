package com.photoncam.processing

import android.graphics.Bitmap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class GrainProcessor @Inject constructor() {

    fun applyGrain(bitmap: Bitmap, amount: Float, size: Float): Bitmap {
        if (amount <= 0f) return bitmap

        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val seed = System.nanoTime()
        val rng = Random(seed)

        val noiseScale = (1f / size).coerceIn(0.1f, 1f)
        val noiseW = (width * noiseScale).toInt().coerceAtLeast(1)
        val noiseH = (height * noiseScale).toInt().coerceAtLeast(1)
        val noise = IntArray(noiseW * noiseH) { rng.nextInt(256) }

        val maxDelta = (amount * 72f).toInt().coerceIn(1, 72)

        for (i in pixels.indices) {
            val x = i % width
            val y = i / width
            val nx = (x * noiseScale).toInt().coerceIn(0, noiseW - 1)
            val ny = (y * noiseScale).toInt().coerceIn(0, noiseH - 1)
            val n = noise[ny * noiseW + nx]
            val delta = ((n / 255f) * 2f - 1f) * maxDelta

            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF)
            val g = ((pixel shr 8) and 0xFF)
            val b = (pixel and 0xFF)

            pixels[i] = (pixel and 0xFF000000.toInt()) or
                ((r + delta.toInt()).coerceIn(0, 255) shl 16) or
                ((g + delta.toInt()).coerceIn(0, 255) shl 8) or
                (b + delta.toInt()).coerceIn(0, 255)
        }

        // Write modified pixels back onto the (already mutable) source bitmap in-place.
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }
}
