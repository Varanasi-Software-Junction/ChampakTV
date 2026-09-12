package com.learnwithchampak.tv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

class MainActivity : AppCompatActivity() {

  private data class HomeLink(val label: String, val url: String, val external: Boolean)

  private lateinit var statusText: TextView
  private lateinit var versionText: TextView
  private lateinit var clockCanvas: ClockCanvasView
  private lateinit var stage: FrameLayout
  private lateinit var scrollView: ScrollView
  private lateinit var pointer: TextView
  private val linkButtons = mutableListOf<Button>()
  private val tag = "ChampakTV"
  private val clockHandler = Handler(Looper.getMainLooper())
  private var pointerX = 0f
  private var pointerY = 0f

  private val apkUrl = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv.apk"
  private val versionUrl = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv-version.json"

  private val clockRunnable = object : Runnable {
    override fun run() {
      if (::clockCanvas.isInitialized) clockCanvas.invalidate()
      clockHandler.postDelayed(this, 1000)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (resources.configuration.screenWidthDp >= 700) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
    buildScreen()
    clockHandler.post(clockRunnable)
    checkLatestVersion()
  }

  override fun onDestroy() {
    clockHandler.removeCallbacks(clockRunnable)
    super.onDestroy()
  }

  private fun buildScreen() {
    val phoneMode = resources.configuration.screenWidthDp < 700

    stage = FrameLayout(this).apply {
      setBackgroundColor(Color.rgb(4, 15, 32))
      isFocusable = true
      isFocusableInTouchMode = true
    }

    scrollView = ScrollView(this).apply {
      isFocusable = false
      setBackgroundColor(Color.rgb(4, 15, 32))
    }

    val root = LinearLayout(this).apply {
      orientation = if (phoneMode) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(
        dp(if (phoneMode) 18 else 44),
        dp(if (phoneMode) 18 else 28),
        dp(if (phoneMode) 18 else 44),
        dp(if (phoneMode) 18 else 28)
      )
      background = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(3, 19, 46), Color.rgb(8, 74, 128), Color.rgb(2, 13, 28))
      )
    }

    val left = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
    }
    root.addView(
      left,
      if (phoneMode) {
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
      } else {
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f)
      }
    )

    val photoSize = if (phoneMode) 170 else 310
    val photo = ImageView(this).apply {
      adjustViewBounds = true
      scaleType = ImageView.ScaleType.CENTER_CROP
      background = rounded(Color.WHITE, dp(26), Color.rgb(74, 198, 255), dp(3))
      setPadding(dp(6), dp(6), dp(6), dp(6))
      loadPhotoInto(this)
    }
    left.addView(photo, LinearLayout.LayoutParams(dp(photoSize), dp(photoSize)))

    left.addView(space(if (phoneMode) 10 else 18))
    left.addView(text("Champak Roy", if (phoneMode) 24f else 30f, Color.WHITE, true).apply { gravity = Gravity.CENTER })
    left.addView(text("AI • ML • Python • DSA • Programming", if (phoneMode) 14f else 17f, Color.rgb(202, 232, 255), false).apply { gravity = Gravity.CENTER })
    left.addView(text("Pointer: remote arrows or touch. Edges scroll.", if (phoneMode) 13f else 15f, Color.rgb(255, 221, 128), true).apply {
      gravity = Gravity.CENTER
      setPadding(0, dp(10), 0, 0)
    })

    val right = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(if (phoneMode) 0 else dp(34), if (phoneMode) dp(16) else 0, 0, 0)
    }
    root.addView(
      right,
      if (phoneMode) {
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
      } else {
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f)
      }
    )

    clockCanvas = ClockCanvasView(this)
    right.addView(
      clockCanvas,
      LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (phoneMode) 110 else 150)).apply { bottomMargin = dp(10) }
    )

    right.addView(text("Installed as: Learn With Champak TV", if (phoneMode) 14f else 16f, Color.rgb(255, 221, 128), true))
    right.addView(text("Learn With", if (phoneMode) 34f else 46f, Color.WHITE, true))
    right.addView(text("Champak", if (phoneMode) 44f else 62f, Color.rgb(77, 207, 255), true).apply {
      setShadowLayer(10f, 0f, 4f, Color.BLACK)
    })
    right.addView(text("Study AI, ML, Python, DSA and Programming with Champak Roy", if (phoneMode) 16f else 22f, Color.WHITE, true).apply {
      setPadding(0, dp(8), 0, dp(10))
    })
    right.addView(text("Learn. Build. Grow.", if (phoneMode) 20f else 24f, Color.rgb(255, 221, 128), true))
    right.addView(space(if (phoneMode) 12 else 18))

    val linkPanel = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(18), dp(14), dp(18), dp(14))
      background = rounded(Color.argb(235, 255, 255, 255), dp(24), Color.rgb(82, 204, 255), dp(2))
    }
    right.addView(linkPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    linkPanel.addView(text("Blogs, YouTube, Browser & APK", if (phoneMode) 20f else 25f, Color.rgb(3, 44, 84), true))
    linkPanel.addView(space(10))

    val links = listOf(
      HomeLink("Open Blank Browser", "", false),
      HomeLink("Learn With Champak", "https://www.learnwithchampak.live", false),
      HomeLink("Inside Kashi", "https://insidekashi.com", false),
      HomeLink("YouTube Channel", "https://youtube.com/@champaksworld", false),
      HomeLink("Download Latest APK", apkUrl, true)
    )

    for (link in links) {
      val button = linkButton(link.label, link.url, phoneMode, link.external)
      linkButtons.add(button)
      linkPanel.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (phoneMode) 64 else 58)))
      linkPanel.addView(space(10))
    }

    val installedInfo = getInstalledVersionLabel()
    versionText = text(
      "Installed: $installedInfo\nLatest public APK: checking...\nAPK link: $apkUrl",
      if (phoneMode) 13f else 15f,
      Color.rgb(3, 44, 84),
      true
    ).apply {
      setPadding(dp(10), dp(8), dp(10), dp(8))
      background = rounded(Color.rgb(232, 247, 255), dp(14), Color.rgb(82, 204, 255), dp(1))
    }
    linkPanel.addView(versionText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

    statusText = text(
      if (phoneMode) "Touch near top/bottom edge to scroll. Blank Browser opens keyboard." else "Use TV remote arrows. Blank Browser opens an empty address bar.",
      if (phoneMode) 14f else 16f,
      Color.rgb(218, 240, 255),
      false
    ).apply { setPadding(0, dp(14), 0, 0) }
    right.addView(statusText)

    scrollView.addView(root)
    stage.addView(scrollView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

    pointer = TextView(this).apply {
      text = "➤"
      textSize = if (phoneMode) 32f else 38f
      setTextColor(Color.rgb(255, 221, 128))
      setShadowLayer(10f, 0f, 0f, Color.BLACK)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
      elevation = dp(20).toFloat()
    }
    stage.addView(pointer, FrameLayout.LayoutParams(dp(56), dp(56)))

    setContentView(stage)

    stage.post {
      pointerX = stage.width * 0.64f
      pointerY = stage.height * 0.60f
      updatePointerPosition()
      focusButtonUnderPointer()
      stage.requestFocus()
    }
  }

  private fun linkButton(label: String, url: String, phoneMode: Boolean, openExternal: Boolean): Button {
    val displayUrl = if (url.isBlank()) "Blank address bar + keyboard" else url
    return Button(this).apply {
      text = "$label\n$displayUrl"
      textSize = if (phoneMode) 14f else 15f
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
        if (hasFocus) statusText.text = "Focused: $label"
      }

      setOnClickListener {
        statusText.text = when {
          openExternal -> "Opening APK download link"
          url.isBlank() -> "Opening blank browser with keyboard"
          else -> "Opening inside app: $label"
        }
        if (openExternal) openOutside(url) else openInsideApp(label, url)
      }
    }
  }

  private fun getInstalledVersionCode(): Long {
    val info = packageManager.getPackageInfo(packageName, 0)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
  }

  private fun getInstalledVersionLabel(): String {
    val info = packageManager.getPackageInfo(packageName, 0)
    val name = info.versionName ?: "unknown"
    val code = getInstalledVersionCode()
    return "v$name ($code)"
  }

  private fun checkLatestVersion() {
    Thread {
      try {
        val json = URL(versionUrl).readText()
        val data = JSONObject(json)
        val latestName = data.optString("versionName", "unknown")
        val latestCode = data.optLong("versionCode", -1)
        val latestApk = data.optString("apkUrl", apkUrl)
        val installedCode = getInstalledVersionCode()
        val message = when {
          latestCode > installedCode -> "Update available"
          latestCode == installedCode -> "Up to date"
          latestCode > 0 && latestCode < installedCode -> "Installed build is newer than public APK"
          else -> "Could not compare version"
        }
        runOnUiThread {
          if (::versionText.isInitialized) {
            versionText.text = "Installed: ${getInstalledVersionLabel()}\nLatest public APK: v$latestName ($latestCode)\nStatus: $message\nAPK link: $latestApk"
          }
          if (::statusText.isInitialized) statusText.text = message
        }
      } catch (ex: Exception) {
        runOnUiThread {
          if (::versionText.isInitialized) {
            versionText.text = "Installed: ${getInstalledVersionLabel()}\nLatest public APK: unable to check now\nAPK link: $apkUrl"
          }
        }
        Log.e(tag, "Version check failed", ex)
      }
    }.start()
  }

  private fun openInsideApp(title: String, url: String) {
    try {
      startActivity(Intent(this, BrowserActivity::class.java).apply {
        putExtra(BrowserActivity.EXTRA_TITLE, title)
        putExtra(BrowserActivity.EXTRA_URL, url)
      })
    } catch (ex: Exception) {
      Toast.makeText(this, "Could not open browser screen", Toast.LENGTH_LONG).show()
      Log.e(tag, "Unable to open internal browser: $url", ex)
    }
  }

  private fun openOutside(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (ex: Exception) {
      Toast.makeText(this, "No browser/downloader found", Toast.LENGTH_LONG).show()
      Log.e(tag, "Unable to open external URL: $url", ex)
    }
  }

  private fun loadPhotoInto(imageView: ImageView) {
    try {
      val bitmap = BitmapFactory.decodeStream(assets.open("champak-photo.png"))
      if (bitmap != null) {
        imageView.setImageBitmap(bitmap)
        return
      }
    } catch (_: Exception) { }

    try {
      val encoded = BufferedReader(InputStreamReader(assets.open("champak_photo.b64"))).readText().replace("\n", "").trim()
      val bytes = Base64.decode(encoded, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
      if (bitmap != null) {
        imageView.setImageBitmap(bitmap)
        return
      }
    } catch (_: Exception) { }

    imageView.setBackgroundColor(Color.rgb(12, 84, 130))
    imageView.contentDescription = "Champak Roy photo"
    Thread {
      try {
        val bitmap = BitmapFactory.decodeStream(URL("https://programmer-s-picnic.github.io/json-images/tv/champak-photo.png").openStream())
        if (bitmap != null) runOnUiThread { imageView.setImageBitmap(bitmap) }
      } catch (ex: Exception) {
        Log.e(tag, "Could not load hosted photo", ex)
      }
    }.start()
  }

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (::stage.isInitialized && ::pointer.isInitialized) {
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
          pointerX = (event.x - pointer.width / 2f).coerceIn(0f, (stage.width - pointer.width).toFloat())
          pointerY = (event.y - pointer.height / 2f).coerceIn(0f, (stage.height - pointer.height).toFloat())
          updatePointerPosition()
          val scrolled = autoScrollAtPointerEdges(0)
          val target = focusButtonUnderPointer()
          if (event.actionMasked == MotionEvent.ACTION_UP) {
            statusText.text = when {
              scrolled -> "Scrolled page from edge"
              target != null -> "Tap/OK on: ${target.text.toString().lineSequence().first()}"
              else -> "Pointer moved"
            }
          }
        }
      }
    }
    return super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      return when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> { movePointer(0, -1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { movePointer(0, 1); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { movePointer(-1, 0); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { movePointer(1, 0); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { clickButtonUnderPointer(); true }
        else -> super.dispatchKeyEvent(event)
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun movePointer(dx: Int, dy: Int) {
    val step = dp(54).toFloat()
    val maxX = (stage.width - pointer.width).coerceAtLeast(0).toFloat()
    val maxY = (stage.height - pointer.height).coerceAtLeast(0).toFloat()
    val desiredX = pointerX + dx * step
    val desiredY = pointerY + dy * step

    pointerX = desiredX.coerceIn(0f, maxX)
    pointerY = desiredY.coerceIn(0f, maxY)
    updatePointerPosition()

    val scrolled = autoScrollAtPointerEdges(dy)
    val target = focusButtonUnderPointer()
    statusText.text = when {
      scrolled -> if (dy < 0) "Pointer at top edge: scrolling up" else "Pointer at bottom edge: scrolling down"
      target != null -> "Pointer over: ${target.text.toString().lineSequence().first()}"
      desiredY < 0f -> "Top edge reached"
      desiredY > maxY -> "Bottom edge reached"
      else -> "Pointer moved"
    }
  }

  private fun autoScrollAtPointerEdges(dy: Int): Boolean {
    if (!::scrollView.isInitialized || !::stage.isInitialized || !::pointer.isInitialized) return false
    if (stage.height <= 0) return false

    val edge = dp(72).toFloat()
    val scrollAmount = dp(130)
    val maxPointerY = (stage.height - pointer.height).coerceAtLeast(0).toFloat()
    val pointerBottom = pointerY + pointer.height
    val nearTop = pointerY <= edge
    val nearBottom = pointerBottom >= stage.height - edge
    val canScrollUp = scrollView.scrollY > 0
    val canScrollDown = scrollView.getChildAt(0)?.let { child -> scrollView.scrollY + scrollView.height < child.height } == true

    return when {
      (dy < 0 || nearTop) && nearTop && canScrollUp -> {
        scrollView.smoothScrollBy(0, -scrollAmount)
        pointerY = (edge + dp(8)).coerceAtMost(maxPointerY)
        updatePointerPosition()
        true
      }
      (dy > 0 || nearBottom) && nearBottom && canScrollDown -> {
        scrollView.smoothScrollBy(0, scrollAmount)
        pointerY = (stage.height - edge - pointer.height - dp(8)).coerceIn(0f, maxPointerY)
        updatePointerPosition()
        true
      }
      else -> false
    }
  }

  private fun updatePointerPosition() {
    pointer.x = pointerX
    pointer.y = pointerY
    pointer.bringToFront()
  }

  private fun focusButtonUnderPointer(): Button? {
    val button = buttonUnderPointer()
    if (button != null && !button.hasFocus()) button.requestFocus()
    return button
  }

  private fun clickButtonUnderPointer() {
    val button = buttonUnderPointer()
    if (button != null) {
      statusText.text = "Pointer clicked: ${button.text.toString().lineSequence().first()}"
      button.performClick()
    } else {
      statusText.text = "Move pointer onto a link button, then press OK"
      Toast.makeText(this, "No button under pointer", Toast.LENGTH_SHORT).show()
    }
  }

  private fun buttonUnderPointer(): Button? {
    if (!::stage.isInitialized || !::pointer.isInitialized) return null
    val stageLocation = IntArray(2)
    stage.getLocationOnScreen(stageLocation)
    val px = (stageLocation[0] + pointer.x + pointer.width / 2).toInt()
    val py = (stageLocation[1] + pointer.y + pointer.height / 2).toInt()
    val rect = Rect()
    for (button in linkButtons) {
      button.getGlobalVisibleRect(rect)
      if (rect.contains(px, py)) return button
    }
    return null
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

  private fun space(h: Int): Space = Space(this).apply {
    layoutParams = LinearLayout.LayoutParams(1, dp(h))
  }

  private fun rounded(color: Int, radius: Int, strokeColor: Int, strokeWidth: Int): GradientDrawable {
    return GradientDrawable().apply {
      setColor(color)
      cornerRadius = radius.toFloat()
      if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }
  }

  private fun buttonBg(focused: Boolean): GradientDrawable {
    val colors = if (focused) intArrayOf(Color.rgb(255, 168, 37), Color.rgb(255, 111, 0)) else intArrayOf(Color.rgb(9, 74, 132), Color.rgb(5, 43, 92))
    return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
      cornerRadius = dp(18).toFloat()
      setStroke(dp(if (focused) 4 else 2), if (focused) Color.WHITE else Color.rgb(82, 204, 255))
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
