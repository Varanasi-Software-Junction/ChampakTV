package com.learnwithchampak.tv

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BrowserActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_URL = "com.learnwithchampak.tv.EXTRA_URL"
    const val EXTRA_TITLE = "com.learnwithchampak.tv.EXTRA_TITLE"
  }

  private val tag = "ChampakTVBrowser"
  private lateinit var webView: WebView
  private lateinit var titleText: TextView
  private lateinit var clockText: TextView
  private lateinit var statusText: TextView
  private lateinit var progress: ProgressBar
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

    val startTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Champak TV Browser"
    val startUrl = intent.getStringExtra(EXTRA_URL) ?: "https://www.learnwithchampak.live"

    buildScreen(startTitle)
    setupWebView()
    webView.loadUrl(startUrl)
    clockHandler.post(clockRunnable)
  }

  override fun onDestroy() {
    clockHandler.removeCallbacks(clockRunnable)
    webView.destroy()
    super.onDestroy()
  }

  private fun updateClock() {
    if (::clockText.isInitialized) {
      clockText.text = clockFormat.format(Date())
    }
  }

  private fun buildScreen(startTitle: String) {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(3, 15, 34))
    }

    val topBar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(14), dp(10), dp(14), dp(8))
      background = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(2, 31, 69), Color.rgb(6, 85, 145))
      )
    }
    root.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(78)))

    val backButton = toolbarButton("Back") { goBackOrClose() }
    val forwardButton = toolbarButton("Forward") {
      if (webView.canGoForward()) webView.goForward() else showStatus("No forward page")
    }
    val reloadButton = toolbarButton("Reload") { webView.reload() }
    val homeButton = toolbarButton("Home") { finish() }
    val externalButton = toolbarButton("Open Outside") { openOutside() }

    topBar.addView(backButton, LinearLayout.LayoutParams(dp(112), dp(54)))
    topBar.addView(forwardButton, LinearLayout.LayoutParams(dp(124), dp(54)))
    topBar.addView(reloadButton, LinearLayout.LayoutParams(dp(116), dp(54)))
    topBar.addView(homeButton, LinearLayout.LayoutParams(dp(100), dp(54)))
    topBar.addView(externalButton, LinearLayout.LayoutParams(dp(156), dp(54)))

    val titleClockBox = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
      setPadding(dp(16), 0, 0, 0)
    }
    topBar.addView(titleClockBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))

    titleText = TextView(this).apply {
      text = startTitle
      textSize = 19f
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.RIGHT
      maxLines = 1
    }
    titleClockBox.addView(titleText)

    clockText = TextView(this).apply {
      textSize = 16f
      setTextColor(Color.rgb(255, 221, 128))
      gravity = Gravity.RIGHT
      maxLines = 1
    }
    titleClockBox.addView(clockText)

    progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 100
      progress = 0
    }
    root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5)))

    webView = WebView(this).apply {
      isFocusable = true
      isFocusableInTouchMode = true
      setBackgroundColor(Color.WHITE)
    }
    root.addView(webView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

    statusText = TextView(this).apply {
      text = "Browser controls: Back, Forward, Reload, Home, Open Outside. Use Up/Down to scroll page."
      textSize = 14f
      setTextColor(Color.rgb(218, 240, 255))
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(16), 0, dp(16), 0)
      background = solid(Color.rgb(4, 24, 54), dp(0))
    }
    root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

    setContentView(root)
    backButton.requestFocus()
  }

  private fun setupWebView() {
    WebView.setWebContentsDebuggingEnabled(true)

    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      databaseEnabled = true
      loadsImagesAutomatically = true
      loadWithOverviewMode = true
      useWideViewPort = true
      builtInZoomControls = false
      displayZoomControls = false
      cacheMode = WebSettings.LOAD_DEFAULT
      mediaPlaybackRequiresUserGesture = false
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        view.loadUrl(request.url.toString())
        return true
      }

      override fun onPageFinished(view: WebView, url: String) {
        titleText.text = view.title ?: url
        showStatus(url)
        progress.progress = 0
      }

      override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        showStatus("Page error: ${error.description}")
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView, newProgress: Int) {
        progress.progress = newProgress
        if (newProgress >= 100) {
          progress.progress = 0
        }
      }

      override fun onReceivedTitle(view: WebView, title: String) {
        titleText.text = title
      }
    }
  }

  private fun toolbarButton(label: String, action: () -> Unit): Button {
    return Button(this).apply {
      text = label
      textSize = 13f
      isAllCaps = false
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      background = buttonBg(false)
      isFocusable = true
      isFocusableInTouchMode = true
      setPadding(dp(8), 0, dp(8), 0)

      setOnFocusChangeListener { view, hasFocus ->
        background = buttonBg(hasFocus)
        view.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(100).start()
      }

      setOnClickListener { action() }
    }
  }

  private fun goBackOrClose() {
    if (webView.canGoBack()) {
      webView.goBack()
    } else {
      finish()
    }
  }

  private fun openOutside() {
    val url = webView.url ?: return
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (ex: Exception) {
      Toast.makeText(this, "No external browser found", Toast.LENGTH_LONG).show()
      Log.e(tag, "Could not open outside: $url", ex)
    }
  }

  private fun showStatus(message: String) {
    if (::statusText.isInitialized) {
      statusText.text = message
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      when (event.keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
          goBackOrClose()
          return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
          if (currentFocus == webView) {
            webView.scrollBy(0, dp(150))
            showStatus("Scroll down")
            return true
          }
        }
        KeyEvent.KEYCODE_DPAD_UP -> {
          if (currentFocus == webView) {
            webView.scrollBy(0, -dp(150))
            showStatus("Scroll up")
            return true
          }
        }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MENU -> {
          webView.requestFocus()
          showStatus("Page area focused. Use Up/Down to scroll.")
          return true
        }
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun buttonBg(focused: Boolean): GradientDrawable {
    val colors = if (focused) {
      intArrayOf(Color.rgb(255, 168, 37), Color.rgb(255, 111, 0))
    } else {
      intArrayOf(Color.rgb(8, 77, 138), Color.rgb(4, 45, 98))
    }
    return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors).apply {
      cornerRadius = dp(14).toFloat()
      setStroke(dp(if (focused) 3 else 1), if (focused) Color.WHITE else Color.rgb(82, 204, 255))
    }
  }

  private fun solid(color: Int, radius: Int): GradientDrawable {
    return GradientDrawable().apply {
      setColor(color)
      cornerRadius = radius.toFloat()
    }
  }

  private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
