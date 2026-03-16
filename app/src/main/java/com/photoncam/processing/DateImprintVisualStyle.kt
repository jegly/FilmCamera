package com.photoncam.processing

/** Color of the burned-in date stamp. */
enum class DateImprintColor(val label: String, val hex: String) {
    AMBER("AMBER", "#FF6600"),  // tuned to match real disposable-camera LED stamps
    RED("RED",     "#DD2222"),
    WHITE("WHITE", "#F0F0F0"),
    YELLOW("GOLD", "#FFE600"),
    GREEN("GREEN", "#00CC44"),
    CYAN("CYAN",   "#00CCEE"),
}

/** Typeface of the burned-in date stamp. */
enum class DateImprintFont(val label: String) {
    LED("LED"),           // 7-segment display style (loads dseg7.ttf from assets if present)
    MONOSPACE("MONO"),
    BOLD("BOLD"),
    SERIF("SERIF"),
    CONDENSED("COND"),
}

/** Size of the burned-in date stamp relative to the image width. */
enum class DateImprintSize(val label: String) {
    SMALL("SMALL"),
    MEDIUM("MED"),
    LARGE("LARGE"),
}

/** Corner/edge position of the burned-in date stamp. */
enum class DateImprintPosition(val label: String) {
    BOTTOM_RIGHT("BOT-R"),
    BOTTOM_LEFT("BOT-L"),
    BOTTOM_CENTER("BOT-C"),
    TOP_RIGHT("TOP-R"),
    TOP_LEFT("TOP-L"),
}
