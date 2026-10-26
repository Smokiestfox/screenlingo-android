package com.screenlingo.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

object Palette {
    val background = Color.rgb(16, 25, 29)
    val panel = Color.rgb(25, 38, 43)
    val border = Color.rgb(44, 62, 65)
    val mint = Color.rgb(113, 232, 190)
    val white = Color.rgb(237, 244, 241)
    val muted = Color.rgb(151, 171, 171)
}

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
fun Context.shape(color: Int, radius: Int = 20, stroke: Int? = null) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(radius).toFloat()
    if (stroke != null) setStroke(dp(1), stroke)
}
fun Context.label(value: String, size: Float = 15f, color: Int = Palette.white, bold: Boolean = false) = TextView(this).apply {
    text = value; textSize = size; setTextColor(color)
    if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    includeFontPadding = false
}
fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
fun LinearLayout.space(height: Int) { addView(View(context), LinearLayout.LayoutParams(1, context.dp(height))) }
fun LinearLayout.item(view: View, height: Int = -2, top: Int = 0) {
    addView(view, LinearLayout.LayoutParams(-1, if (height < 0) height else context.dp(height)).apply { topMargin = context.dp(top) })
}
fun Context.action(value: String, primary: Boolean = false, onClick: () -> Unit) = label(
    value, 16f, if (primary) Palette.background else Palette.white, true
).apply {
    gravity = Gravity.CENTER
    background = shape(if (primary) Palette.mint else Palette.panel, 16, if (primary) null else Palette.border)
    setPadding(dp(14), dp(14), dp(14), dp(14))
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
}
