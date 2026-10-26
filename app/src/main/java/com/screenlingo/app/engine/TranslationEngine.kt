package com.screenlingo.app.engine

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class TranslatedBlock(val bounds: Rect, val source: String, val translated: String)

class TranslationEngine(source: String, target: String) : AutoCloseable {
    private val sameLanguage = source == target
    private val recognizer = TextRecognition.getClient(when (source) {
        "ja" -> JapaneseTextRecognizerOptions.Builder().build()
        "zh" -> ChineseTextRecognizerOptions.Builder().build()
        "ko" -> KoreanTextRecognizerOptions.Builder().build()
        "hi" -> DevanagariTextRecognizerOptions.Builder().build()
        else -> TextRecognizerOptions.DEFAULT_OPTIONS
    })
    private val translator = Translation.getClient(TranslatorOptions.Builder()
        .setSourceLanguage(source).setTargetLanguage(target).build())
    private val cache = TranslationCache()
    private var closed = false
    var lastCacheHits = 0
        private set
    var lastCacheMisses = 0
        private set

    fun prepare(wifiOnly: Boolean) = if (sameLanguage) Tasks.forResult(null) else
        translator.downloadModelIfNeeded(DownloadConditions.Builder().apply {
            if (wifiOnly) requireWifi()
        }.build())

    fun process(bitmap: Bitmap, callback: (Result<List<TranslatedBlock>>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                if (closed) { callback(Result.failure(IllegalStateException("Engine closed"))); return@addOnSuccessListener }
                val blocks = result.textBlocks.filter { it.text.isNotBlank() && it.boundingBox != null }
                lastCacheHits = 0; lastCacheMisses = 0
                // Bound work per frame; cached blocks do not issue new translation requests.
                val tasks = blocks.take(50).map { block ->
                    val text = block.text
                    val cached = cache.get(text)
                    if (cached != null) lastCacheHits++ else lastCacheMisses++
                    val task = when {
                        sameLanguage -> Tasks.forResult(text)
                        cached != null -> Tasks.forResult(cached)
                        else -> translator.translate(text).addOnSuccessListener { cache.put(text, it) }
                    }
                    task.continueWith { completed ->
                        TranslatedBlock(Rect(block.boundingBox!!), text, completed.getResult(Exception::class.java))
                    }
                }
                Tasks.whenAllSuccess<TranslatedBlock>(tasks)
                    .addOnSuccessListener { callback(Result.success(it)) }
                    .addOnFailureListener { callback(Result.failure(it)) }
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    override fun close() { closed = true; recognizer.close(); translator.close() }
}
