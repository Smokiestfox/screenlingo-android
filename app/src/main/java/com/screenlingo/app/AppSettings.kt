package com.screenlingo.app

import android.content.Context

data class Language(val code: String, val name: String, val nativeName: String)

object Languages {
    // Source list is intentionally limited to scripts supported by the bundled OCR engines.
    val sources = listOf(
        Language("en", "Tiếng Anh", "English"), Language("ja", "Tiếng Nhật", "日本語"),
        Language("zh", "Tiếng Trung", "中文"), Language("ko", "Tiếng Hàn", "한국어"),
        Language("vi", "Tiếng Việt", "Tiếng Việt"), Language("fr", "Tiếng Pháp", "Français"),
        Language("de", "Tiếng Đức", "Deutsch"), Language("es", "Tiếng Tây Ban Nha", "Español"),
        Language("pt", "Tiếng Bồ Đào Nha", "Português"), Language("it", "Tiếng Ý", "Italiano"),
        Language("id", "Tiếng Indonesia", "Bahasa Indonesia"), Language("hi", "Tiếng Hindi", "हिन्दी")
    )
    val targets = sources + listOf(
        Language("th", "Tiếng Thái", "ไทย"), Language("ru", "Tiếng Nga", "Русский"),
        Language("ar", "Tiếng Ả Rập", "العربية"), Language("tr", "Tiếng Thổ Nhĩ Kỳ", "Türkçe")
    )
    fun find(code: String) = targets.firstOrNull { it.code == code } ?: sources.first()
}

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("screenlingo", Context.MODE_PRIVATE)
    var source: String
        get() = prefs.getString("source", "en")!!
        set(value) { prefs.edit().putString("source", value).apply() }
    var target: String
        get() = prefs.getString("target", "vi")!!
        set(value) { prefs.edit().putString("target", value).apply() }
    var intervalMs: Long
        get() = prefs.getLong("interval", 900)
        set(value) { prefs.edit().putLong("interval", value.coerceIn(500, 3000)).apply() }
    var regionMode: Boolean
        get() = prefs.getBoolean("region", false)
        set(value) { prefs.edit().putBoolean("region", value).apply() }
    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifiOnly", true)
        set(value) { prefs.edit().putBoolean("wifiOnly", value).apply() }
}
