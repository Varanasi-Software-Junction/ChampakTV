package com.learnwithchampak.tv

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

  private lateinit var statusText: TextView
  private lateinit var clockText: TextView
  private lateinit var root: LinearLayout
  private val tag = "ChampakTV"
  private val clockHandler = Handler(Looper.getMainLooper())
  private val clockFormat = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm:ss a", Locale.getDefault())

  private val clockRunnable = object : Runnable {
    override fun run() {
      updateClock()
      clockHandler.postDelayed(this, 1000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildScreen()
    clockHandler.post(clockRunnable)
  }

  override fun onDestroy() {
    clockHandler.removeCallbacks(clockRunnable)
    super.onDestroy()
  }

  private fun updateClock() {
    if (::clockText.isInitialized) {
      clockText.text = clockFormat.format(Date())
    }
  }

  private fun buildScreen() {
    val scroll = ScrollView(this).apply {
      isFocusable = false
      setBackgroundColor(Color.rgb(4, 15, 32))
    }

    root = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(44), dp(28), dp(44), dp(28))
      background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(3, 19, 46), Color.rgb(8, 74, 128), Color.rgb(2, 13, 28))
      )
    }

    val left = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
    }
    root.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f))

    val photo = ImageView(this).apply {
      adjustViewBounds = true
      scaleType = ImageView.ScaleType.CENTER_CROP
      background = rounded(Color.rgb(255, 255, 255), dp(26), Color.rgb(74, 198, 255), dp(3))
      setPadding(dp(6), dp(6), dp(6), dp(6))
      loadPhotoInto(this)
    }
    left.addView(photo, LinearLayout.LayoutParams(dp(310), dp(310)))

    left.addView(space(18))

    val name = text("Champak Roy", 30f, Color.WHITE, true)
    left.addView(name)

    val role = text("AI • ML • Python • DSA • Programming", 17f, Color.rgb(202, 232, 255), false)
    role.gravity = Gravity.CENTER
    left.addView(role)

    val right = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(34), 0, 0, 0)
    }
    root.addView(right, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f))

    clockText = text("", 19f, Color.rgb(255, 221, 128), true).apply {
      gravity = Gravity.RIGHT
      setPadding(0, 0, 0, dp(8))
    }
    right.addView(clockText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    val installBadge = text("Installed as: Learn With Champak TV", 16f, Color.rgb(255, 221, 128), true)
    installBadge.setPadding(0, 0, 0, dp(6))
    right.addView(installBadge)

    val title1 = text("Learn With", 46f, Color.WHITE, true)
    val title2 = text("Champak", 62f, Color.rgb(77, 207, 255), true)
    title2.setShadowLayer(10f, 0f, 4f, Color.rgb(0, 0, 0))
    right.addView(title1)
    right.addView(title2)

    val subtitle = text("Study AI, ML, Python, DSA and Programming with Champak Roy", 22f, Color.WHITE, true)
    subtitle.setPadding(0, dp(8), 0, dp(16))
    right.addView(subtitle)

    val tagLine = text("Learn. Build. Grow.", 24f, Color.rgb(255, 221, 128), true)
    right.addView(tagLine)

    right.addView(space(18))

    val linkPanel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(22), dp(18), dp(22), dp(18))
      background = rounded(Color.argb(235, 255, 255, 255), dp(24), Color.rgb(82, 204, 255), dp(2))
    }
    right.addView(linkPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    val linksHeading = text("Blogs & Learning Links", 25f, Color.rgb(3, 44, 84), true)
    linkPanel.addView(linksHeading)
    linkPanel.addView(space(10))

    val links = listOf(
      Pair("Learn With Champak", "https://www.learnwithchampak.live"),
      Pair("Inside Kashi", "https://insidekashi.com"),
      Pair("YouTube Channel", "https://youtube.com/@champaksworld")
    )

    for ((label, url) in links) {
      val b = linkButton(label, url)
      linkPanel.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))
      linkPanel.addView(space(10))
    }

    statusText = text("Use TV remote: Up/Down to focus, OK to open inside Champak TV browser.", 16f, Color.rgb(218, 240, 255), false)
    statusText.setPadding(0, dp(18), 0, 0)
    right.addView(statusText)

    scroll.addView(root)
    setContentView(scroll)
  }

  private fun linkButton(label: String, url: String): Button {
    return Button(this).apply {
      text = "$label\n$url"
      textSize = 15f
      isAllCaps = false
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(18), 0, dp(18), 0)
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      background = buttonBg(false)
      isFocusable = true
      isFocusableInTouchMode = true

      setOnFocusChangeListener { view, hasFocus ->
        background = buttonBg(hasFocus)
        view.animate().scaleX(if (hasFocus) 1.045f else 1f).scaleY(if (hasFocus) 1.045f else 1f).setDuration(120).start()
        if (hasFocus) {
          statusText.text = "Focused: $label"
          Log.d(this@MainActivity.tag, "Focused: $label")
        }
      }

      setOnClickListener {
        statusText.text = "Opening inside app: $label"
        openInsideApp(label, url)
      }
    }
  }

  private fun openInsideApp(title: String, url: String) {
    try {
      val intent = Intent(this, BrowserActivity::class.java).apply {
        putExtra(BrowserActivity.EXTRA_TITLE, title)
        putExtra(BrowserActivity.EXTRA_URL, url)
      }
      startActivity(intent)
    } catch (ex: Exception) {
      Toast.makeText(this, "Could not open browser screen", Toast.LENGTH_LONG).show()
      Log.e(tag, "Unable to open internal browser: $url", ex)
    }
  }

  private fun loadPhotoInto(imageView: ImageView) {
    try {
      val input = assets.open("champak-photo.png")
      val bitmap = BitmapFactory.decodeStream(input)
      if (bitmap != null) {
        imageView.setImageBitmap(bitmap)
        Log.d(tag, "Loaded bundled PNG photo")
        return
      }
    } catch (ex: Exception) {
      Log.d(tag, "Bundled PNG photo not found, trying Base64 asset")
    }

    try {
      val input = assets.open("champak_photo.b64")
      val encoded = BufferedReader(InputStreamReader(input)).readText().replace("\n", "").trim()
      if (encoded.isEmpty()) {
        throw IllegalStateException("champak_photo.b64 is empty")
      }
      val bytes = Base64.decode(encoded, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      if (bitmap == null) {
        throw IllegalStateException("Base64 photo could not be decoded")
      }
      imageView.setImageBitmap(bitmap)
      Log.d(tag, "Loaded bundled Base64 photo")
      return
    } catch (ex: Exception) {
      Log.d(tag, "Bundled Base64 photo not usable, trying hosted photo")
    }

    imageView.setBackgroundColor(Color.rgb(12, 84, 130))
    imageView.contentDescription = "Champak Roy photo"

    Thread {
      try {
        val imageUrl = URL("https://programmer-s-picnic.github.io/json-images/tv/champak-photo.png")
        val bitmap = BitmapFactory.decodeStream(imageUrl.openStream())
        if (bitmap != null) {
          runOnUiThread {
            imageView.setImageBitmap(bitmap)
            Log.d(tag, "Loaded hosted PNG photo")
          }
        }
      } catch (ex: Exception) {
        Log.e(tag, "Could not load hosted photo", ex)
      }
    }.start()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      val keyName = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "select"
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> "back"
        else -> "keyCode ${event.keyCode}"
      }
      Log.d(tag, "Remote key: $keyName")
      if (::statusText.isInitialized && keyName != "back") {
        statusText.text = "Remote key: $keyName"
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView {
    return TextView(this).apply {
      text = value
      textSize = size
      setTextColor(color)
      includeFontPadding = true
      if (bold) typeface = Typeface.DEFAULT_BOLD
    }
  }

  private fun space(h: Int): Space {
    return Space(this).apply {
      layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }
  }

  private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable {
    return GradientDrawable().apply {
      setColor(color)
      cornerRadius = radius.toFloat()
      if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }
  }

  private fun buttonBg(focused: Boolean): GradientDrawable {
    val colors = if (focused) {
      intArrayOf(Color.rgb(255, 168, 37), Color.rgb(255, 111, 0))
    } else {
      intArrayOf(Color.rgb(9, 74, 132), Color.rgb(5, 43, 92))
    }
    return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
      cornerRadius = dp(18).toFloat()
      setStroke(dp(if (focused) 4 else 2), if (focused) Color.WHITE else Color.rgb(82, 204, 255))
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
