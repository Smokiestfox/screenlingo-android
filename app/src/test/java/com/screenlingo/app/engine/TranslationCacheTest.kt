package com.screenlingo.app.engine

import org.junit.Assert.*
import org.junit.Test

class TranslationCacheTest {
    @Test fun repeatedTextReusesTranslationAcrossSpacingNoise() {
        val cache = TranslationCache()
        cache.put("  Hello  world\t ", "Xin chào thế giới")
        assertEquals("Xin chào thế giới", cache.get("Hello world"))
        assertNull(cache.get("Hello world!"))
        assertNull(cache.get("hello world"))
    }

    @Test fun leastRecentlyUsedEntryIsEvicted() {
        val cache = TranslationCache(2)
        cache.put("one", "một"); cache.put("two", "hai")
        assertEquals("một", cache.get("one"))
        cache.put("three", "ba")
        assertNull(cache.get("two"))
        assertEquals("một", cache.get("one"))
        assertEquals("ba", cache.get("three"))
    }

    @Test fun lineBoundariesArePreserved() {
        val cache = TranslationCache()
        cache.put("First line\nSecond line", "Dòng đầu\nDòng sau")
        assertNull(cache.get("First line Second line"))
    }
}
