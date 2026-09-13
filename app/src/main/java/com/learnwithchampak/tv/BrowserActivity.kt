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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
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
import java.util.Locale

class BrowserActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_URL = "com.learnwithchampak.tv.EXTRA_URL"
    const val EXTRA_TITLE = "com.learnwithchampak.tv.EXTRA_TITLE"
    private const val PREFS = "champak_browser_prefs"
    private const val KEY_DEFAULT_ASKED = "default_browser_asked_v27"
    private const val HOME_URL = "https://www.learnwithchampak.live"
    private const val APK_URL = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv.apk?v=2.7"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
  }

  private lateinit var prefs: SharedPreferences
  private lateinit var web: WebView
  private lateinit var address: EditText
  private lateinit var title: TextView
  private lateinit var status: TextView
  private lateinit var progress: ProgressBar
  private lateinit var bar: LinearLayout
  private var full = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (resources.configuration.screenWidthDp >= 700) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildUi(intent.getStringExtra(EXTRA_TITLE) ?: "Learn With Champak Browser")
    setupWeb()
    val start = intent?.data?.toString().orEmpty().ifBlank { intent.getStringExtra(EXTRA_URL).orEmpty() }.ifBlank { HOME_URL }
    load(start)
    askDefaultOnFirstRun()
  }

  private fun buildUi(startTitle: String) {
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(3, 15, 34)) }
    bar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(6), dp(8), dp(6)); background = grad() }
    root.addView(bar, LinearLayout.LayoutParams(-1, dp(if (resources.configuration.screenWidthDp < 700) 178 else 132)))

    val top = row(); bar.addView(top, LinearLayout.LayoutParams(-1, dp(38)))
    title = TextView(this).apply { text = startTitle; textSize = 16f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER_VERTICAL; maxLines = 1 }
    top.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
    top.addView(btn("Google") { openExternal("https://accounts.google.com/") })
    top.addView(btn("Default") { openDefaultSettings() })
    top.addView(btn("Update") { updateApp() })

    val mid = row(); bar.addView(mid, LinearLayout.LayoutParams(-1, dp(46)))
    mid.addView(btn("Back") { if (web.canGoBack()) web.goBack() else finish() })
    mid.addView(btn("Fwd") { if (web.canGoForward()) web.goForward() })
    mid.addView(btn("Reload") { web.reload() })
    mid.addView(btn("Home") { load(HOME_URL) })
    address = EditText(this).apply {
      hint = "Type website or search"
      setSingleLine(true)
      textSize = 14f
      setTextColor(Color.rgb(3, 44, 84))
      background = round(Color.WHITE, dp(10), 0, 0)
      setPadding(dp(10), 0, dp(10), 0)
      imeOptions = EditorInfo.IME_ACTION_GO
      setOnEditorActionListener { _, actionId, event -> if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) { load(text.toString()); true } else false }
      setOnFocusChangeListener { _, hasFocus -> if (hasFocus) postDelayed({ keyboard() }, 100) }
    }
    mid.addView(address, LinearLayout.LayoutParams(0, dp(38), 1f))
    mid.addView(btn("Go") { load(address.text.toString()) })

    val low = row(); bar.addView(low, LinearLayout.LayoutParams(-1, dp(38)))
    low.addView(btn("Outside") { openExternal(web.url ?: HOME_URL) })
    low.addView(btn("Chrome") { openChrome(web.url ?: HOME_URL) })
    low.addView(btn("Full") { setFull(true) })
    low.addView(btn("Keyboard") { address.requestFocus(); keyboard() })
    low.addView(TextView(this).apply { text = "Google login: use Google / Chrome / Outside if blocked"; setTextColor(Color.rgb(255, 221, 128)); textSize = 12f; gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, -1, 1f))

    progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
    root.addView(progress, LinearLayout.LayoutParams(-1, dp(4)))
    web = WebView(this).apply { setBackgroundColor(Color.WHITE); isFocusable = true; isFocusableInTouchMode = true }
    root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
    status = TextView(this).apply { text = "Ready"; setTextColor(Color.rgb(218, 240, 255)); textSize = 12f; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), 0, dp(10), 0); background = round(Color.rgb(4, 24, 54), 0, 0, 0) }
    root.addView(status, LinearLayout.LayoutParams(-1, dp(30)))
    setContentView(root)
  }

  private fun setupWeb() {
    WebView.setWebContentsDebuggingEnabled(true)
    CookieManager.getInstance().setAcceptCookie(true)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
    web.settings.apply {
      javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true; loadsImagesAutomatically = true
      loadWithOverviewMode = true; useWideViewPort = true; builtInZoomControls = true; displayZoomControls = false
      cacheMode = WebSettings.LOAD_DEFAULT; mediaPlaybackRequiresUserGesture = false; allowFileAccess = true; allowContentAccess = true
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE; userAgentString = UA
    }
    web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ -> download(url, userAgent, contentDisposition, mimeType) }
    web.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = route(request.url.toString())
      @Suppress("DEPRECATION") override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = route(url)
      override fun onPageFinished(view: WebView, url: String) { title.text = view.title ?: url; address.setText(if (url == "about:blank") "" else url); progress.progress = 0; status.text = if (url.contains("accounts.google.com")) "Google page detected. Use Google/Chrome/Outside if sign-in is blocked." else "Ready" }
    }
    web.webChromeClient = object : WebChromeClient() { override fun onProgressChanged(view: WebView, p: Int) { progress.progress = if (p >= 100) 0 else p } }
  }

  private fun route(url: String): Boolean {
    val u = url.lowercase(Locale.ROOT)
    if (u.endsWith(".apk")) { updateApp(); return true }
    if (u.startsWith("tel:") || u.startsWith("mailto:") || u.startsWith("whatsapp:")) { openExternal(url); return true }
    web.loadUrl(url); return true
  }

  private fun load(input: String) {
    val v = input.trim()
    val url = when {
      v.isEmpty() -> "about:blank"
      v.startsWith("http://") || v.startsWith("https://") || v == "about:blank" -> v
      v.startsWith("www.") || (v.contains('.') && !v.contains(' ')) -> "https://$v"
      else -> "https://www.google.com/search?q=${URLEncoder.encode(v, "UTF-8")}"
    }
    address.setText(if (url == "about:blank") "" else url); status.text = "Opening $url"; web.loadUrl(url)
  }

  private fun askDefaultOnFirstRun() {
    if (prefs.getBoolean(KEY_DEFAULT_ASKED, false)) return
    prefs.edit().putBoolean(KEY_DEFAULT_ASKED, true).apply()
    AlertDialog.Builder(this).setTitle("Set as default browser?").setMessage("Choose Learn With Champak as the browser for web links from Android Default apps. Google sign-in may still need Chrome/System browser, so Google and Chrome buttons are included.").setPositiveButton("Open Default Apps") { _, _ -> openDefaultSettings() }.setNegativeButton("Later", null).show()
  }

  private fun openDefaultSettings() { try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
  private fun openChrome(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.android.chrome") }) } catch (_: Exception) { openExternal(url) } }
  private fun openExternal(url: String) { try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { Toast.makeText(this, "No app found for this link", Toast.LENGTH_LONG).show() } }
  private fun updateApp() { download(APK_URL, UA, "", "application/vnd.android.package-archive") }
  private fun download(url: String, userAgent: String, contentDisposition: String, mimeType: String) { try { val name = URLUtil.guessFileName(url, contentDisposition, mimeType); val req = DownloadManager.Request(Uri.parse(url)).apply { setMimeType(mimeType); addRequestHeader("User-Agent", userAgent); setTitle(name); setDescription("Learn With Champak update"); setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name); allowScanningByMediaScanner() }; (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req); Toast.makeText(this, "Download started. Open Downloads to install.", Toast.LENGTH_LONG).show() } catch (_: Exception) { openExternal(url) } }
  private fun keyboard() { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT) }
  private fun setFull(enabled: Boolean) { full = enabled; bar.visibility = if (enabled) View.GONE else View.VISIBLE; progress.visibility = if (enabled) View.GONE else View.VISIBLE; status.visibility = if (enabled) View.GONE else View.VISIBLE; window.decorView.systemUiVisibility = if (enabled) View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY else View.SYSTEM_UI_FLAG_LAYOUT_STABLE }
  override fun onBackPressed() { if (full) setFull(false) else if (web.canGoBack()) web.goBack() else super.onBackPressed() }
  private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(4)) }
  private fun btn(text: String, action: () -> Unit) = Button(this).apply { this.text = text; textSize = 12f; isAllCaps = false; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; background = round(Color.rgb(8, 77, 138), dp(10), Color.rgb(82, 204, 255), dp(1)); setOnClickListener { action() } }
  private fun grad() = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(2, 31, 69), Color.rgb(6, 85, 145)))
  private fun round(c: Int, r: Int, s: Int, w: Int) = GradientDrawable().apply { setColor(c); cornerRadius = r.toFloat(); if (w > 0) setStroke(w, s) }
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
