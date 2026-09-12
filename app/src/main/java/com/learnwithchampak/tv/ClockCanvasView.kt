package com.learnwithchampak.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ClockCanvasView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  private val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(230, 255, 255, 255)
    style = Paint.Style.FILL
  }

  private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(77, 207, 255)
    style = Paint.Style.STROKE
    strokeWidth = dp(4).toFloat()
  }

  private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(5, 43, 92)
    strokeWidth = dp(2).toFloat()
    strokeCap = Paint.Cap.ROUND
  }

  private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(3, 44, 84)
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    textSize = dp(14).toFloat()
  }

  private val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(3, 44, 84)
    strokeWidth = dp(6).toFloat()
    strokeCap = Paint.Cap.ROUND
  }

  private val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(8, 74, 128)
    strokeWidth = dp(4).toFloat()
    strokeCap = Paint.Cap.ROUND
  }

  private val secondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(255, 111, 0)
    strokeWidth = dp(2).toFloat()
    strokeCap = Paint.Cap.ROUND
  }

  private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(255, 168, 37)
    style = Paint.Style.FILL
  }

  private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(255, 255, 255)
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.DEFAULT_BOLD
    textSize = dp(18).toFloat()
  }

  private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.rgb(255, 221, 128)
    textAlign = Paint.Align.CENTER
    textSize = dp(12).toFloat()
  }

  private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.argb(90, 0, 0, 0)
    style = Paint.Style.FILL
  }

  private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
  private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
  private val textBounds = Rect()

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)

    val w = width.toFloat()
    val h = height.toFloat()
    val clockSize = minOf(w * 0.40f, h * 0.84f)
    val radius = clockSize / 2f
    val cx = radius + dp(14)
    val cy = h / 2f

    val panel = RectF(0f, 0f, w, h)
    canvas.drawRoundRect(panel, dp(22).toFloat(), dp(22).toFloat(), panelPaint)

    canvas.drawCircle(cx, cy, radius, facePaint)
    canvas.drawCircle(cx, cy, radius - dp(2), ringPaint)

    for (i in 0 until 60) {
      val angle = Math.toRadians((i * 6 - 90).toDouble())
      val inner = if (i % 5 == 0) radius - dp(18) else radius - dp(10)
      val outer = radius - dp(5)
      val x1 = cx + kotlin.math.cos(angle).toFloat() * inner
      val y1 = cy + kotlin.math.sin(angle).toFloat() * inner
      val x2 = cx + kotlin.math.cos(angle).toFloat() * outer
      val y2 = cy + kotlin.math.sin(angle).toFloat() * outer
      tickPaint.strokeWidth = if (i % 5 == 0) dp(3).toFloat() else dp(1).toFloat()
      canvas.drawLine(x1, y1, x2, y2, tickPaint)
    }

    for (n in 1..12) {
      val angle = Math.toRadians((n * 30 - 90).toDouble())
      val tx = cx + kotlin.math.cos(angle).toFloat() * (radius - dp(34))
      val ty = cy + kotlin.math.sin(angle).toFloat() * (radius - dp(34))
      val value = n.toString()
      numberPaint.getTextBounds(value, 0, value.length, textBounds)
      canvas.drawText(value, tx, ty - textBounds.exactCenterY(), numberPaint)
    }

    val now = Calendar.getInstance()
    val hours = now.get(Calendar.HOUR).toFloat()
    val minutes = now.get(Calendar.MINUTE).toFloat()
    val seconds = now.get(Calendar.SECOND).toFloat()

    val hourAngle = Math.toRadians(((hours + minutes / 60f) * 30f - 90f).toDouble())
    val minuteAngle = Math.toRadians(((minutes + seconds / 60f) * 6f - 90f).toDouble())
    val secondAngle = Math.toRadians((seconds * 6f - 90f).toDouble())

    drawHand(canvas, cx, cy, hourAngle, radius * 0.46f, hourPaint)
    drawHand(canvas, cx, cy, minuteAngle, radius * 0.66f, minutePaint)
    drawHand(canvas, cx, cy, secondAngle, radius * 0.78f, secondPaint)
    canvas.drawCircle(cx, cy, dp(7).toFloat(), centerPaint)

    val nowDate = now.time
    val textX = cx + radius + ((w - (cx + radius)) / 2f)
    val titleY = cy - dp(35)
    canvas.drawText("Learn With Champak", textX, titleY, textPaint)
    canvas.drawText(timeFormat.format(nowDate), textX, titleY + dp(34), textPaint)
    canvas.drawText(dateFormat.format(nowDate), textX, titleY + dp(62), smallTextPaint)
  }

  private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angle: Double, length: Float, paint: Paint) {
    val x = cx + kotlin.math.cos(angle).toFloat() * length
    val y = cy + kotlin.math.sin(angle).toFloat() * length
    canvas.drawLine(cx, cy, x, y, paint)
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
