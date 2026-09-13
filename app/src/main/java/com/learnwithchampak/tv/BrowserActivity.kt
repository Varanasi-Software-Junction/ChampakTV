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
import java.util.Locale

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
  private lateinit var addressBar: EditText
  private lateinit var titleText: TextView
  private lateinit var prefs: SharedPreferences
  private val tabs = mutableListOf<BrowserTab>()
  private var currentIndex = -1
  private var fullScreen = false

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
    root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(3, 15, 34)) }
    header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)); setBackgroundColor(Color.rgb(5, 62, 112)) }
    root.addView(header, LinearLayout.LayoutParams(-1, -2))

    val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(top, LinearLayout.LayoutParams(-1, dp(42)))
    titleText = TextView(this).apply { text = "Learn With Champak Browser"; setTextColor(Color.WHITE); textSize = 15f; maxLines = 1 }
    top.addView(titleText, LinearLayout.LayoutParams(0, -1, 1f))
    top.addView(btn("＋", "New tab") { newTab(HOME_URL) })
    top.addView(btn("▤", "Tabs") { showTabs() })
    top.addView(btn("×", "Close tab") { closeCurrentTab() })
    top.addView(btn("⛶", "Full") { setFullScreenMode(true) })

    val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(nav, LinearLayout.LayoutParams(-1, dp(44)))
    nav.addView(btn("←", "Back") { activeWebView()?.let { if (it.canGoBack()) it.goBack() else finish() } })
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
      setOnEditorActionListener { _, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) { loadInCurrent(text.toString()); true } else false
      }
    }
    nav.addView(addressBar, LinearLayout.LayoutParams(0, dp(38), 1f))
    nav.addView(btn("▶", "Go") { loadInCurrent(addressBar.text.toString()) })

    val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    header.addView(tools, LinearLayout.LayoutParams(-1, dp(38)))
    tools.addView(btn("Google", "Google sign-in outside") { openGoogleSignIn() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Outside", "Open outside") { openOutside(activeTab()?.url ?: HOME_URL) }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Default", "Set default browser") { openDefaultBrowserSettings() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Update", "Update app") { openOutside(APK_URL) }, LinearLayout.LayoutParams(0, -1, 1f))

    tabStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(2, 35, 70)) }
    header.addView(tabStrip, LinearLayout.LayoutParams(-1, dp(34)))

    webHolder = FrameLayout(this)
    root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))
    setContentView(root)
  }

  private fun btn(text: String, desc: String, action: () -> Unit): Button = btn(text, desc, action, LinearLayout.LayoutParams(dp(48), -1))

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
    }
  }

  private fun newWebView(): WebView {
    return WebView(this).apply {
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
          activeTabFor(view)?.title = title
          if (view == activeWebView()) titleText.text = title
          refreshTabs()
        }
      }
      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          val url = request.url.toString()
          if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:")) { openOutside(url); return true }
          if (url.contains("accounts.google.com") || url.contains("/signin") || url.contains("ServiceLogin")) {
            Toast.makeText(this@BrowserActivity, "Opening Google sign-in in system browser", Toast.LENGTH_LONG).show()
            openOutside(url)
            return true
          }
          return false
        }
        override fun onPageFinished(view: WebView, url: String) {
          activeTabFor(view)?.url = url
          if (view == activeWebView()) addressBar.setText(url)
          refreshTabs()
        }
      }
    }
  }

  private fun newTab(url: String) {
    val web = newWebView()
    val tab = BrowserTab(web, "New Tab", url)
    tabs.add(tab)
    switchTo(tabs.lastIndex)
    web.loadUrl(normalizeUrl(url))
  }

  private fun switchTo(index: Int) {
    if (index !in tabs.indices) return
    currentIndex = index
    webHolder.removeAllViews()
    webHolder.addView(tabs[index].webView, FrameLayout.LayoutParams(-1, -1))
    titleText.text = tabs[index].title
    addressBar.setText(tabs[index].url)
    refreshTabs()
  }

  private fun closeCurrentTab() {
    if (tabs.size <= 1) { loadInCurrent("about:blank"); return }
    val old = tabs.removeAt(currentIndex)
    webHolder.removeView(old.webView)
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
    AlertDialog.Builder(this).setTitle("Open Tabs").setItems(labels) { _, which -> switchTo(which) }.setPositiveButton("New Tab") { _, _ -> newTab(HOME_URL) }.setNegativeButton("Close", null).show()
  }

  private fun loadInCurrent(input: String) {
    val url = normalizeUrl(input)
    activeTab()?.url = url
    addressBar.setText(if (url == "about:blank") "" else url)
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
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { Toast.makeText(this, "No outside browser/app found", Toast.LENGTH_LONG).show() }
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

  private fun setFullScreenMode(enabled: Boolean) {
    fullScreen = enabled
    header.visibility = if (enabled) View.GONE else View.VISIBLE
    window.decorView.systemUiVisibility = if (enabled) {
      View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    } else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    if (enabled) Toast.makeText(this, "Back exits full screen", Toast.LENGTH_SHORT).show()
  }

  override fun onBackPressed() {
    if (fullScreen) { setFullScreenMode(false); return }
    activeWebView()?.let { if (it.canGoBack()) { it.goBack(); return } }
    super.onBackPressed()
  }

  private fun showKeyboard() {
    addressBar.requestFocus()
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
