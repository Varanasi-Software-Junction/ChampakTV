package com.learnwithchampak.tv

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashSet
import java.util.Locale

class BrowserActivity : AppCompatActivity() {

  companion object {
    const val EXTRA_URL = "com.learnwithchampak.tv.EXTRA_URL"
    const val EXTRA_TITLE = "com.learnwithchampak.tv.EXTRA_TITLE"
    private const val PREFS = "champak_browser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks"
    private const val KEY_HISTORY = "history"
  }

  private val tag = "ChampakTVBrowser"
  private lateinit var webView: WebView
  private lateinit var titleText: TextView
  private lateinit var clockText: TextView
  private lateinit var statusText: TextView
  private lateinit var addressBar: EditText
  private lateinit var progress: ProgressBar
  private lateinit var prefs: SharedPreferences
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
    prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (resources.configuration.screenWidthDp >= 700) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    val startTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Champak Browser"
    val startUrl = normalizeUrl(intent.getStringExtra(EXTRA_URL) ?: "https://www.learnwithchampak.live")

    buildScreen(startTitle)
    setupWebView()
    loadAddress(startUrl)
    clockHandler.post(clockRunnable)
  }

  override fun onDestroy() {
    clockHandler.removeCallbacks(clockRunnable)
    webView.destroy()
    super.onDestroy()
  }

  private fun updateClock() {
    if (::clockText.isInitialized) clockText.text = clockFormat.format(Date())
  }

  private fun buildScreen(startTitle: String) {
    val phoneMode = resources.configuration.screenWidthDp < 700

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(3, 15, 34))
    }

    val topBar = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(10), dp(8), dp(10), dp(8))
      background = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(2, 31, 69), Color.rgb(6, 85, 145))
      )
    }
    root.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (phoneMode) 222 else 164)))

    val buttonRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    topBar.addView(buttonRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

    val backButton = toolbarButton("Back") { goBackOrClose() }
    val forwardButton = toolbarButton("Forward") { if (webView.canGoForward()) webView.goForward() else showStatus("No forward page") }
    val reloadButton = toolbarButton("Reload") { webView.reload() }
    val homeButton = toolbarButton("Home") { finish() }
    val externalButton = toolbarButton(if (phoneMode) "Outside" else "Open Outside") { openOutside() }

    buttonRow.addView(backButton, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(forwardButton, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(reloadButton, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(homeButton, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(externalButton, LinearLayout.LayoutParams(0, dp(44), 1.2f))

    val addressRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(8), 0, 0)
    }
    topBar.addView(addressRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))

    addressBar = EditText(this).apply {
      hint = "Enter website address or search"
      textSize = if (phoneMode) 14f else 16f
      singleLine = true
      setTextColor(Color.rgb(3, 44, 84))
      setHintTextColor(Color.rgb(80, 105, 125))
      setPadding(dp(12), 0, dp(12), 0)
      background = solid(Color.WHITE, dp(12))
      imeOptions = EditorInfo.IME_ACTION_GO
      setOnEditorActionListener { _, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
          loadAddress(addressBar.text.toString())
          true
        } else false
      }
    }
    addressRow.addView(addressBar, LinearLayout.LayoutParams(0, dp(48), 1f))
    addressRow.addView(toolbarButton("Go") { loadAddress(addressBar.text.toString()) }, LinearLayout.LayoutParams(dp(if (phoneMode) 62 else 82), dp(48)))

    val browserRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(8), 0, 0)
    }
    topBar.addView(browserRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)))

    browserRow.addView(toolbarButton("Bookmark") { addBookmark() }, LinearLayout.LayoutParams(0, dp(46), 1.25f))
    browserRow.addView(toolbarButton("Bookmarks") { showBookmarks() }, LinearLayout.LayoutParams(0, dp(46), 1.25f))
    browserRow.addView(toolbarButton("History") { showHistory() }, LinearLayout.LayoutParams(0, dp(46), 1f))
    browserRow.addView(toolbarButton("Downloads") { openDownloads() }, LinearLayout.LayoutParams(0, dp(46), 1.15f))

    val titleClockBox = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
      setPadding(0, dp(6), 0, 0)
    }
    topBar.addView(titleClockBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

    titleText = TextView(this).apply {
      text = startTitle
      textSize = if (phoneMode) 14f else 18f
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.RIGHT
      maxLines = 1
    }
    titleClockBox.addView(titleText)

    clockText = TextView(this).apply {
      textSize = if (phoneMode) 12f else 15f
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
      text = "Browser ready: address bar, bookmarks, history and downloads enabled."
      textSize = if (phoneMode) 12f else 14f
      setTextColor(Color.rgb(218, 240, 255))
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      background = solid(Color.rgb(4, 24, 54), dp(0))
    }
    root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

    setContentView(root)
    if (!phoneMode) addressBar.requestFocus()
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
      builtInZoomControls = true
      displayZoomControls = false
      cacheMode = WebSettings.LOAD_DEFAULT
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = true
      allowContentAccess = true
    }

    webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
      startDownload(url, userAgent, contentDisposition, mimeType)
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val target = request.url.toString()
        if (target.startsWith("tel:") || target.startsWith("mailto:") || target.startsWith("whatsapp:")) {
          openExternalUrl(target)
          return true
        }
        view.loadUrl(target)
        return true
      }

      @Suppress("DEPRECATION")
      override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
          openExternalUrl(url)
          return true
        }
        view.loadUrl(url)
        return true
      }

      override fun onPageFinished(view: WebView, url: String) {
        val title = view.title ?: url
        titleText.text = title
        addressBar.setText(url)
        addHistory(title, url)
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
        if (newProgress >= 100) progress.progress = 0
      }

      override fun onReceivedTitle(view: WebView, title: String) {
        titleText.text = title
      }
    }
  }

  private fun toolbarButton(label: String, action: () -> Unit): Button {
    return Button(this).apply {
      text = label
      textSize = 11.5f
      isAllCaps = false
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      background = buttonBg(false)
      isFocusable = true
      isFocusableInTouchMode = true
      setPadding(dp(3), 0, dp(3), 0)

      setOnFocusChangeListener { view, hasFocus ->
        background = buttonBg(hasFocus)
        view.animate().scaleX(if (hasFocus) 1.04f else 1f).scaleY(if (hasFocus) 1.04f else 1f).setDuration(100).start()
      }

      setOnClickListener { action() }
    }
  }

  private fun loadAddress(input: String) {
    val target = normalizeUrl(input)
    addressBar.setText(target)
    showStatus("Opening: $target")
    webView.loadUrl(target)
  }

  private fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "https://www.learnwithchampak.live"
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    if (value.startsWith("www.") || (value.contains(".") && !value.contains(" "))) return "https://$value"
    val query = URLEncoder.encode(value, "UTF-8")
    return "https://www.google.com/search?q=$query"
  }

  private fun goBackOrClose() {
    if (webView.canGoBack()) webView.goBack() else finish()
  }

  private fun openOutside() {
    val url = webView.url ?: return
    openExternalUrl(url)
  }

  private fun openExternalUrl(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (ex: Exception) {
      Toast.makeText(this, "No app found for this link", Toast.LENGTH_LONG).show()
      Log.e(tag, "Could not open external URL: $url", ex)
    }
  }

  private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimeType: String) {
    try {
      val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
      val request = DownloadManager.Request(Uri.parse(url)).apply {
        setMimeType(mimeType)
        addRequestHeader("User-Agent", userAgent)
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
        setTitle(fileName)
        setDescription("Downloading from Champak Browser")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        allowScanningByMediaScanner()
      }
      val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      dm.enqueue(request)
      showStatus("Downloading: $fileName")
      Toast.makeText(this, "Download started: $fileName", Toast.LENGTH_LONG).show()
    } catch (ex: Exception) {
      Toast.makeText(this, "Download failed. Opening outside instead.", Toast.LENGTH_LONG).show()
      Log.e(tag, "Download failed: $url", ex)
      openExternalUrl(url)
    }
  }

  private fun openDownloads() {
    try {
      startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    } catch (ex: Exception) {
      Toast.makeText(this, "Downloads app not found", Toast.LENGTH_LONG).show()
      Log.e(tag, "Could not open downloads", ex)
    }
  }

  private fun addBookmark() {
    val url = webView.url ?: addressBar.text.toString()
    val title = titleText.text.toString().ifBlank { url }
    val item = "$title|$url"
    val items = loadItems(KEY_BOOKMARKS)
    items.remove(item)
    items.add(0, item)
    saveItems(KEY_BOOKMARKS, items.take(80))
    showStatus("Bookmarked: $title")
    Toast.makeText(this, "Bookmark saved", Toast.LENGTH_SHORT).show()
  }

  private fun addHistory(title: String, url: String) {
    if (url.isBlank()) return
    val item = "${title.ifBlank { url }}|$url"
    val deduped = LinkedHashSet<String>()
    deduped.add(item)
    for (old in loadItems(KEY_HISTORY)) {
      if (!old.endsWith("|$url")) deduped.add(old)
    }
    saveItems(KEY_HISTORY, deduped.take(120))
  }

  private fun showBookmarks() {
    showSavedList("Bookmarks", KEY_BOOKMARKS, "No bookmarks yet")
  }

  private fun showHistory() {
    showSavedList("History", KEY_HISTORY, "No history yet")
  }

  private fun showSavedList(title: String, key: String, emptyMessage: String) {
    val items = loadItems(key)
    if (items.isEmpty()) {
      Toast.makeText(this, emptyMessage, Toast.LENGTH_LONG).show()
      return
    }
    val labels = items.map { it.substringBefore("|") }.toTypedArray()
    AlertDialog.Builder(this)
      .setTitle(title)
      .setItems(labels) { _, which ->
        val url = items[which].substringAfter("|", items[which])
        loadAddress(url)
      }
      .setNegativeButton("Close", null)
      .setNeutralButton("Clear") { _, _ ->
        prefs.edit().remove(key).apply()
        Toast.makeText(this, "$title cleared", Toast.LENGTH_SHORT).show()
      }
      .show()
  }

  private fun loadItems(key: String): MutableList<String> {
    return prefs.getString(key, "")
      .orEmpty()
      .lines()
      .map { it.trim() }
      .filter { it.contains("|") }
      .toMutableList()
  }

  private fun saveItems(key: String, items: List<String>) {
    prefs.edit().putString(key, items.joinToString("\n")).apply()
  }

  private fun showStatus(message: String) {
    if (::statusText.isInitialized) statusText.text = message
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
        KeyEvent.KEYCODE_MENU -> {
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
