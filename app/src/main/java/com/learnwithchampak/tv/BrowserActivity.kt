package com.learnwithchampak.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min

class BrowserActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_URL = "com.learnwithchampak.tv.EXTRA_URL"
    const val EXTRA_TITLE = "com.learnwithchampak.tv.EXTRA_TITLE"
    private const val HOME_URL = "https://www.learnwithchampak.live"
    private const val APK_URL = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv.apk"
    private const val PREFS = "champak_tabs_prefs"
    private const val KEY_DEFAULT_ASKED = "default_asked_browser"
    private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
  }

  private data class BrowserTab(val webView: WebView, var title: String, var url: String)

  private lateinit var root: LinearLayout
  private lateinit var header: LinearLayout
  private lateinit var tabStrip: LinearLayout
  private lateinit var webHolder: FrameLayout
  private lateinit var pointer: TextView
  private lateinit var addressBar: EditText
  private lateinit var titleText: TextView
  private lateinit var prefs: SharedPreferences

  private val tabs = mutableListOf<BrowserTab>()
  private var currentIndex = -1
  private var fullScreen = false
  private var pointerX = 0f
  private var pointerY = 0f
  private var longPressMenuShown = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (resources.configuration.screenWidthDp >= 700) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildUi()
    val startUrl = intent?.data?.toString().orEmpty().ifBlank { intent.getStringExtra(EXTRA_URL).orEmpty() }.ifBlank { HOME_URL }
    newTab(startUrl)
    askDefaultBrowserOnFirstRun()
  }

  override fun onDestroy() {
    tabs.forEach { it.webView.destroy() }
    super.onDestroy()
  }

  private fun buildUi() {
    root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(3, 15, 34))
    }

    header = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(8), dp(6), dp(8), dp(6))
      setBackgroundColor(Color.rgb(5, 62, 112))
    }
    root.addView(header, LinearLayout.LayoutParams(-1, -2))

    val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(top, LinearLayout.LayoutParams(-1, dp(42)))
    titleText = TextView(this).apply {
      text = "Learn With Champak Browser"
      setTextColor(Color.WHITE)
      textSize = 15f
      maxLines = 1
      gravity = Gravity.CENTER_VERTICAL
    }
    top.addView(titleText, LinearLayout.LayoutParams(0, -1, 1f))
    top.addView(btn("▦", "Return to Buttons") { returnToButtons() })
    top.addView(btn("＋", "New tab") { newTab(HOME_URL) })
    top.addView(btn("▤", "Tabs") { showTabs() })
    top.addView(btn("×", "Close tab") { closeCurrentTab() })
    top.addView(btn("⛶", "Full screen") { setFullScreenMode(true, true) })

    val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(nav, LinearLayout.LayoutParams(-1, dp(44)))
    nav.addView(btn("←", "Back") { goBackOrClose() })
    nav.addView(btn("→", "Forward") { activeWebView()?.let { if (it.canGoForward()) it.goForward() } })
    nav.addView(btn("↻", "Reload") { activeWebView()?.reload() })
    nav.addView(btn("⌂", "Home") { loadInCurrent(HOME_URL) })
    addressBar = EditText(this).apply {
      hint = "Type website or search"
      setSingleLine(true)
      textSize = 14f
      setTextColor(Color.rgb(3, 44, 84))
      setBackgroundColor(Color.WHITE)
      imeOptions = EditorInfo.IME_ACTION_GO
      setOnFocusChangeListener { _, hasFocus ->
        pointer.visibility = if (hasFocus) View.GONE else View.VISIBLE
        if (hasFocus) showKeyboard()
      }
      setOnClickListener { showKeyboard() }
      setOnEditorActionListener { _, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
          loadInCurrent(text.toString())
          hideKeyboardAndFocusPage()
          true
        } else false
      }
    }
    nav.addView(addressBar, LinearLayout.LayoutParams(0, dp(38), 1f))
    nav.addView(btn("▶", "Go") { loadInCurrent(addressBar.text.toString()); hideKeyboardAndFocusPage() })

    val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    header.addView(tools, LinearLayout.LayoutParams(-1, dp(38)))
    tools.addView(btn("Buttons", "Return to first buttons screen") { returnToButtons() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Page", "Focus web page") { focusWebPage() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Keys", "Open keyboard") { focusAddressBar() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Google", "Google sign-in outside") { openGoogleSignIn() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Outside", "Open outside") { openOutside(activeTab()?.url ?: HOME_URL) }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Update", "Update app") { openOutside(APK_URL) }, LinearLayout.LayoutParams(0, -1, 1f))

    tabStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(2, 35, 70)) }
    header.addView(tabStrip, LinearLayout.LayoutParams(-1, dp(34)))

    webHolder = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    pointer = TextView(this).apply {
      text = "●"
      textSize = 34f
      gravity = Gravity.CENTER
      setTextColor(Color.rgb(255, 235, 59))
      setShadowLayer(8f, 0f, 0f, Color.BLACK)
      visibility = View.VISIBLE
      isFocusable = false
      isClickable = false
      elevation = dp(20).toFloat()
    }
    webHolder.setOnClickListener { focusWebPage() }
    webHolder.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
        pointerX = event.x
        pointerY = event.y
        updatePointerPosition()
        focusWebPage()
      }
      false
    }
    root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(root)
    webHolder.post { centerPointer() }
  }

  private fun btn(text: String, desc: String, action: () -> Unit): Button =
    btn(text, desc, action, LinearLayout.LayoutParams(dp(48), -1))

  private fun btn(text: String, desc: String, action: () -> Unit, lp: LinearLayout.LayoutParams): Button {
    return Button(this).apply {
      this.text = text
      contentDescription = desc
      textSize = 12f
      isAllCaps = false
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.rgb(7, 91, 156))
      setOnClickListener { action() }
      layoutParams = lp
      isFocusable = true
    }
  }

  private fun newWebView(): WebView {
    return WebView(this).apply {
      isFocusable = true
      isFocusableInTouchMode = true
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.databaseEnabled = true
      settings.loadsImagesAutomatically = true
      settings.loadWithOverviewMode = true
      settings.useWideViewPort = true
      settings.builtInZoomControls = true
      settings.displayZoomControls = false
      settings.cacheMode = WebSettings.LOAD_DEFAULT
      settings.mediaPlaybackRequiresUserGesture = false
      settings.userAgentString = DESKTOP_USER_AGENT
      CookieManager.getInstance().setAcceptCookie(true)
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
      webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
          activeTabFor(view)?.title = title.ifBlank { activeTabFor(view)?.url ?: "Page" }
          if (view == activeWebView()) titleText.text = title.ifBlank { activeTab()?.url ?: "Page" }
          refreshTabs()
        }
      }
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          val url = request.url.toString()
          if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:")) {
            openOutside(url)
            return true
          }
          return false
        }
        override fun onPageFinished(view: WebView, url: String) {
          activeTabFor(view)?.url = url
          if (view == activeWebView()) addressBar.setText(if (url == "about:blank") "" else url)
          refreshTabs()
          webHolder.post { ensurePointerVisible() }
        }
      }
    }
  }

  private fun newTab(url: String) {
    val web = newWebView()
    val tab = BrowserTab(web, "New Tab", normalizeUrl(url))
    tabs.add(tab)
    switchTo(tabs.lastIndex)
    web.loadUrl(tab.url)
    focusWebPage()
  }

  private fun switchTo(index: Int) {
    if (index !in tabs.indices) return
    currentIndex = index
    webHolder.removeAllViews()
    webHolder.addView(tabs[index].webView, FrameLayout.LayoutParams(-1, -1))
    addPointerOverlay()
    titleText.text = tabs[index].title
    addressBar.setText(if (tabs[index].url == "about:blank") "" else tabs[index].url)
    refreshTabs()
    webHolder.post { ensurePointerVisible() }
  }

  private fun addPointerOverlay() {
    val size = dp(54)
    if (pointer.parent != null) (pointer.parent as? FrameLayout)?.removeView(pointer)
    webHolder.addView(pointer, FrameLayout.LayoutParams(size, size))
    pointer.bringToFront()
  }

  private fun closeCurrentTab() {
    if (tabs.size <= 1) {
      loadInCurrent("about:blank")
      return
    }
    val old = tabs.removeAt(currentIndex)
    old.webView.destroy()
    switchTo(currentIndex.coerceAtMost(tabs.lastIndex))
  }

  private fun refreshTabs() {
    tabStrip.removeAllViews()
    tabs.forEachIndexed { i, tab ->
      val label = if (i == currentIndex) "● ${i + 1}: ${tab.title.take(18)}" else "${i + 1}: ${tab.title.take(18)}"
      tabStrip.addView(btn(label, "Tab ${i + 1}") { switchTo(i) }, LinearLayout.LayoutParams(0, -1, 1f))
    }
  }

  private fun showTabs() {
    val labels = tabs.mapIndexed { i, tab -> "${i + 1}. ${tab.title}\n${tab.url}" }.toTypedArray()
    AlertDialog.Builder(this)
      .setTitle("Open Tabs")
      .setItems(labels) { _, which -> switchTo(which) }
      .setPositiveButton("New Tab") { _, _ -> newTab(HOME_URL) }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun loadInCurrent(input: String) {
    val url = normalizeUrl(input)
    activeTab()?.url = url
    addressBar.setText(if (url == "about:blank") "" else url)
    activeWebView()?.settings?.userAgentString = DESKTOP_USER_AGENT
    activeWebView()?.loadUrl(url)
  }

  private fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "about:blank"
    if (value.startsWith("http://") || value.startsWith("https://") || value == "about:blank") return value
    if (value.startsWith("www.") || (value.contains(".") && !value.contains(" "))) return "https://$value"
    return "https://www.google.com/search?q=${URLEncoder.encode(value, "UTF-8")}"
  }

  private fun activeTab(): BrowserTab? = tabs.getOrNull(currentIndex)
  private fun activeWebView(): WebView? = activeTab()?.webView
  private fun activeTabFor(view: WebView): BrowserTab? = tabs.firstOrNull { it.webView == view }

  private fun openGoogleSignIn() {
    openOutside("https://accounts.google.com/signin")
  }

  private fun openOutside(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
      Toast.makeText(this, "No outside browser/app found", Toast.LENGTH_LONG).show()
    }
  }

  private fun askDefaultBrowserOnFirstRun() {
    if (prefs.getBoolean(KEY_DEFAULT_ASKED, false)) return
    prefs.edit().putBoolean(KEY_DEFAULT_ASKED, true).apply()
    AlertDialog.Builder(this)
      .setTitle("Use as default browser?")
      .setMessage("Set Learn With Champak as a browser choice for links on this Android phone or TV.")
      .setPositiveButton("Open Settings") { _, _ -> openDefaultBrowserSettings() }
      .setNegativeButton("Later", null)
      .show()
  }

  private fun openDefaultBrowserSettings() {
    try {
      startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    } catch (_: Exception) {
      try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) { }
    }
  }

  private fun setFullScreenMode(enabled: Boolean, focusPageAfterToggle: Boolean) {
    fullScreen = enabled
    header.visibility = if (enabled) View.GONE else View.VISIBLE
    window.decorView.systemUiVisibility = if (enabled) {
      View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    } else {
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
    if (focusPageAfterToggle) {
      webHolder.post {
        ensurePointerVisible()
        focusWebPage()
      }
    }
    if (enabled) Toast.makeText(this, "Pointer active. Back exits full screen. Long-press OK opens menu.", Toast.LENGTH_LONG).show()
  }

  private fun focusAddressBar() {
    setFullScreenMode(false, false)
    addressBar.requestFocus()
    showKeyboard()
  }

  private fun showKeyboard() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun hideKeyboardAndFocusPage() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(addressBar.windowToken, 0)
    focusWebPage()
  }

  private fun focusWebPage() {
    addressBar.clearFocus()
    activeWebView()?.requestFocus()
    ensurePointerVisible()
  }

  private fun ensurePointerVisible() {
    if (!::pointer.isInitialized || pointer.parent == null) return
    if (pointerX <= 0f || pointerY <= 0f) centerPointer()
    clampPointer()
    pointer.visibility = View.VISIBLE
    pointer.bringToFront()
    updatePointerPosition()
  }

  private fun centerPointer() {
    pointerX = (webHolder.width / 2f).coerceAtLeast(dp(80).toFloat())
    pointerY = (webHolder.height / 2f).coerceAtLeast(dp(80).toFloat())
    updatePointerPosition()
  }

  private fun clampPointer() {
    val half = dp(27).toFloat()
    val maxX = max(half, webHolder.width.toFloat() - half)
    val maxY = max(half, webHolder.height.toFloat() - half)
    pointerX = min(max(pointerX, half), maxX)
    pointerY = min(max(pointerY, half), maxY)
  }

  private fun updatePointerPosition() {
    if (!::pointer.isInitialized) return
    clampPointer()
    val half = dp(27).toFloat()
    pointer.x = pointerX - half
    pointer.y = pointerY - half
  }

  private fun movePointer(dx: Float, dy: Float) {
    ensurePointerVisible()
    pointerX += dx
    pointerY += dy
    updatePointerPosition()
    autoScrollAtEdges(dx, dy)
  }

  private fun autoScrollAtEdges(dx: Float, dy: Float) {
    val edge = dp(42)
    val scroll = dp(260)
    when {
      dy > 0 && pointerY >= webHolder.height - edge -> performPageScroll(scroll)
      dy < 0 && pointerY <= edge -> performPageScroll(-scroll)
      dx > 0 && pointerX >= webHolder.width - edge -> activeWebView()?.scrollBy(dp(180), 0)
      dx < 0 && pointerX <= edge -> activeWebView()?.scrollBy(-dp(180), 0)
    }
  }

  private fun performPageScroll(amount: Int) {
    activeWebView()?.let { web ->
      web.scrollBy(0, amount)
      val js = """
        (function(){
          var amount = $amount;
          var el = document.scrollingElement || document.documentElement || document.body;
          if (el) { el.scrollBy({top: amount, left: 0, behavior: 'smooth'}); }
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
      web.evaluateJavascript(js, null)
    }
  }

  private fun clickAtPointer() {
    val web = activeWebView() ?: return
    focusWebPage()
    val x = pointerX.coerceIn(1f, max(1f, web.width - 1f))
    val y = pointerY.coerceIn(1f, max(1f, web.height - 1f))
    val now = System.currentTimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0)
    web.dispatchTouchEvent(down)
    web.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun showBrowserCommandMenu() {
    longPressMenuShown = true
    val options = arrayOf(
      "Return to Buttons / First Screen",
      if (fullScreen) "Show Controls" else "Full Screen Web Page",
      "Focus Web Page / Pointer",
      "Open Keyboard / Address Bar",
      "Back in Web Page",
      "Home",
      "Open Outside",
      "Exit Browser",
      "Cancel"
    )
    AlertDialog.Builder(this)
      .setTitle("Browser Menu")
      .setItems(options) { _, which ->
        longPressMenuShown = false
        when (which) {
          0 -> returnToButtons()
          1 -> setFullScreenMode(!fullScreen, true)
          2 -> focusWebPage()
          3 -> focusAddressBar()
          4 -> goBackOrClose()
          5 -> loadInCurrent(HOME_URL)
          6 -> openOutside(activeTab()?.url ?: HOME_URL)
          7 -> finish()
        }
      }
      .setOnCancelListener { longPressMenuShown = false }
      .show()
  }

  private fun goBackOrClose() {
    if (fullScreen) {
      setFullScreenMode(false, true)
      return
    }
    activeWebView()?.let {
      if (it.canGoBack()) {
        it.goBack()
        return
      }
    }
    returnToButtons()
  }

  private fun returnToButtons() {
    setFullScreenMode(false, false)
    finish()
  }

  @Deprecated("Deprecated in Java")
  override fun onBackPressed() {
    goBackOrClose()
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_DOWN) {
      showBrowserCommandMenu()
      return true
    }

    if (addressBar.hasFocus()) {
      if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
        hideKeyboardAndFocusPage()
        return true
      }
      return super.dispatchKeyEvent(event)
    }

    if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      longPressMenuShown = false
      return true
    }

    if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

    when (event.keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> {
        movePointer(0f, -dp(28).toFloat())
        return true
      }
      KeyEvent.KEYCODE_DPAD_DOWN -> {
        movePointer(0f, dp(28).toFloat())
        return true
      }
      KeyEvent.KEYCODE_DPAD_LEFT -> {
        movePointer(-dp(28).toFloat(), 0f)
        return true
      }
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        movePointer(dp(28).toFloat(), 0f)
        return true
      }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        if (event.repeatCount >= 6 && !longPressMenuShown) {
          showBrowserCommandMenu()
        } else if (event.repeatCount == 0) {
          clickAtPointer()
        }
        return true
      }
      KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
        goBackOrClose()
        return true
      }
      KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_GUIDE -> {
        returnToButtons()
        return true
      }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
