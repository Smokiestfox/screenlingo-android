package com.screenlingo.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.screenlingo.app.ui.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RegionSelector(context: Context, private val selected: (RectF?) -> Unit) : View(context) {
    private var startX = 0f
    private var startY = 0f
    private var box: RectF? = null
    private val dim = Paint().apply { color = 0xAA08140F.toInt() }
    private val clear = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF71E8BE.toInt(); style = Paint.Style.STROKE; strokeWidth = context.dp(2).toFloat() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = context.dp(16).toFloat(); textAlign = Paint.Align.CENTER }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); contentDescription = "Kéo để chọn vùng dịch. Chạm để huỷ." }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dim)
        box?.let { canvas.drawRect(it, clear); canvas.drawRect(it, stroke) }
        canvas.drawText("Kéo để chọn vùng cần dịch", width / 2f, context.dp(100).toFloat(), text)
        text.textSize = context.dp(12).toFloat()
        canvas.drawText("Thả tay để xác nhận · Chạm để huỷ", width / 2f, context.dp(126).toFloat(), text)
        text.textSize = context.dp(16).toFloat()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = event.x; startY = event.y; box = null }
            MotionEvent.ACTION_MOVE -> box = RectF(min(startX, event.x), min(startY, event.y), max(startX, event.x), max(startY, event.y))
            MotionEvent.ACTION_UP -> {
                val chosen = if (abs(event.x - startX) >= context.dp(32) && abs(event.y - startY) >= context.dp(32)) {
                    val location = IntArray(2); getLocationOnScreen(location)
                    RectF(min(startX, event.x) + location[0], min(startY, event.y) + location[1], max(startX, event.x) + location[0], max(startY, event.y) + location[1])
                } else null
                performClick(); selected(chosen)
            }
            MotionEvent.ACTION_CANCEL -> selected(null)
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
