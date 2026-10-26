package com.screenlingo.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.screenlingo.app.engine.TranslationEngine
import com.screenlingo.app.engine.TranslatedBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Requires network on the first run to download the Vietnamese translation model. */
@RunWith(AndroidJUnit4::class)
class TranslationInstrumentedTest {
    @Test fun englishImageIsRecognizedAndTranslatedIntoVietnamese() {
        val bitmap = Bitmap.createBitmap(800, 240, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("Hello world", 50f, 140f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 64f })
        }
        val engine = TranslationEngine("en", "vi")
        try {
            Tasks.await(engine.prepare(false), 240, TimeUnit.SECONDS)
            var result: Result<List<TranslatedBlock>>? = null
            val completed = CountDownLatch(1)
            engine.process(bitmap) { result = it; completed.countDown() }
            assertTrue("OCR / translation timed out", completed.await(60, TimeUnit.SECONDS))
            val blocks = result!!.getOrThrow()
            assertTrue(blocks.isNotEmpty())
            assertTrue(blocks.first().source.contains("Hello world"))
            assertTrue(blocks.first().translated.isNotBlank())
            assertNotEquals(blocks.first().source, blocks.first().translated)
        } finally { engine.close(); bitmap.recycle() }
    }
}
