package com.photoncam.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Burns an orange date-time stamp into the bottom-right corner of a [Bitmap],
 * replicating the look of disposable cameras from the 90s.
 */
@Singleton
class DateImprintProcessor @Inject constructor() {

    private val dateFormat = SimpleDateFormat("MM ' DD ' yyyy", Locale.US)

    fun burn(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val textSize = (result.width * 0.032f).coerceIn(24f, 72f)

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
            typeface = Typeface.MONOSPACE
            alpha = 180
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF8C00") // amber orange
            this.textSize = textSize
            typeface = Typeface.MONOSPACE
        }

        val dateString = dateFormat.format(Date()).uppercase()
        val textWidth = textPaint.measureText(dateString)
        val x = result.width - textWidth - result.width * 0.02f
        val y = result.height - result.height * 0.03f

        // Shadow offset for legibility
        canvas.drawText(dateString, x + 2f, y + 2f, shadowPaint)
        canvas.drawText(dateString, x, y, textPaint)

        bitmap.recycle()
        return result
    }
}
