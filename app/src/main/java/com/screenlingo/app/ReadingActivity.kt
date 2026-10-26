package com.screenlingo.app

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import com.screenlingo.app.ui.*

/** Real text for testing OCR and scroll updates; no hardcoded translation results. */
class ReadingActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = column().apply {
            setBackgroundColor(Palette.background); setPadding(dp(28), dp(52), dp(28), dp(50))
            item(action("←  Back to ScreenLingo") { finish() })
            item(label("THE READING ROOM", 12f, Palette.mint, true), top = 36)
            item(label("A little curiosity\ngoes a long way.", 32f, bold = true), top = 18)
            item(label("This is a sample page for live translation. Start a session in ScreenLingo, return here, then scroll to discover more stories.", 13f, Palette.muted), top = 20)
            listOf(
                "Discover something new" to "Every day is a chance to learn something new. Open a book, explore a different culture, or start a conversation with someone you have never met.",
                "Small steps matter" to "You do not need to see the whole staircase. Just take the first step. Progress happens when we keep moving, even when the path is not clear.",
                "Make room for the unexpected" to "The best journeys do not always follow a plan. Sometimes a wrong turn leads to a beautiful place you would never have found otherwise.",
                "Stay connected" to "Language helps us share ideas and understand each other. A simple hello can be the beginning of a wonderful friendship.",
                "A quiet moment" to "Take a deep breath. Look around you. There is always something worth noticing in the world, if you give yourself a moment to see it."
            ).forEach { (title, body) ->
                item(label(title, 22f, bold = true), top = 38)
                item(label(body, 18f).apply { setLineSpacing(dp(8).toFloat(), 1f) }, top = 18)
            }
        }
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
