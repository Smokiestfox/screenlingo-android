package com.screenlingo.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import com.screenlingo.app.engine.TranslatedBlock
import com.screenlingo.app.ui.dp

/** A non-touchable canvas. All coordinates passed in are physical display pixels. */
class TranslationOverlay(context: Context) : View(context) {
    var blocks: List<TranslatedBlock> = emptyList()
        set(value) { field = value; prepareLayouts(); invalidate() }
    var region: RectF? = null
        set(value) { field = value; invalidate() }
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(6, 12, 10) }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(113, 232, 190); style = Paint.Style.STROKE; strokeWidth = context.dp(1).toFloat() }
    private val screenLocation = IntArray(2)
    private data class RenderBlock(val box: RectF, val text: StaticLayout)
    private var rendered = emptyList<RenderBlock>()
    private val padding = context.dp(3).toFloat()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh); prepareLayouts()
    }

    private fun prepareLayouts() {
        rendered = blocks.mapNotNull { block ->
            val box = RectF(block.bounds)
            if (box.width() < 8 || box.height() < 8) return@mapNotNull null
            box.inset(-padding, -padding)
            box.left = box.left.coerceAtLeast(0f)
            box.right = box.right.coerceAtMost((if (width > 0) width else resources.displayMetrics.widthPixels).toFloat())
            val availableWidth = (box.width() - padding * 2).toInt().coerceAtLeast(1)
            val availableHeight = (box.height() - padding * 2).toInt().coerceAtLeast(1)
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 255, 245) }
            var size = (block.bounds.height() / block.source.lines().size.coerceAtLeast(1) * 0.80f)
                .coerceIn(context.dp(10).toFloat(), context.dp(19).toFloat())
            var layout: StaticLayout
            do {
                paint.textSize = size
                layout = StaticLayout.Builder.obtain(block.translated, 0, block.translated.length, paint, availableWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                if (layout.height <= availableHeight || size <= context.dp(8)) break
                size -= 1f
            } while (true)
            RenderBlock(box, layout)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(screenLocation)
        canvas.save()
        canvas.translate(-screenLocation[0].toFloat(), -screenLocation[1].toFloat())
        region?.let { canvas.drawRoundRect(it, 8f, 8f, outline) }
        rendered.forEach { block ->
            val box = block.box
            canvas.drawRoundRect(box, context.dp(4).toFloat(), context.dp(4).toFloat(), background)
            canvas.save()
            canvas.clipRect(box)
            canvas.translate(box.left + padding, box.top + padding)
            block.text.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }
}
