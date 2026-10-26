package com.screenlingo.app.engine

/** Exact-text LRU: punctuation and case can change translation meaning. */
class TranslationCache(private val capacity: Int = 256) {
    private val values = object : LinkedHashMap<String, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > capacity
    }
    fun get(text: String): String? = values[normalize(text)]
    fun put(text: String, translated: String) { values[normalize(text)] = translated }
    private fun normalize(text: String) = text.trim().replace(Regex("[ \\t]+"), " ")
}
