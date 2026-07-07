package com.jegly.filmcamera.processing

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DateImprintStyle {
    CLASSIC,      // DEC 24 '95
    NUMERIC_US,   // 12/24/95
    NUMERIC_EU,   // 24.12.95
    YEAR_FIRST,   // '95 DEC 24
    WITH_TIME,    // 24 DEC 14:30
    YY_MM_DD,     // '26 03 13
    YYYY_MM_DD;   // 2026 03 13

    /** Short preview label shown on the DATE button in the UI. */
    fun formatPreview(): String {
        val now = Date()
        return when (this) {
            CLASSIC     -> SimpleDateFormat("MMM dd ''yy",  Locale.US).format(now).uppercase()
            NUMERIC_US  -> SimpleDateFormat("MM/dd/yy",     Locale.US).format(now)
            NUMERIC_EU  -> SimpleDateFormat("dd.MM.yy",     Locale.US).format(now)
            YEAR_FIRST  -> SimpleDateFormat("''yy MMM dd",  Locale.US).format(now).uppercase()
            WITH_TIME   -> SimpleDateFormat("dd MMM HH:mm", Locale.US).format(now).uppercase()
            YY_MM_DD    -> SimpleDateFormat("''yy MM dd",   Locale.US).format(now)
            YYYY_MM_DD  -> SimpleDateFormat("yyyy MM dd",   Locale.US).format(now)
        }
    }

    fun next(): DateImprintStyle = entries[(ordinal + 1) % entries.size]
}
