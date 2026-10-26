package com.screenlingo.app.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.screenlingo.app.engine.TranslatedBlock
import com.screenlingo.app.ui.*
import kotlin.math.abs

class OverlayController(
    private val context: Context,
    private val togglePause: () -> Unit,
    private val stop: () -> Unit,
    private val regionChanged: (RectF?) -> Unit,
    private val selectingChanged: (Boolean) -> Unit
) : AutoCloseable {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val translation = TranslationOverlay(context)
    private val translationParams = params(-1, -1, true)
    private val bubble: TextView = context.label("SL", 16f, Palette.background, true).apply {
        gravity = Gravity.CENTER; background = context.shape(Palette.mint, 30)
        elevation = context.dp(6).toFloat(); contentDescription = "Điều khiển dịch màn hình. Kéo để di chuyển."
    }
    private val bubbleParams = params(context.dp(50), context.dp(50), false).apply {
        x = displaySize().first - context.dp(66); y = context.dp(180)
    }
    private var menu: View? = null
    private var selector: RegionSelector? = null
    var isSelecting = false
        private set
    private var paused = false
    private var closed = false

    init {
        wm.addView(translation, translationParams)
        wm.addView(bubble, bubbleParams)
        installDrag()
    }

    private fun params(width: Int, height: Int, passThrough: Boolean) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (passThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0), PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        // Android 12+ allows touches through a non-touchable application overlay only below
        // the platform obscuring-opacity limit. Keep the entire window below that limit.
        if (passThrough) alpha = 0.79f
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun displaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= 30) return wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun installDrag() {
        var downX = 0f; var downY = 0f; var originX = 0; var originY = 0; var dragging = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        bubble.setOnClickListener { toggleMenu() }
        bubble.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; originX = bubbleParams.x; originY = bubbleParams.y; dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - downX) > slop || abs(event.rawY - downY) > slop) dragging = true
                    if (dragging) {
                        dismissMenu()
                        val (w, h) = displaySize()
                        bubbleParams.x = (originX + event.rawX - downX).toInt().coerceIn(0, (w - context.dp(50)).coerceAtLeast(0))
                        bubbleParams.y = (originY + event.rawY - downY).toInt().coerceIn(context.dp(28), (h - context.dp(80)).coerceAtLeast(context.dp(28)))
                        wm.updateViewLayout(bubble, bubbleParams)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) bubble.performClick()
            }
            true
        }
    }

    private fun toggleMenu() {
        if (menu != null) { dismissMenu(); return }
        val panel = context.column().apply {
            background = context.shape(Palette.panel, 18, Palette.border)
            setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
            item(context.action(if (paused) "▶  Tiếp tục" else "Ⅱ  Tạm dừng") { dismissMenu(); togglePause() })
            item(context.action("▣  Chọn vùng dịch") { dismissMenu(); selectRegion() }, top = 6)
            item(context.action("↗  Toàn màn hình") { dismissMenu(); regionChanged(null) }, top = 6)
            item(context.action("■  Dừng phiên dịch") { dismissMenu(); stop() }, top = 6)
            item(context.action("Đóng bảng điều khiển") { dismissMenu() }, top = 6)
        }
        menu = panel
        val (w, h) = displaySize()
        wm.addView(panel, params(context.dp(226), -2, false).apply {
            x = (bubbleParams.x - context.dp(180)).coerceIn(0, (w - context.dp(226)).coerceAtLeast(0))
            y = bubbleParams.y.coerceAtMost((h - context.dp(360)).coerceAtLeast(context.dp(30)))
        })
    }

    fun dismissMenu() { menu?.let { wm.removeView(it) }; menu = null }

    fun selectRegion() {
        if (closed || isSelecting) return
        dismissMenu(); isSelecting = true; selectingChanged(true)
        hideForCapture(); bubble.visibility = View.INVISIBLE
        selector = RegionSelector(context) { rect ->
            selector?.let { wm.removeView(it) }; selector = null
            isSelecting = false; bubble.visibility = View.VISIBLE; restore()
            if (rect != null) regionChanged(rect)
            selectingChanged(false)
        }.also { wm.addView(it, params(-1, -1, false)) }
    }

    fun setPaused(value: Boolean) { paused = value; bubble.text = if (value) "Ⅱ" else "SL" }
    fun hideForCapture() {
        if (!closed && translationParams.alpha != 0f) {
            // Change the actual window surface alpha. Root View.INVISIBLE alone can leave
            // the last submitted buffer in the captured display on some Android versions.
            translationParams.alpha = 0f; wm.updateViewLayout(translation, translationParams)
        }
    }
    fun restore() {
        if (!isSelecting && !closed && translationParams.alpha != 0.79f) {
            translationParams.alpha = 0.79f; wm.updateViewLayout(translation, translationParams)
        }
    }
    fun show(blocks: List<TranslatedBlock>, region: RectF?) {
        translation.blocks = blocks; translation.region = region; restore()
    }
    fun clear() { translation.blocks = emptyList(); translation.region = null }
    fun excludedBounds(): Rect {
        val location = IntArray(2); bubble.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + bubble.width, location[1] + bubble.height)
    }
    fun hasOpenMenu() = menu != null
    fun hasTranslations() = translation.blocks.isNotEmpty()
    fun onDisplayChanged() {
        val (w, h) = displaySize()
        bubbleParams.x = bubbleParams.x.coerceIn(0, (w - context.dp(50)).coerceAtLeast(0))
        bubbleParams.y = bubbleParams.y.coerceIn(context.dp(28), (h - context.dp(80)).coerceAtLeast(context.dp(28)))
        wm.updateViewLayout(bubble, bubbleParams); dismissMenu(); clear()
    }

    override fun close() {
        closed = true; dismissMenu()
        selector?.let { wm.removeView(it) }; selector = null
        wm.removeView(bubble); wm.removeView(translation)
    }
}
