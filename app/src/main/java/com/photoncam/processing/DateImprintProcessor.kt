package com.photoncam.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Burns a date stamp into a [Bitmap] with fully configurable appearance.
 * Supports an LED/7-segment style with optional bloom glow (BlurMaskFilter).
 */
@Singleton
class DateImprintProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * LED typeface loaded lazily from assets/fonts/dseg7.ttf.
     * Falls back to Typeface.MONOSPACE if the file is not present.
     * Place DSEG7Classic-Regular.ttf (free, MIT) at that path for a real segment-display look.
     */
    private val ledTypeface: Typeface by lazy {
        runCatching {
            Typeface.createFromAsset(context.assets, "fonts/dseg7.ttf")
        }.getOrDefault(Typeface.MONOSPACE)
    }

    fun burn(
        bitmap: Bitmap,
        style: DateImprintStyle,
        color: DateImprintColor = DateImprintColor.AMBER,
        font: DateImprintFont = DateImprintFont.LED,
        size: DateImprintSize = DateImprintSize.MEDIUM,
        position: DateImprintPosition = DateImprintPosition.BOTTOM_RIGHT,
        /** 0–100: radius of the bloom halo drawn behind the text. */
        glowAmount: Int = 100,
        /** 0–100: soft-focus blur applied to the sharp text layer itself. */
        blurAmount: Int = 50,
        opacity: Int = 50,
        /** 0–20: how many times the blur text layer is drawn to intensify without widening. */
        blurRepeat: Int = 3,
    ): Bitmap {
        // Draw directly onto the mutable source bitmap — no copy needed.
        val result = bitmap
        val canvas = Canvas(result)

        val w = result.width.toFloat()
        val h = result.height.toFloat()

        val dateString = formatDate(style)

        // ── Size ───────────────────────────────────────────────────────────────
        val textSize = when (size) {
            DateImprintSize.SMALL  -> (w * 0.020f).coerceIn(14f, 44f)
            DateImprintSize.MEDIUM -> (w * 0.030f).coerceIn(20f, 64f)
            DateImprintSize.LARGE  -> (w * 0.044f).coerceIn(28f, 92f)
        }

        // ── Typeface ───────────────────────────────────────────────────────────
        val typeface = when (font) {
            DateImprintFont.LED       -> ledTypeface
            DateImprintFont.MONOSPACE -> Typeface.MONOSPACE
            DateImprintFont.BOLD      -> Typeface.DEFAULT_BOLD
            DateImprintFont.SERIF     -> Typeface.SERIF
            DateImprintFont.CONDENSED -> Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        // ── Color ──────────────────────────────────────────────────────────────
        val textColor = Color.parseColor(color.hex)

        // ── Opacity ────────────────────────────────────────────────────────────
        val opacityFactor = opacity.coerceIn(0, 100) / 100f

        // ── Glow radius (bloom halo) ───────────────────────────────────────────
        val glowRadius = textSize * (glowAmount.coerceIn(0, 100) / 100f) * 0.375f

        // ── Text blur radius (soft-focus diffusion) ────────────────────────────
        val textBlurRadius = textSize * (blurAmount.coerceIn(0, 100) / 100f) * 0.2f

        // ── Paints ─────────────────────────────────────────────────────────────

        // Drop shadow
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.BLACK
            this.textSize = textSize
            this.typeface = typeface
            alpha = (160 * opacityFactor).toInt()
        }

        // Glow layer — blurred halo drawn before the sharp text
        val glowPaint: Paint? = if (glowRadius > 0f) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = textColor
                this.textSize = textSize
                this.typeface = typeface
                alpha = (255 * opacityFactor).toInt()
                maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.SOLID)
            }
        } else null

        // Sharp foreground text (with optional soft-focus blur)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = textColor
            this.textSize = textSize
            this.typeface = typeface
            alpha = (255 * opacityFactor).toInt()
            if (textBlurRadius > 0f) {
                maskFilter = BlurMaskFilter(textBlurRadius, BlurMaskFilter.Blur.NORMAL)
            }
        }

        // LED "inactive segments" ghost — very faint same-color layer underneath
        val ghostPaint: Paint? = if (font == DateImprintFont.LED) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = textColor
                this.textSize = textSize
                this.typeface = typeface
                alpha = (30 * opacityFactor).toInt()
            }
        } else null

        val textWidth = textPaint.measureText(dateString)
        val margin = w * 0.10f   // ~10% from edge — sits noticeably inward from the border

        // ── Position ───────────────────────────────────────────────────────────
        val x = when (position) {
            DateImprintPosition.BOTTOM_RIGHT, DateImprintPosition.TOP_RIGHT ->
                w - textWidth - margin
            DateImprintPosition.BOTTOM_LEFT, DateImprintPosition.TOP_LEFT ->
                margin
            DateImprintPosition.BOTTOM_CENTER ->
                (w - textWidth) / 2f
        }
        val y = when (position) {
            DateImprintPosition.BOTTOM_RIGHT, DateImprintPosition.BOTTOM_LEFT,
            DateImprintPosition.BOTTOM_CENTER ->
                h - h * 0.075f   // ~7.5% from bottom — slightly more inward
            DateImprintPosition.TOP_RIGHT, DateImprintPosition.TOP_LEFT ->
                h * 0.085f + textSize
        }

        // ── Draw layers ────────────────────────────────────────────────────────
        // 1. LED inactive-segment ghost (if LED font)
        ghostPaint?.let { canvas.drawText(dateString, x, y, it) }
        // 2. Drop shadow
        canvas.drawText(dateString, x + 2f, y + 2f, shadowPaint)
        // 3. Glow halo (if blur enabled)
        glowPaint?.let { canvas.drawText(dateString, x, y, it) }
        // 4. Sharp/blur text — drawn blurRepeat times to intensify without widening the blur area
        repeat(blurRepeat.coerceIn(1, 20)) { canvas.drawText(dateString, x, y, textPaint) }

        return result
    }

    private fun formatDate(style: DateImprintStyle): String {
        val now = Date()
        return when (style) {
            DateImprintStyle.CLASSIC    -> SimpleDateFormat("MMM dd ''yy",  Locale.US).format(now).uppercase()
            DateImprintStyle.NUMERIC_US -> SimpleDateFormat("MM/dd/yy",     Locale.US).format(now)
            DateImprintStyle.NUMERIC_EU -> SimpleDateFormat("dd.MM.yy",     Locale.US).format(now)
            DateImprintStyle.YEAR_FIRST -> SimpleDateFormat("''yy MMM dd",  Locale.US).format(now).uppercase()
            DateImprintStyle.WITH_TIME  -> SimpleDateFormat("dd MMM HH:mm", Locale.US).format(now).uppercase()
            DateImprintStyle.YY_MM_DD   -> SimpleDateFormat("''yy MM dd",   Locale.US).format(now)
            DateImprintStyle.YYYY_MM_DD -> SimpleDateFormat("yyyy MM dd",   Locale.US).format(now)
        }
    }
}
