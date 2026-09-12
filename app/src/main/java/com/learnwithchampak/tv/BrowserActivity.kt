package com.learnwithchampak.tv

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import android.widget.FrameLayout
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
  private lateinit var stage: FrameLayout
  private lateinit var webView: WebView
  private lateinit var pointer: TextView
  private lateinit var titleText: TextView
  private lateinit var clockText: TextView
  private lateinit var statusText: TextView
  private lateinit var addressBar: EditText
  private lateinit var progress: ProgressBar
  private lateinit var prefs: SharedPreferences
  private val browserButtons = mutableListOf<Button>()
  private val clockHandler = Handler(Looper.getMainLooper())
  private val clockFormat = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm:ss a", Locale.getDefault())
  private var pointerX = 0f
  private var pointerY = 0f
  private var lastEdgeScrollAt = 0L

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

    val rawStartUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
    val startTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Champak Browser"
    val openBlank = rawStartUrl.isBlank() || rawStartUrl == "about:blank"

    buildScreen(if (openBlank) "Blank Browser" else startTitle)
    setupWebView()

    if (openBlank) openBlankPage(true) else loadAddress(rawStartUrl)
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

    stage = FrameLayout(this).apply {
      setBackgroundColor(Color.rgb(3, 15, 34))
      isFocusable = true
      isFocusableInTouchMode = true
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(3, 15, 34))
    }
    stage.addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

    val topBar = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(10), dp(8), dp(10), dp(8))
      background = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Color.rgb(2, 31, 69), Color.rgb(6, 85, 145))
      )
    }
    root.addView(topBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (phoneMode) 270 else 208)))

    val buttonRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    topBar.addView(buttonRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))

    buttonRow.addView(toolbarButton("Back") { goBackOrClose() }, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(toolbarButton("Forward") { if (webView.canGoForward()) webView.goForward() else showStatus("No forward page") }, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(toolbarButton("Reload") { webView.reload() }, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(toolbarButton("Home") { finish() }, LinearLayout.LayoutParams(0, dp(44), 1f))
    buttonRow.addView(toolbarButton(if (phoneMode) "Outside" else "Open Outside") { openOutside() }, LinearLayout.LayoutParams(0, dp(44), 1.2f))

    val addressRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(8), 0, 0)
    }
    topBar.addView(addressRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)))

    addressBar = EditText(this).apply {
      hint = "Blank address bar: type website or search"
      textSize = if (phoneMode) 14f else 16f
      setSingleLine(true)
      setTextColor(Color.rgb(3, 44, 84))
      setHintTextColor(Color.rgb(80, 105, 125))
      setPadding(dp(12), 0, dp(12), 0)
      background = solid(Color.WHITE, dp(12))
      imeOptions = EditorInfo.IME_ACTION_GO
      isFocusable = true
      isFocusableInTouchMode = true
      isCursorVisible = true
      setSelectAllOnFocus(true)
      setOnClickListener { focusAddressBar(true) }
      setOnTouchListener { _, _ ->
        focusAddressBar(true)
        false
      }
      setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) addressBar.postDelayed({ showKeyboard() }, 120)
      }
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

    browserRow.addView(toolbarButton("Blank") { openBlankPage(true) }, LinearLayout.LayoutParams(0, dp(46), 0.9f))
    browserRow.addView(toolbarButton("Bookmark") { addBookmark() }, LinearLayout.LayoutParams(0, dp(46), 1.25f))
    browserRow.addView(toolbarButton("Bookmarks") { showBookmarks() }, LinearLayout.LayoutParams(0, dp(46), 1.25f))
    browserRow.addView(toolbarButton("History") { showHistory() }, LinearLayout.LayoutParams(0, dp(46), 1f))
    browserRow.addView(toolbarButton("Downloads") { openDownloads() }, LinearLayout.LayoutParams(0, dp(46), 1.15f))

    val helpRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(6), 0, 0)
    }
    topBar.addView(helpRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))
    helpRow.addView(toolbarButton(if (phoneMode) "Keyboard" else "Open Keyboard") { focusAddressBar(true) }, LinearLayout.LayoutParams(0, dp(34), 1f))
    helpRow.addView(toolbarButton(if (phoneMode) "Page" else "Focus Page") { focusWebPage() }, LinearLayout.LayoutParams(0, dp(34), 1f))

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
      text = "Smooth pointer. Edge scroll uses WebView + page JavaScript."
      textSize = if (phoneMode) 12f else 14f
      setTextColor(Color.rgb(218, 240, 255))
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), 0, dp(12), 0)
      background = solid(Color.rgb(4, 24, 54), dp(0))
    }
    root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(38)))

    pointer = TextView(this).apply {
      text = "➤"
      textSize = if (phoneMode) 30f else 38f
      setTextColor(Color.rgb(255, 221, 128))
      setShadowLayer(10f, 0f, 0f, Color.BLACK)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
      elevation = dp(30).toFloat()
      isClickable = false
      isFocusable = false
    }
    stage.addView(pointer, FrameLayout.LayoutParams(dp(56), dp(56)))

    setContentView(stage)

    stage.post {
      placePointerInWebPage()
      focusWebPage()
    }
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
        titleText.text = if (url == "about:blank") "Blank Browser" else title
        if (url == "about:blank") addressBar.setText("") else addressBar.setText(url)
        if (url != "about:blank") addHistory(title, url)
        showStatus("Pointer active. Edge contact scrolls opened web pages.")
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
      browserButtons.add(this)
    }
  }

  private fun openBlankPage(showKeyboardAfterOpen: Boolean) {
    webView.loadUrl("about:blank")
    titleText.text = "Blank Browser"
    addressBar.setText("")
    showStatus("Blank address bar ready. Start typing a website or search.")
    if (showKeyboardAfterOpen) addressBar.postDelayed({ focusAddressBar(true) }, 220)
  }

  private fun loadAddress(input: String) {
    val target = normalizeUrl(input)
    addressBar.setText(target)
    showStatus("Opening: $target")
    webView.loadUrl(target)
    focusWebPage()
  }

  private fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "about:blank"
    if (value.startsWith("http://") || value.startsWith("https://") || value == "about:blank") return value
    if (value.startsWith("www.") || (value.contains(".") && !value.contains(" "))) return "https://$value"
    val query = URLEncoder.encode(value, "UTF-8")
    return "https://www.google.com/search?q=$query"
  }

  private fun focusAddressBar(openKeyboard: Boolean) {
    addressBar.isFocusableInTouchMode = true
    addressBar.requestFocus()
    addressBar.requestFocusFromTouch()
    addressBar.setSelection(addressBar.text.length)
    pointer.visibility = TextView.GONE
    if (openKeyboard) showKeyboard()
    showStatus("Keyboard ready. Type address/search and press Go.")
  }

  private fun showKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(addressBar.windowToken, 0)
  }

  private fun focusWebPage() {
    hideKeyboard()
    addressBar.clearFocus()
    webView.requestFocus()
    pointer.visibility = TextView.VISIBLE
    pointer.bringToFront()
    showStatus("Page focused. Arrows move pointer smoothly; OK clicks; edges scroll.")
  }

  private fun goBackOrClose() {
    if (webView.canGoBack()) webView.goBack() else finish()
  }

  private fun openOutside() {
    val url = webView.url ?: return
    if (url == "about:blank") {
      showStatus("Blank page has no outside link")
      return
    }
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
    if (url.isBlank() || url == "about:blank") {
      Toast.makeText(this, "Open a page first", Toast.LENGTH_SHORT).show()
      return
    }
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
    if (url.isBlank() || url == "about:blank") return
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

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (::stage.isInitialized && ::pointer.isInitialized) {
      if (isInsideView(addressBar, event.rawX.toInt(), event.rawY.toInt())) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) focusAddressBar(true)
        return super.dispatchTouchEvent(event)
      }

      if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE || event.actionMasked == MotionEvent.ACTION_UP) {
        if (isInsideView(webView, event.rawX.toInt(), event.rawY.toInt())) {
          pointer.visibility = TextView.VISIBLE
          pointerX = (event.x - pointer.width / 2f).coerceIn(0f, (stage.width - pointer.width).toFloat())
          pointerY = (event.y - pointer.height / 2f).coerceIn(0f, (stage.height - pointer.height).toFloat())
          updatePointerPosition(false)
          autoScrollWebAtPointerEdges(0)
        }
      }
    }
    return super.dispatchTouchEvent(event)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      if (currentFocus == addressBar) return super.dispatchKeyEvent(event)
      return when (event.keyCode) {
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { goBackOrClose(); true }
        KeyEvent.KEYCODE_DPAD_UP -> { movePointer(0, -1); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { movePointer(0, 1); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { movePointer(-1, 0); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { movePointer(1, 0); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { clickAtPointer(); true }
        KeyEvent.KEYCODE_MENU -> { focusAddressBar(true); true }
        else -> super.dispatchKeyEvent(event)
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun movePointer(dx: Int, dy: Int) {
    focusWebPage()
    val step = dp(32).toFloat()
    val maxX = (stage.width - pointer.width).coerceAtLeast(0).toFloat()
    val maxY = (stage.height - pointer.height).coerceAtLeast(0).toFloat()
    pointerX = (pointerX + dx * step).coerceIn(0f, maxX)
    pointerY = (pointerY + dy * step).coerceIn(0f, maxY)
    keepPointerNearWebArea()
    updatePointerPosition(true)
    val scrolled = autoScrollWebAtPointerEdges(dy)
    showStatus(if (scrolled) "Scrolling opened web page" else "Pointer moved smoothly. OK clicks web page.")
  }

  private fun clickAtPointer() {
    if (isPointerOverAddressBar()) {
      focusAddressBar(true)
      return
    }

    val local = pointerLocalToWeb() ?: run {
      showStatus("Move pointer inside the web page")
      return
    }
    val now = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, local.first, local.second, 0)
    val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, local.first, local.second, 0)
    webView.dispatchTouchEvent(down)
    webView.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
    showStatus("Clicked web page at pointer")
  }

  private fun pointerLocalToWeb(): Pair<Float, Float>? {
    val stageLoc = IntArray(2)
    val webLoc = IntArray(2)
    stage.getLocationOnScreen(stageLoc)
    webView.getLocationOnScreen(webLoc)
    val centerXScreen = stageLoc[0] + pointerX + pointer.width / 2f
    val centerYScreen = stageLoc[1] + pointerY + pointer.height / 2f
    val localX = centerXScreen - webLoc[0]
    val localY = centerYScreen - webLoc[1]
    if (localX < 0 || localY < 0 || localX > webView.width || localY > webView.height) return null
    return Pair(localX, localY)
  }

  private fun autoScrollWebAtPointerEdges(dy: Int): Boolean {
    val rect = webRectOnStage()
    if (rect.height() <= 0) return false
    val edge = dp(72)
    val centerY = (pointerY + pointer.height / 2f).toInt()
    val nearTop = centerY <= rect.top + edge
    val nearBottom = centerY >= rect.bottom - edge
    val scrollAmount = dp(220)

    return when {
      (dy < 0 || nearTop) && nearTop -> {
        performPageScroll(-scrollAmount)
        pointerY = (rect.top + edge + 8).toFloat().coerceAtMost((rect.bottom - pointer.height).toFloat())
        updatePointerPosition(true)
        true
      }
      (dy > 0 || nearBottom) && nearBottom -> {
        performPageScroll(scrollAmount)
        pointerY = (rect.bottom - edge - pointer.height - 8).toFloat().coerceAtLeast(rect.top.toFloat())
        updatePointerPosition(true)
        true
      }
      else -> false
    }
  }

  private fun performPageScroll(amount: Int) {
    val now = SystemClock.uptimeMillis()
    if (now - lastEdgeScrollAt < 70) return
    lastEdgeScrollAt = now
    webView.scrollBy(0, amount)
    val js = """
      (function(){
        var amount = $amount;
        var scrolled = false;
        var el = document.scrollingElement || document.documentElement || document.body;
        if (el) { el.scrollBy({top: amount, left: 0, behavior: 'smooth'}); scrolled = true; }
        var midX = Math.floor(window.innerWidth / 2);
        var midY = amount > 0 ? window.innerHeight - 12 : 12;
        var hit = document.elementFromPoint(midX, midY);
        while (hit && hit !== document.body && hit !== document.documentElement) {
          var style = window.getComputedStyle(hit);
          var canScroll = /(auto|scroll)/.test(style.overflowY) && hit.scrollHeight > hit.clientHeight;
          if (canScroll) { hit.scrollBy({top: amount, left: 0, behavior: 'smooth'}); break; }
          hit = hit.parentElement;
        }
      })();
    """.trimIndent()
    webView.evaluateJavascript(js, null)
  }

  private fun keepPointerNearWebArea() {
    val rect = webRectOnStage()
    val minY = rect.top.toFloat()
    val maxY = (rect.bottom - pointer.height).coerceAtLeast(rect.top).toFloat()
    if (pointerY < minY) pointerY = minY
    if (pointerY > maxY) pointerY = maxY
  }

  private fun placePointerInWebPage() {
    val rect = webRectOnStage()
    pointerX = (rect.left + rect.width() * 0.50f - pointer.width / 2f).coerceIn(0f, (stage.width - pointer.width).toFloat())
    pointerY = (rect.top + rect.height() * 0.50f - pointer.height / 2f).coerceIn(0f, (stage.height - pointer.height).toFloat())
    updatePointerPosition(false)
  }

  private fun webRectOnStage(): Rect {
    val stageLoc = IntArray(2)
    val webLoc = IntArray(2)
    stage.getLocationOnScreen(stageLoc)
    webView.getLocationOnScreen(webLoc)
    val left = webLoc[0] - stageLoc[0]
    val top = webLoc[1] - stageLoc[1]
    return Rect(left, top, left + webView.width, top + webView.height)
  }

  private fun isPointerOverAddressBar(): Boolean {
    val stageLoc = IntArray(2)
    stage.getLocationOnScreen(stageLoc)
    val px = (stageLoc[0] + pointerX + pointer.width / 2f).toInt()
    val py = (stageLoc[1] + pointerY + pointer.height / 2f).toInt()
    return isInsideView(addressBar, px, py)
  }

  private fun isInsideView(view: android.view.View, screenX: Int, screenY: Int): Boolean {
    val rect = Rect()
    view.getGlobalVisibleRect(rect)
    return rect.contains(screenX, screenY)
  }

  private fun updatePointerPosition(animated: Boolean) {
    pointer.animate().cancel()
    if (animated) {
      pointer.animate().x(pointerX).y(pointerY).setDuration(95).start()
    } else {
      pointer.x = pointerX
      pointer.y = pointerY
    }
    pointer.bringToFront()
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
