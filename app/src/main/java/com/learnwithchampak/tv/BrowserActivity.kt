package com.learnwithchampak.tv

import android.app.AlertDialog
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class BrowserActivity : AppCompatActivity() {
  companion object {
    const val EXTRA_URL = "com.learnwithchampak.tv.EXTRA_URL"
    const val EXTRA_TITLE = "com.learnwithchampak.tv.EXTRA_TITLE"
    const val EXTRA_TIMED_OPEN = "com.learnwithchampak.tv.EXTRA_TIMED_OPEN"
    const val EXTRA_START_FRESH = "com.learnwithchampak.tv.EXTRA_START_FRESH"
    const val EXTRA_REOPEN_ALL_TABS = "com.learnwithchampak.tv.EXTRA_REOPEN_ALL_TABS"
    private const val HOME_URL = "https://www.learnwithchampak.live"
    private const val INSIDE_KASHI_URL = "https://insidekashi.com"
    private const val YOUTUBE_URL = "https://youtube.com/@champaksworld"
    private const val WHATSAPP_URL = "https://web.whatsapp.com"
    private const val GOOGLE_ACCOUNT_URL = "https://myaccount.google.com/"
    private const val GMAIL_URL = "https://mail.google.com/mail/u/0/"
    private const val GOOGLE_SIGN_IN_URL = "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fmyaccount.google.com%2F&hl=en"
    private const val GOOGLE_GMAIL_SIGN_IN_URL = "https://accounts.google.com/AccountChooser?continue=https%3A%2F%2Fmail.google.com%2Fmail%2Fu%2F0%2F&hl=en"
    private const val GOOGLE_HOME_URL = "https://www.google.com"
    private const val GITHUB_CODE_URL = "https://github.com/Programmer-s-Picnic/json-images/tree/main/windows_app"
    private const val PRIVACY_URL = "https://programmer-s-picnic.github.io/json-images/tv/privacy-policy.html"
    private const val PREFS = "champak_tabs_prefs"
    private const val KEY_DEFAULT_ASKED = "default_asked_browser"
    private const val KEY_BOOKMARKS = "browser_bookmarks"
    private const val KEY_HISTORY = "browser_history"
    private const val KEY_OPEN_TABS = "browser_open_tabs"
    private const val KEY_PRIVATE_TABS = "browser_private_tabs"
    private const val KEY_CURRENT_TAB = "browser_current_tab"
    private const val KEY_LAST_DOWNLOAD_ID = "browser_last_download_id"
    private const val KEY_SEARCH_ENGINE = "browser_search_engine"
    private const val KEY_TEXT_ZOOM = "browser_text_zoom"
    private const val KEY_DESKTOP_MODE = "browser_desktop_mode"
    private const val KEY_ROTATION_SECONDS = "browser_rotation_seconds"
    private const val KEY_QUICK_LINKS = "browser_quick_links"
    private const val KEY_WEATHER_CITY = "browser_weather_city"
    private const val KEY_WEATHER_LAT = "browser_weather_lat"
    private const val KEY_WEATHER_LON = "browser_weather_lon"
    private const val MAX_HISTORY = 80
    private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
  }

  private data class BrowserTab(
    val webView: WebView,
    var title: String,
    var url: String,
    var privacyBlur: Boolean = false
  )

  private lateinit var screen: FrameLayout
  private lateinit var root: LinearLayout
  private lateinit var header: LinearLayout
  private lateinit var tabStrip: LinearLayout
  private lateinit var webHolder: FrameLayout
  private lateinit var privacyOverlay: TextView
  private lateinit var pointer: TextView
  private lateinit var addressBar: AutoCompleteTextView
  private lateinit var titleText: TextView
  private lateinit var rotationButton: Button
  private lateinit var navBar: LinearLayout
  private lateinit var clockTimeText: TextView
  private lateinit var clockDateText: TextView
  private lateinit var weatherText: TextView
  private lateinit var statusText: TextView
  private lateinit var prefs: SharedPreferences

  private val tabs = mutableListOf<BrowserTab>()
  private val rotationHandler = Handler(Looper.getMainLooper())
  private val chromeHandler = Handler(Looper.getMainLooper())
  private var currentIndex = -1
  private var fullScreen = false
  private var pointerX = 0f
  private var pointerY = 0f
  private var longPressMenuShown = false
  private var restoringSession = false
  private var windowHasFocus = true
  private var rotationActive = false
  private var rotationPaused = false
  private var rotationDeadlineMs = 0L
  private var rotationSeconds = 30
  private var desktopMode = false
  private var textZoom = 100
  private var weatherLoading = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    rotationSeconds = prefs.getInt(KEY_ROTATION_SECONDS, 30).coerceIn(5, 300)
    desktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false)
    textZoom = prefs.getInt(KEY_TEXT_ZOOM, 100).coerceIn(75, 200)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().flush()
    if (resources.configuration.screenWidthDp >= 700) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildUi()
    startChromeClock()
    refreshWeather()
    val requestedUrl = intent?.data?.toString().orEmpty().ifBlank { intent.getStringExtra(EXTRA_URL).orEmpty() }
    val timedOpen = intent?.getBooleanExtra(EXTRA_TIMED_OPEN, false) == true
    val startFresh = intent?.getBooleanExtra(EXTRA_START_FRESH, false) == true
    val reopenAllTabs = intent?.getBooleanExtra(EXTRA_REOPEN_ALL_TABS, false) == true

    when {
      timedOpen && requestedUrl.isNotBlank() -> restoreSessionSilentlyAndOpen(requestedUrl)
      reopenAllTabs -> restoreSavedSessionSilently()
      startFresh -> {
        clearSavedSession()
        newTab(requestedUrl.ifBlank { "about:blank" })
      }
      else -> restorePreviousSessionOrStartFresh(requestedUrl.takeIf { it.isNotBlank() })
    }

    TimedSiteScheduler.scheduleNext(this)
    askDefaultBrowserOnFirstRun()
  }

  override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    if (intent == null) return
    setIntent(intent)
    val requestedUrl = intent.data?.toString().orEmpty().ifBlank { intent.getStringExtra(EXTRA_URL).orEmpty() }
    if (requestedUrl.isNotBlank()) {
      newTab(requestedUrl)
      if (intent.getBooleanExtra(EXTRA_TIMED_OPEN, false)) {
        Toast.makeText(this, "Timed site opened automatically", Toast.LENGTH_SHORT).show()
        TimedSiteScheduler.scheduleNext(this)
      }
    }
  }

  override fun onPause() {
    saveOpenTabs()
    CookieManager.getInstance().flush()
    super.onPause()
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    windowHasFocus = hasFocus
    applyPrivacyState()
  }

  override fun onDestroy() {
    rotationHandler.removeCallbacksAndMessages(null)
    chromeHandler.removeCallbacksAndMessages(null)
    saveOpenTabs()
    CookieManager.getInstance().flush()
    tabs.forEach { it.webView.destroy() }
    super.onDestroy()
  }

  private fun savedSessionUrls(): List<String> {
    return prefs.getString(KEY_OPEN_TABS, "").orEmpty()
      .lines()
      .map { it.trim() }
      .filter { it.isNotEmpty() }
  }

  private fun savedSessionPrivacy(): List<Boolean> {
    return prefs.getString(KEY_PRIVATE_TABS, "").orEmpty()
      .lines()
      .filter { it.isNotEmpty() }
      .map { it == "1" }
  }

  private fun saveOpenTabs() {
    if (restoringSession || tabs.isEmpty()) return
    prefs.edit()
      .putString(KEY_OPEN_TABS, tabs.joinToString("\n") { it.url.ifBlank { "about:blank" } })
      .putString(KEY_PRIVATE_TABS, tabs.joinToString("\n") { if (it.privacyBlur) "1" else "0" })
      .putInt(KEY_CURRENT_TAB, currentIndex.coerceAtLeast(0))
      .apply()
  }

  private fun clearSavedSession() {
    prefs.edit()
      .remove(KEY_OPEN_TABS)
      .remove(KEY_PRIVATE_TABS)
      .remove(KEY_CURRENT_TAB)
      .apply()
  }

  private fun restoreSavedSessionSilently() {
    val savedUrls = savedSessionUrls()
    if (savedUrls.isEmpty()) {
      newTab("about:blank")
      Toast.makeText(this, "No saved tabs found", Toast.LENGTH_SHORT).show()
      return
    }

    val savedCurrent = prefs.getInt(KEY_CURRENT_TAB, 0)
    val savedPrivacy = savedSessionPrivacy()
    restoringSession = true
    try {
      savedUrls.forEachIndexed { index, url -> newTab(url, savedPrivacy.getOrElse(index) { false }) }
      if (tabs.isNotEmpty()) switchTo(savedCurrent.coerceIn(0, tabs.lastIndex))
    } finally {
      restoringSession = false
    }

    saveOpenTabs()
    Toast.makeText(this, "All saved tabs reopened", Toast.LENGTH_SHORT).show()
  }

  private fun restoreSessionSilentlyAndOpen(requestedUrl: String) {
    val savedUrls = savedSessionUrls()
    val savedPrivacy = savedSessionPrivacy()
    val target = normalizeUrl(requestedUrl)

    restoringSession = true
    try {
      savedUrls.forEachIndexed { index, url ->
        if (url.isNotBlank()) newTab(url, savedPrivacy.getOrElse(index) { false })
      }
      val existing = tabs.indexOfFirst { it.url == target }
      if (existing >= 0) {
        switchTo(existing)
      } else {
        newTab(target)
      }
    } finally {
      restoringSession = false
    }

    saveOpenTabs()
    Toast.makeText(this, "Timed site opened automatically", Toast.LENGTH_SHORT).show()
  }

  private fun restorePreviousSessionOrStartFresh(requestedUrl: String?) {
    val savedUrls = savedSessionUrls()
    val hasUsefulSession = savedUrls.any { it != "about:blank" }

    if (!hasUsefulSession) {
      clearSavedSession()
      newTab(requestedUrl ?: HOME_URL)
      return
    }

    val savedCurrent = prefs.getInt(KEY_CURRENT_TAB, 0)
    val savedPrivacy = savedSessionPrivacy()
    val count = savedUrls.size
    val dialog = AlertDialog.Builder(this)
      .setTitle("Previous browsing session found")
      .setMessage(
        "${count} ${if (count == 1) "tab was" else "tabs were"} still open.\n\n" +
          "Reopen them, or discard the old session and start fresh?"
      )
      .setPositiveButton("REOPEN ${count} ${if (count == 1) "TAB" else "TABS"}") { _, _ ->
        restoringSession = true
        try {
          savedUrls.forEachIndexed { index, url -> newTab(url, savedPrivacy.getOrElse(index) { false }) }
          if (tabs.isNotEmpty()) switchTo(savedCurrent.coerceIn(0, tabs.lastIndex))
        } finally {
          restoringSession = false
        }

        if (!requestedUrl.isNullOrBlank() && savedUrls.none { it == requestedUrl }) {
          newTab(requestedUrl)
        }
        saveOpenTabs()
        Toast.makeText(this, "Previous tabs reopened", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton("DISCARD & START FRESH") { _, _ ->
        clearSavedSession()
        newTab(requestedUrl ?: "about:blank")
        saveOpenTabs()
        Toast.makeText(this, "Previous tabs discarded", Toast.LENGTH_SHORT).show()
      }
      .setCancelable(false)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }
    dialog.show()
  }

  private fun buildUi() {
    screen = FrameLayout(this).apply {
      setBackgroundColor(Color.rgb(2, 15, 30))
      isFocusable = true
      isFocusableInTouchMode = true
    }

    root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(2, 15, 30))
    }
    screen.addView(root, FrameLayout.LayoutParams(-1, -1))

    val compactUi = resources.configuration.screenWidthDp < 700
    val wideUi = resources.configuration.screenWidthDp >= 900

    header = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(if (compactUi) 6 else 10), dp(6), dp(if (compactUi) 6 else 10), dp(5))
      setBackgroundColor(Color.rgb(5, 93, 139))
    }
    root.addView(header, LinearLayout.LayoutParams(-1, -2))

    // Windows-style brand / clock / weather / utility row.
    val topScroll = HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      isFillViewport = wideUi
      overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    val topRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(2), dp(2), dp(2), dp(4))
    }
    topScroll.addView(topRow, ViewGroup.LayoutParams(if (wideUi) -1 else -2, dp(if (compactUi) 66 else 72)))
    header.addView(topScroll, LinearLayout.LayoutParams(-1, dp(if (compactUi) 68 else 74)))

    val avatar = ImageView(this).apply {
      setImageResource(com.learnwithchampak.tv.R.drawable.champak_installer_icon)
      scaleType = ImageView.ScaleType.CENTER_CROP
      background = roundedBg(Color.rgb(9, 63, 100), dp(24), Color.WHITE, dp(1))
      clipToOutline = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
    topRow.addView(avatar, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(8) })

    val brand = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, 0, dp(10), 0)
    }
    titleText = TextView(this).apply {
      text = "Champak's Browser v${appVersionName()}"
      setTextColor(Color.WHITE)
      textSize = if (compactUi) 15f else 18f
      typeface = Typeface.DEFAULT_BOLD
      maxLines = 1
    }
    val tagline = TextView(this).apply {
      text = "Learn With Champak • Designed by Champak Roy • Strictly for learning purposes"
      setTextColor(Color.rgb(255, 221, 74))
      textSize = if (compactUi) 9.5f else 10.5f
      typeface = Typeface.DEFAULT_BOLD
      maxLines = 1
    }
    brand.addView(titleText)
    brand.addView(tagline)
    topRow.addView(brand, LinearLayout.LayoutParams(if (wideUi) 0 else dp(470), -1, if (wideUi) 1f else 0f))

    val clockCard = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), dp(5), dp(12), dp(5))
      background = roundedBg(Color.rgb(9, 92, 137), dp(12), Color.rgb(72, 174, 218), dp(1))
    }
    clockTimeText = TextView(this).apply {
      setTextColor(Color.WHITE)
      textSize = if (compactUi) 13f else 15f
      typeface = Typeface.DEFAULT_BOLD
    }
    clockDateText = TextView(this).apply {
      setTextColor(Color.rgb(255, 226, 96))
      textSize = if (compactUi) 8.5f else 9.5f
      typeface = Typeface.DEFAULT_BOLD
    }
    clockCard.addView(clockTimeText)
    clockCard.addView(clockDateText)
    topRow.addView(clockCard, LinearLayout.LayoutParams(dp(160), dp(54)).apply { marginEnd = dp(8) })

    weatherText = TextView(this).apply {
      text = "Weather loading..."
      setTextColor(Color.WHITE)
      textSize = if (compactUi) 9f else 10f
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(12), dp(5), dp(12), dp(5))
      background = roundedBg(Color.rgb(9, 92, 137), dp(12), Color.rgb(72, 174, 218), dp(1))
      setOnClickListener { showWeatherLocationDialog() }
      isFocusable = true
    }
    topRow.addView(weatherText, LinearLayout.LayoutParams(dp(190), dp(54)).apply { marginEnd = dp(8) })

    topRow.addView(chromePill("How to Use", true) { showAddressHints() })
    topRow.addView(chromePill("Contact", true) { showContactDialog() })
    topRow.addView(chromePill("☰ Menu", false) { showBrowserCommandMenu() })

    // Tabs row first, matching Windows.
    val tabsRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setBackgroundColor(Color.rgb(2, 37, 64))
      setPadding(dp(2), dp(2), dp(2), dp(2))
    }
    val tabsLabel = TextView(this).apply {
      text = "Tabs"
      setTextColor(Color.WHITE)
      textSize = 11f
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
    }
    tabsRow.addView(tabsLabel, LinearLayout.LayoutParams(dp(38), dp(36)))

    val tabScroll = HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    tabStrip = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(2), dp(1), dp(2), dp(1))
    }
    tabScroll.addView(tabStrip, ViewGroup.LayoutParams(-2, dp(36)))
    tabsRow.addView(tabScroll, LinearLayout.LayoutParams(0, dp(38), 1f))

    val tabsActions = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    rotationButton = chromePill("⟳ Rotate", true) { showTabRotationDialog() }
    tabsActions.addView(rotationButton)
    tabsActions.addView(chromePill("＋ New", true) { newTab(HOME_URL) })
    tabsActions.addView(chromePill("▣ List", true) { showTabs() })
    tabsActions.addView(chromePill("⏱ Timed", true) { showTimedSiteMenu() })
    tabsRow.addView(tabsActions, LinearLayout.LayoutParams(-2, dp(38)))
    header.addView(tabsRow, LinearLayout.LayoutParams(-1, dp(40)))

    // Address row.
    val addressRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(0, dp(5), 0, dp(5))
    }
    addressRow.addView(btn("← Back", "Back") { goBackOrClose() }, fixedButtonLp(72, if (compactUi) 42 else 46))
    addressRow.addView(btn("→ Forward", "Forward") { activeWebView()?.let { if (it.canGoForward()) it.goForward() } }, fixedButtonLp(88, if (compactUi) 42 else 46))
    addressRow.addView(btn("⟳ Reload", "Reload") { activeWebView()?.reload() }, fixedButtonLp(82, if (compactUi) 42 else 46))

    addressBar = AutoCompleteTextView(this).apply {
      hint = "Search Google or type a website address"
      threshold = 1
      setSingleLine(true)
      textSize = if (compactUi) 15f else 17f
      setTextColor(Color.rgb(3, 44, 84))
      setHintTextColor(Color.rgb(100, 120, 138))
      background = roundedBg(Color.WHITE, dp(12), Color.WHITE, 0)
      setPadding(dp(14), 0, dp(14), 0)
      minHeight = dp(if (compactUi) 42 else 46)
      imeOptions = EditorInfo.IME_ACTION_GO
      setSelectAllOnFocus(false)
      setOnFocusChangeListener { _, hasFocus ->
        pointer.visibility = View.VISIBLE
        if (hasFocus) {
          refreshAddressSuggestions()
          showKeyboard()
          postDelayed({ showDropDown() }, 200)
        }
      }
      setOnClickListener {
        refreshAddressSuggestions()
        showKeyboard()
        showDropDown()
      }
      setOnItemClickListener { _, _, position, _ ->
        val value = adapter?.getItem(position)?.toString().orEmpty()
        if (value.isNotBlank()) {
          setText(value, false)
          loadInCurrent(value)
          hideKeyboardAndFocusPage()
        }
      }
      setOnEditorActionListener { _, actionId, event ->
        if (actionId == EditorInfo.IME_ACTION_GO || event?.keyCode == KeyEvent.KEYCODE_ENTER) {
          openAddressBarValue()
          true
        } else false
      }
    }
    addressRow.addView(addressBar, LinearLayout.LayoutParams(0, dp(if (compactUi) 42 else 46), 1f).apply {
      marginStart = dp(4)
      marginEnd = dp(6)
    })
    addressRow.addView(chromePill("▶ Go", true) { openAddressBarValue() })
    addressRow.addView(btn("⛶ Full Screen", "Full screen") { setFullScreenMode(true, true) }, fixedButtonLp(104, if (compactUi) 42 else 46))
    header.addView(addressRow, LinearLayout.LayoutParams(-1, dp(if (compactUi) 52 else 56)))

    // Main shortcut toolbar.
    val navScroll = HorizontalScrollView(this).apply {
      isHorizontalScrollBarEnabled = false
      isFillViewport = true
      overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
    }
    navBar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
    }
    navScroll.addView(navBar, ViewGroup.LayoutParams(-2, dp(if (compactUi) 40 else 44)))
    header.addView(navScroll, LinearLayout.LayoutParams(-1, dp(if (compactUi) 42 else 46)))
    populateToolbar(compactUi)

    statusText = TextView(this).apply {
      text = "🎓 Learn With Champak    Ready"
      setTextColor(Color.WHITE)
      textSize = if (compactUi) 9.5f else 10.5f
      setPadding(dp(4), dp(1), dp(4), dp(1))
      setBackgroundColor(Color.rgb(3, 99, 144))
    }
    header.addView(statusText, LinearLayout.LayoutParams(-1, dp(22)))

    webHolder = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))

    privacyOverlay = TextView(this).apply {
      text = "PRIVATE TAB\nReturn to the browser to reveal"
      textSize = 24f
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.rgb(4, 28, 50))
      visibility = View.GONE
      isFocusable = false
      isClickable = false
      elevation = dp(40).toFloat()
    }

    pointer = TextView(this).apply {
      text = "●"
      textSize = 34f
      gravity = Gravity.CENTER
      setTextColor(Color.rgb(255, 235, 59))
      setShadowLayer(8f, 0f, 0f, Color.BLACK)
      visibility = View.VISIBLE
      isFocusable = false
      isClickable = false
      elevation = dp(50).toFloat()
    }
    screen.addView(pointer, FrameLayout.LayoutParams(dp(54), dp(54)))

    screen.setOnTouchListener { _, event ->
      if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE || event.action == MotionEvent.ACTION_UP) {
        pointerX = event.x
        pointerY = event.y
        ensurePointerVisible()
        if (event.action == MotionEvent.ACTION_UP) clickAtPointer()
      }
      true
    }

    setContentView(screen)
    refreshAddressSuggestions()
    screen.post { centerPointerInWebPage() }
  }

  private fun appVersionName(): String {
    return try {
      packageManager.getPackageInfo(packageName, 0).versionName ?: "3"
    } catch (_: Exception) {
      "3"
    }
  }

  private fun roundedBg(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
    GradientDrawable().apply {
      setColor(fill)
      cornerRadius = radius.toFloat()
      if (strokeWidth > 0) setStroke(strokeWidth, stroke)
    }

  private fun chromePill(label: String, yellow: Boolean, action: () -> Unit): Button =
    Button(this).apply {
      text = label
      textSize = 10.5f
      isAllCaps = false
      typeface = Typeface.DEFAULT_BOLD
      setTextColor(if (yellow) Color.BLACK else Color.WHITE)
      background = roundedBg(
        if (yellow) Color.rgb(255, 199, 0) else Color.rgb(27, 112, 160),
        dp(18),
        if (yellow) Color.rgb(255, 214, 58) else Color.rgb(54, 149, 199),
        dp(1)
      )
      setPadding(dp(10), 0, dp(10), 0)
      minWidth = 0
      minHeight = dp(34)
      setOnClickListener { action() }
      setOnFocusChangeListener { v, hasFocus ->
        v.scaleX = if (hasFocus) 1.06f else 1f
        v.scaleY = if (hasFocus) 1.06f else 1f
        v.elevation = if (hasFocus) dp(8).toFloat() else dp(1).toFloat()
      }
      layoutParams = LinearLayout.LayoutParams(-2, dp(34)).apply { marginEnd = dp(6) }
      isFocusable = true
    }

  private val chromeClockTick = object : Runnable {
    override fun run() {
      if (::clockTimeText.isInitialized) {
        clockTimeText.text = "◷  " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        clockDateText.text = SimpleDateFormat("EEEE, d MMM yyyy", Locale.getDefault()).format(Date())
      }
      chromeHandler.postDelayed(this, 1000L)
    }
  }

  private fun startChromeClock() {
    chromeHandler.removeCallbacks(chromeClockTick)
    chromeHandler.post(chromeClockTick)
  }

  private fun showContactDialog() {
    AlertDialog.Builder(this)
      .setTitle("Contact")
      .setMessage("Champak Roy\n\nEmail: champaksworld@gmail.com\nWhatsApp / Phone: +91 9335874326")
      .setPositiveButton("Email") { _, _ ->
        openOutside("mailto:champaksworld@gmail.com")
      }
      .setNeutralButton("WhatsApp") { _, _ ->
        openOutside("https://wa.me/919335874326")
      }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showWeatherLocationDialog() {
    val input = EditText(this).apply {
      hint = "City, e.g. Varanasi"
      setSingleLine(true)
      setText(prefs.getString(KEY_WEATHER_CITY, "Varanasi") ?: "Varanasi")
      selectAll()
    }
    val dialog = AlertDialog.Builder(this)
      .setTitle("Weather Location")
      .setView(input)
      .setPositiveButton("Set", null)
      .setNeutralButton("Varanasi") { _, _ ->
        prefs.edit()
          .putString(KEY_WEATHER_CITY, "Varanasi")
          .putString(KEY_WEATHER_LAT, "25.3176")
          .putString(KEY_WEATHER_LON, "82.9739")
          .apply()
        refreshWeather()
      }
      .setNegativeButton("Cancel", null)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val city = input.text.toString().trim()
        if (city.isEmpty()) return@setOnClickListener
        resolveWeatherCity(city) { ok ->
          if (ok) dialog.dismiss()
        }
      }
    }
    dialog.show()
  }

  private fun resolveWeatherCity(city: String, done: (Boolean) -> Unit) {
    if (weatherLoading) return
    weatherLoading = true
    weatherText.text = "Finding $city..."
    Thread {
      try {
        val q = URLEncoder.encode(city, "UTF-8")
        val conn = URL("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=en&format=json").openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val results = org.json.JSONObject(body).optJSONArray("results")
        if (results == null || results.length() == 0) throw IllegalArgumentException("Location not found")
        val row = results.getJSONObject(0)
        val nameParts = listOf(
          row.optString("name"),
          row.optString("admin1"),
          row.optString("country")
        ).filter { it.isNotBlank() }.distinct()
        val label = nameParts.joinToString(", ")
        prefs.edit()
          .putString(KEY_WEATHER_CITY, label)
          .putString(KEY_WEATHER_LAT, row.getDouble("latitude").toString())
          .putString(KEY_WEATHER_LON, row.getDouble("longitude").toString())
          .apply()
        runOnUiThread {
          weatherLoading = false
          refreshWeather()
          done(true)
        }
      } catch (e: Exception) {
        runOnUiThread {
          weatherLoading = false
          weatherText.text = "Weather unavailable\nTap to set location"
          Toast.makeText(this, e.message ?: "Location not found", Toast.LENGTH_LONG).show()
          done(false)
        }
      }
    }.start()
  }

  private fun refreshWeather() {
    if (!::weatherText.isInitialized || weatherLoading) return
    val city = prefs.getString(KEY_WEATHER_CITY, "Varanasi") ?: "Varanasi"
    val lat = prefs.getString(KEY_WEATHER_LAT, "25.3176") ?: "25.3176"
    val lon = prefs.getString(KEY_WEATHER_LON, "82.9739") ?: "82.9739"
    weatherLoading = true
    weatherText.text = "☀  $city\nWeather loading..."
    Thread {
      try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m&timezone=auto"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val current = org.json.JSONObject(body).getJSONObject("current")
        val temp = current.optDouble("temperature_2m", Double.NaN)
        val feels = current.optDouble("apparent_temperature", Double.NaN)
        val wind = current.optDouble("wind_speed_10m", Double.NaN)
        val code = current.optInt("weather_code", -1)
        val condition = weatherCondition(code)
        val shortCity = if (city.length > 24) city.take(22) + "…" else city
        val text = "☀  $shortCity\n${temp.toInt()}°C • $condition\nFeels ${feels.toInt()}°C • Wind ${wind.toInt()} km/h"
        runOnUiThread {
          weatherLoading = false
          weatherText.text = text
        }
      } catch (_: Exception) {
        runOnUiThread {
          weatherLoading = false
          weatherText.text = "☁  $city\nWeather unavailable\nTap to change location"
        }
      }
    }.start()
  }

  private fun weatherCondition(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    in 51..57 -> "Drizzle"
    in 61..67 -> "Rain"
    in 71..77 -> "Snow"
    in 80..82 -> "Showers"
    in 95..99 -> "Thunderstorm"
    else -> "Weather"
  }

  private fun populateToolbar(compactUi: Boolean = resources.configuration.screenWidthDp < 700) {
    if (!::navBar.isInitialized) return
    navBar.removeAllViews()
    val h = if (compactUi) 38 else 42
    fun add(label: String, desc: String, width: Int, action: () -> Unit) {
      navBar.addView(btn(label, desc, action), fixedButtonLp(width, h))
    }

    add("← Back", "Back", 82) { goBackOrClose() }
    add("Forward →", "Forward", 102) { activeWebView()?.let { if (it.canGoForward()) it.goForward() } }
    add("Reload", "Reload", 82) { activeWebView()?.reload() }
    add("Find", "Find on page", 72) { showFindOnPageDialog() }
    add("Home", "Home", 76) { loadInCurrent(HOME_URL) }
    add("Learn", "Learn With Champak", 78) { newTab(HOME_URL) }
    add("Kashi", "Inside Kashi", 76) { newTab(INSIDE_KASHI_URL) }
    add("YouTube", "YouTube", 86) { newTab(YOUTUBE_URL) }
    add("WhatsApp", "WhatsApp Web", 92) { newTab(WHATSAPP_URL) }
    add("Google", "Google Search", 82) { newTab(GOOGLE_HOME_URL) }
    add("G Account", "Google Account secure sign-in", 98) { openSecureCustomTab(GOOGLE_SIGN_IN_URL, "Google Account") }
    add("Gmail", "Gmail secure sign-in", 76) { openSecureCustomTab(GOOGLE_GMAIL_SIGN_IN_URL, "Gmail") }
    add("Keyboard", "Open keyboard", 94) { focusAddressBar() }
    add("★ Save", "Add bookmark", 84) { addCurrentBookmark() }
    add("Bookmarks", "Bookmarks", 100) { showBookmarks() }
    add("History", "Visited links", 82) { showVisitedLinks() }
    add("Developer", "Developer tools and source", 96) { showDeveloperMenuAndroid() }
    add("Download", "Download current file or page", 98) { downloadCurrentUrl() }
    add("Open File", "Open last downloaded file", 94) { openLastDownloadedFile() }
    add("Downloads", "Open Downloads", 94) { openDownloadsFolder() }
    add("Privacy", "Toggle privacy blur for this tab", 86) { togglePrivacyBlur() }
    add("Timed", "Timed site open", 76) { showTimedSiteMenu() }
    add("Rotate", "Rotate tabs", 78) { showTabRotationDialog() }
    add("Share", "Share current page", 76) { shareCurrentPage() }
    add("Copy", "Copy current link", 72) { copyCurrentLink() }
    add("Desktop", "Toggle desktop site", 88) { toggleDesktopMode() }
    add("Default", "Default browser settings", 84) { openDefaultBrowserSettings() }
    add("Links", "Manage custom toolbar links", 74) { showQuickLinksMenu() }
    add("Settings", "Browser settings", 90) { showBrowserSettings() }
    add("Help", "Browser help", 70) { showAddressHints() }

    readQuickLinks().forEach { item ->
      val label = item.first.take(14)
      add(label, item.second, (label.length * 9 + 36).coerceIn(78, 150)) {
        newTab(item.second)
      }
    }
  }

  private fun fixedButtonLp(widthDp: Int, heightDp: Int = 44): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).apply {
      marginEnd = dp(5)
    }

  private fun btn(text: String, desc: String, action: () -> Unit): Button =
    btn(text, desc, action, fixedButtonLp(72))

  private fun btn(text: String, desc: String, action: () -> Unit, lp: LinearLayout.LayoutParams): Button {
    return Button(this).apply {
      this.text = text
      contentDescription = desc
      textSize = 12.5f
      isAllCaps = false
      setTextColor(Color.WHITE)
      setBackgroundColor(Color.rgb(8, 92, 156))
      setPadding(dp(8), 0, dp(8), 0)
      minWidth = dp(64)
      minHeight = dp(40)
      setOnClickListener { action() }
      setOnFocusChangeListener { v, hasFocus ->
        v.alpha = if (hasFocus) 1f else 0.92f
        v.scaleX = if (hasFocus) 1.06f else 1f
        v.scaleY = if (hasFocus) 1.06f else 1f
        v.elevation = if (hasFocus) dp(10).toFloat() else dp(2).toFloat()
      }
      layoutParams = lp
      isFocusable = true
    }
  }

  private fun newWebView(): WebView {
    return WebView(this).apply {
      isFocusable = true
      isFocusableInTouchMode = true
      configureWebView(this)
      webChromeClient = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String) {
          activeTabFor(view)?.title = title.ifBlank { activeTabFor(view)?.url ?: "Page" }
          if (view == activeWebView()) titleText.text = title.ifBlank { activeTab()?.url ?: "Page" }
          refreshTabs()
          saveOpenTabs()
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
          val popup = newWebView()
          val tab = BrowserTab(popup, "Google sign-in", "about:blank")
          tabs.add(tab)
          switchTo(tabs.lastIndex)
          val transport = resultMsg.obj as WebView.WebViewTransport
          transport.webView = popup
          resultMsg.sendToTarget()
          Toast.makeText(this@BrowserActivity, "Opened sign-in window in a new tab", Toast.LENGTH_SHORT).show()
          return true
        }

        override fun onCloseWindow(window: WebView) {
          val index = tabs.indexOfFirst { it.webView == window }
          if (index >= 0 && tabs.size > 1) {
            tabs.removeAt(index)
            window.destroy()
            switchTo(currentIndex.coerceAtMost(tabs.lastIndex))
          }
        }
      }
      setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        enqueueDownload(url, userAgent, contentDisposition, mimeType)
      }

      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          val url = request.url.toString()
          if (url.startsWith("mailto:") || url.startsWith("tel:") || url.startsWith("whatsapp:")) {
            openOutside(url)
            return true
          }
          if (isGoogleAuthenticationUrl(url)) {
            openGoogleSignInSecurely(url)
            return true
          }
          return false
        }
        override fun onPageFinished(view: WebView, url: String) {
          activeTabFor(view)?.url = url
          if (view == activeWebView()) addressBar.setText(if (url == "about:blank") "" else url, false)
          addVisitedLink(url)
          CookieManager.getInstance().flush()
          refreshTabs()
          saveOpenTabs()
          screen.post { ensurePointerVisible() }
        }
      }
    }
  }

  private fun configureWebView(web: WebView) {
    web.settings.javaScriptEnabled = true
    web.settings.domStorageEnabled = true
    web.settings.databaseEnabled = true
    web.settings.loadsImagesAutomatically = true
    web.settings.loadWithOverviewMode = false
    web.settings.useWideViewPort = true
    web.settings.builtInZoomControls = true
    web.settings.displayZoomControls = false
    web.settings.cacheMode = WebSettings.LOAD_DEFAULT
    web.settings.mediaPlaybackRequiresUserGesture = false
    web.settings.userAgentString = if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(this)
    web.settings.textZoom = textZoom
    web.settings.javaScriptCanOpenWindowsAutomatically = true
    web.settings.setSupportMultipleWindows(true)
    web.settings.allowContentAccess = true
    web.settings.allowFileAccess = true
    web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    web.isHorizontalScrollBarEnabled = true
    web.isVerticalScrollBarEnabled = true
    web.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
    web.overScrollMode = View.OVER_SCROLL_ALWAYS
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
  }

  private fun newTab(url: String, privacyBlur: Boolean = false) {
    val web = newWebView()
    val tab = BrowserTab(web, "New Tab", normalizeUrl(url), privacyBlur)
    tabs.add(tab)
    switchTo(tabs.lastIndex)
    web.loadUrl(tab.url)
    saveOpenTabs()
    focusWebPage()
  }

  private fun switchTo(index: Int, fromRotation: Boolean = false) {
    if (index !in tabs.indices) return
    currentIndex = index
    webHolder.removeAllViews()
    webHolder.addView(tabs[index].webView, FrameLayout.LayoutParams(-1, -1))
    webHolder.addView(privacyOverlay, FrameLayout.LayoutParams(-1, -1))
    titleText.text = if (!windowHasFocus && tabs[index].privacyBlur) "Private Tab" else tabs[index].title
    addressBar.setText(if (tabs[index].url == "about:blank") "" else tabs[index].url, false)
    refreshTabs()
    saveOpenTabs()
    applyPrivacyState()
    screen.post { ensurePointerVisible() }
    if (rotationActive && !rotationPaused && !fromRotation) resetRotationDeadline()
  }

  private fun closeCurrentTab() {
    if (tabs.size <= 1) {
      loadInCurrent("about:blank")
      return
    }
    val old = tabs.removeAt(currentIndex)
    old.webView.destroy()
    switchTo(currentIndex.coerceAtMost(tabs.lastIndex))
    if (rotationActive && tabs.size < 2) stopTabRotation("Rotation stopped — only one tab remains")
  }

  private fun refreshTabs() {
    tabStrip.removeAllViews()
    tabs.forEachIndexed { i, tab ->
      val shownTitle = if (!windowHasFocus && tab.privacyBlur) "Private Tab" else tab.title
      val privacyMark = if (tab.privacyBlur) "P " else ""
      val label = if (i == currentIndex) "● ${privacyMark}${i + 1}: ${shownTitle.take(18)}" else "${privacyMark}${i + 1}: ${shownTitle.take(18)}"
      tabStrip.addView(btn(label, "Tab ${i + 1}") { switchTo(i) }, LinearLayout.LayoutParams(0, -1, 1f))
    }
  }

  private fun togglePrivacyBlur() {
    val tab = activeTab() ?: return
    tab.privacyBlur = !tab.privacyBlur
    saveOpenTabs()
    refreshTabs()
    applyPrivacyState()
    Toast.makeText(
      this,
      if (tab.privacyBlur) "Privacy Blur enabled for this tab" else "Privacy Blur disabled for this tab",
      Toast.LENGTH_SHORT
    ).show()
  }

  private fun applyPrivacyState() {
    if (!::privacyOverlay.isInitialized) return
    val tab = activeTab()
    val hide = !windowHasFocus && tab?.privacyBlur == true
    val web = tab?.webView

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      web?.setRenderEffect(
        if (hide) RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP) else null
      )
    } else {
      web?.alpha = if (hide) 0.08f else 1f
    }

    privacyOverlay.visibility = if (hide) View.VISIBLE else View.GONE
    if (::titleText.isInitialized && tab != null) {
      titleText.text = if (hide) "Private Tab" else tab.title
    }
    refreshTabs()
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

  private fun openAddressBarValue() {
    val input = addressBar.text.toString()
    val saved = findSavedLink(input)
    loadInCurrent(saved ?: input)
    hideKeyboardAndFocusPage()
  }

  private fun loadInCurrent(input: String) {
    val url = normalizeUrl(input)
    activeTab()?.url = url
    addressBar.setText(if (url == "about:blank") "" else url, false)
    activeWebView()?.let {
      configureWebView(it)
      it.loadUrl(url)
    }
    saveOpenTabs()
  }

  private fun normalizeUrl(input: String): String {
    val value = input.trim()
    if (value.isEmpty()) return "about:blank"
    if (value.startsWith("http://") || value.startsWith("https://") || value == "about:blank") return value
    if (value.startsWith("www.") || (value.contains(".") && !value.contains(" "))) return "https://$value"
    val query = URLEncoder.encode(value, "UTF-8")
    return when (prefs.getString(KEY_SEARCH_ENGINE, "Google") ?: "Google") {
      "Bing" -> "https://www.bing.com/search?q=$query"
      "DuckDuckGo" -> "https://duckduckgo.com/?q=$query"
      else -> "https://www.google.com/search?q=$query"
    }
  }

  private fun activeTab(): BrowserTab? = tabs.getOrNull(currentIndex)
  private fun activeWebView(): WebView? = activeTab()?.webView
  private fun activeTabFor(view: WebView): BrowserTab? = tabs.firstOrNull { it.webView == view }


  private fun showFindOnPageDialog() {
    val input = EditText(this).apply {
      hint = "Text to find"
      setSingleLine(true)
      inputType = InputType.TYPE_CLASS_TEXT
    }
    val dialog = AlertDialog.Builder(this)
      .setTitle("Find on Page")
      .setView(input)
      .setPositiveButton("Find", null)
      .setNeutralButton("Next", null)
      .setNegativeButton("Done") { _, _ -> activeWebView()?.clearMatches() }
      .create()
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val q = input.text.toString().trim()
        if (q.isNotEmpty()) activeWebView()?.findAllAsync(q)
      }
      dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
        activeWebView()?.findNext(true)
      }
    }
    dialog.setOnDismissListener { activeWebView()?.clearMatches() }
    dialog.show()
  }

  private fun shareCurrentPage() {
    val url = activeTab()?.url.orEmpty()
    if (url.isBlank() || url == "about:blank") {
      Toast.makeText(this, "No page to share", Toast.LENGTH_SHORT).show()
      return
    }
    startActivity(
      Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, activeTab()?.title ?: "Web page")
          putExtra(Intent.EXTRA_TEXT, url)
        },
        "Share page"
      )
    )
  }

  private fun copyCurrentLink() {
    val url = activeTab()?.url.orEmpty()
    if (url.isBlank() || url == "about:blank") {
      Toast.makeText(this, "No page link to copy", Toast.LENGTH_SHORT).show()
      return
    }
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Web link", url))
    Toast.makeText(this, "Link copied", Toast.LENGTH_SHORT).show()
  }

  private fun toggleDesktopMode() {
    desktopMode = !desktopMode
    prefs.edit().putBoolean(KEY_DESKTOP_MODE, desktopMode).apply()
    tabs.forEach {
      it.webView.settings.userAgentString =
        if (desktopMode) DESKTOP_USER_AGENT else WebSettings.getDefaultUserAgent(this)
    }
    activeWebView()?.reload()
    Toast.makeText(
      this,
      if (desktopMode) "Desktop site mode enabled" else "Mobile site mode enabled",
      Toast.LENGTH_SHORT
    ).show()
  }

  private fun readQuickLinks(): MutableList<Pair<String, String>> =
    prefs.getString(KEY_QUICK_LINKS, "").orEmpty()
      .lines()
      .mapNotNull { line ->
        val parts = line.split("\t", limit = 2)
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
          Pair(parts[0], parts[1])
        } else null
      }
      .toMutableList()

  private fun saveQuickLinks(items: List<Pair<String, String>>) {
    prefs.edit().putString(
      KEY_QUICK_LINKS,
      items.joinToString("\n") { "${it.first.replace("\t", " ")}\t${it.second}" }
    ).apply()
  }

  private fun showQuickLinksMenu() {
    val items = readQuickLinks()
    if (items.isEmpty()) {
      AlertDialog.Builder(this)
        .setTitle("Quick Links")
        .setMessage("Add your own shortcuts for sites you use often.")
        .setPositiveButton("Add Link") { _, _ -> showQuickLinkEditor() }
        .setNegativeButton("Close", null)
        .show()
      return
    }

    AlertDialog.Builder(this)
      .setTitle("Quick Links")
      .setItems(items.map { "${it.first}\n${it.second}" }.toTypedArray()) { _, which ->
        loadInCurrent(items[which].second)
      }
      .setPositiveButton("Add Link") { _, _ -> showQuickLinkEditor() }
      .setNeutralButton("Manage") { _, _ -> showManageQuickLinks() }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showManageQuickLinks() {
    val items = readQuickLinks()
    if (items.isEmpty()) {
      showQuickLinksMenu()
      return
    }
    AlertDialog.Builder(this)
      .setTitle("Manage Quick Links")
      .setItems(items.map { it.first }.toTypedArray()) { _, which ->
        val selected = items[which]
        AlertDialog.Builder(this)
          .setTitle(selected.first)
          .setItems(arrayOf("Edit", "Remove")) { _, action ->
            if (action == 0) {
              showQuickLinkEditor(which, selected.first, selected.second)
            } else {
              items.removeAt(which)
              saveQuickLinks(items)
              populateToolbar()
              Toast.makeText(this, "Quick link removed from toolbar", Toast.LENGTH_SHORT).show()
            }
          }
          .setNegativeButton("Cancel", null)
          .show()
      }
      .setPositiveButton("Add Link") { _, _ -> showQuickLinkEditor() }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showQuickLinkEditor(index: Int? = null, oldLabel: String = "", oldUrl: String = "") {
    val box = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(8), dp(20), 0)
    }
    val labelInput = EditText(this).apply {
      hint = "Label, e.g. News"
      setSingleLine(true)
      setText(oldLabel)
    }
    val urlInput = EditText(this).apply {
      hint = "https://example.com"
      setSingleLine(true)
      setText(oldUrl.ifBlank { activeTab()?.url?.takeIf { it != "about:blank" } ?: "" })
    }
    box.addView(labelInput)
    box.addView(urlInput)

    val dialog = AlertDialog.Builder(this)
      .setTitle(if (index == null) "Add Quick Link" else "Edit Quick Link")
      .setView(box)
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val label = labelInput.text.toString().trim()
        val url = normalizeUrl(urlInput.text.toString())
        if (label.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
          Toast.makeText(this, "Enter a label and valid web address", Toast.LENGTH_LONG).show()
          return@setOnClickListener
        }
        val items = readQuickLinks()
        if (index == null || index !in items.indices) items.add(Pair(label, url))
        else items[index] = Pair(label, url)
        saveQuickLinks(items)
        populateToolbar()
        dialog.dismiss()
        Toast.makeText(this, "Quick link saved to toolbar", Toast.LENGTH_SHORT).show()
      }
    }
    dialog.show()
  }

  private fun resetRotationDeadline() {
    rotationDeadlineMs = System.currentTimeMillis() + rotationSeconds * 1000L
  }

  private val rotationTick = object : Runnable {
    override fun run() {
      if (!rotationActive) return
      if (!rotationPaused) {
        val remaining = ((rotationDeadlineMs - System.currentTimeMillis() + 999L) / 1000L)
          .coerceAtLeast(0L)
          .toInt()
        if (::rotationButton.isInitialized) {
          rotationButton.text = "⟳ ${remaining}s"
          rotationButton.setBackgroundColor(Color.rgb(9, 121, 105))
        }
        if (remaining <= 0) {
          if (tabs.size < 2) {
            stopTabRotation("Rotation stopped — open at least two tabs")
            return
          }
          switchTo((currentIndex + 1) % tabs.size, fromRotation = true)
          resetRotationDeadline()
        }
      } else if (::rotationButton.isInitialized) {
        rotationButton.text = "Ⅱ Rotate"
        rotationButton.setBackgroundColor(Color.rgb(80, 93, 110))
      }
      rotationHandler.postDelayed(this, 500L)
    }
  }

  private fun startTabRotation() {
    if (tabs.size < 2) {
      Toast.makeText(this, "Open at least two tabs first", Toast.LENGTH_LONG).show()
      return
    }
    rotationActive = true
    rotationPaused = false
    resetRotationDeadline()
    rotationHandler.removeCallbacks(rotationTick)
    rotationHandler.post(rotationTick)
    Toast.makeText(this, "Rotating tabs every $rotationSeconds seconds", Toast.LENGTH_SHORT).show()
  }

  private fun pauseTabRotation() {
    if (!rotationActive) return
    rotationPaused = true
  }

  private fun resumeTabRotation() {
    if (!rotationActive) return
    rotationPaused = false
    resetRotationDeadline()
  }

  private fun stopTabRotation(message: String = "Tab rotation stopped") {
    rotationActive = false
    rotationPaused = false
    rotationHandler.removeCallbacks(rotationTick)
    if (::rotationButton.isInitialized) {
      rotationButton.text = "Rotate"
      rotationButton.setBackgroundColor(Color.rgb(8, 92, 156))
    }
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
  }

  private fun showTabRotationDialog() {
    val input = EditText(this).apply {
      hint = "Seconds per tab (5–300)"
      inputType = InputType.TYPE_CLASS_NUMBER
      setSingleLine(true)
      setText(rotationSeconds.toString())
      selectAll()
    }
    val choices = arrayOf(
      if (!rotationActive) "Start rotation" else "Restart with this duration",
      if (rotationPaused) "Resume" else "Pause",
      "Stop"
    )
    AlertDialog.Builder(this)
      .setTitle("Rotate Tabs")
      .setMessage(
        "Each tab is shown in sequence. Default is 30 seconds.\n\n" +
          "Current: ${if (!rotationActive) "Stopped" else if (rotationPaused) "Paused" else "Running"}"
      )
      .setView(input)
      .setItems(choices) { _, which ->
        when (which) {
          0 -> {
            val seconds = input.text.toString().toIntOrNull() ?: rotationSeconds
            rotationSeconds = seconds.coerceIn(5, 300)
            prefs.edit().putInt(KEY_ROTATION_SECONDS, rotationSeconds).apply()
            startTabRotation()
          }
          1 -> if (rotationPaused) resumeTabRotation() else pauseTabRotation()
          2 -> stopTabRotation()
        }
      }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showBrowserSettings() {
    val engine = prefs.getString(KEY_SEARCH_ENGINE, "Google") ?: "Google"
    val options = arrayOf(
      "Search engine: $engine",
      "Text size: $textZoom%",
      "Desktop site: ${if (desktopMode) "On" else "Off"}",
      "Tab rotation",
      "Quick links",
      "Clear browsing data",
      "Default browser settings",
      "Privacy policy"
    )
    AlertDialog.Builder(this)
      .setTitle("Browser Settings")
      .setItems(options) { _, which ->
        when (which) {
          0 -> showSearchEngineDialog()
          1 -> showTextSizeDialog()
          2 -> toggleDesktopMode()
          3 -> showTabRotationDialog()
          4 -> showQuickLinksMenu()
          5 -> showClearBrowsingDataDialog()
          6 -> openDefaultBrowserSettings()
          7 -> openOutside(PRIVACY_URL)
        }
      }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showSearchEngineDialog() {
    val engines = arrayOf("Google", "DuckDuckGo", "Bing")
    val current = prefs.getString(KEY_SEARCH_ENGINE, "Google") ?: "Google"
    AlertDialog.Builder(this)
      .setTitle("Default Search Engine")
      .setSingleChoiceItems(engines, engines.indexOf(current).coerceAtLeast(0)) { dialog, which ->
        prefs.edit().putString(KEY_SEARCH_ENGINE, engines[which]).apply()
        dialog.dismiss()
        Toast.makeText(this, "${engines[which]} selected", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showTextSizeDialog() {
    val values = intArrayOf(75, 90, 100, 110, 125, 150, 175, 200)
    AlertDialog.Builder(this)
      .setTitle("Web Text Size")
      .setSingleChoiceItems(
        values.map { "$it%" }.toTypedArray(),
        values.indexOf(textZoom).coerceAtLeast(0)
      ) { dialog, which ->
        textZoom = values[which]
        prefs.edit().putInt(KEY_TEXT_ZOOM, textZoom).apply()
        tabs.forEach { it.webView.settings.textZoom = textZoom }
        dialog.dismiss()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showClearBrowsingDataDialog() {
    val items = arrayOf("Visited history", "Cookies + cache", "All browsing data (keeps bookmarks)")
    AlertDialog.Builder(this)
      .setTitle("Clear Browsing Data")
      .setItems(items) { _, which ->
        when (which) {
          0 -> {
            saveList(KEY_HISTORY, emptyList())
            refreshAddressSuggestions()
          }
          1 -> {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            tabs.forEach { it.webView.clearCache(true); it.webView.clearFormData() }
          }
          2 -> {
            saveList(KEY_HISTORY, emptyList())
            clearSavedSession()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            tabs.forEach {
              it.webView.clearCache(true)
              it.webView.clearFormData()
              it.webView.clearHistory()
            }
            refreshAddressSuggestions()
          }
        }
        Toast.makeText(this, "Browsing data cleared", Toast.LENGTH_SHORT).show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun isGoogleAuthenticationUrl(url: String): Boolean {
    val host = try { Uri.parse(url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
    return host == "accounts.google.com" ||
      host == "oauth2.googleapis.com" ||
      host == "accounts.youtube.com" ||
      host.endsWith(".googleusercontent.com") ||
      host == "myaccount.google.com"
  }

  private fun openSecureCustomTab(url: String, label: String) {
    setFullScreenMode(false, false)
    try {
      val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
      customTabsIntent.launchUrl(this, Uri.parse(url))
      Toast.makeText(
        this,
        "$label opened in Android's secure browser session. Close it to return here.",
        Toast.LENGTH_LONG
      ).show()
    } catch (_: Exception) {
      openOutside(url)
    }
  }

  private fun openGoogleSignInSecurely(url: String = GOOGLE_SIGN_IN_URL) {
    // Preserve a website's full OAuth URL including client_id, redirect_uri,
    // scope, state and PKCE parameters. Only use our account chooser when
    // the toolbar explicitly starts a generic Google sign-in.
    val safeUrl = if (isGoogleAuthenticationUrl(url)) url else GOOGLE_SIGN_IN_URL
    openSecureCustomTab(safeUrl, "Google sign-in")
  }

  private fun openGoogleSignInOutside() {
    openSecureCustomTab(GOOGLE_SIGN_IN_URL, "Google Account")
  }

  private fun showDeveloperMenuAndroid() {
    val options = arrayOf(
      "Open Android source repository",
      "Copy current page URL",
      "Open current page outside",
      "Reload current page"
    )
    AlertDialog.Builder(this)
      .setTitle("Developer")
      .setItems(options) { _, which ->
        when (which) {
          0 -> openOutside(GITHUB_CODE_URL)
          1 -> copyCurrentLink()
          2 -> openOutside(activeTab()?.url ?: HOME_URL)
          3 -> activeWebView()?.reload()
        }
      }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun openOutside(url: String) {
    try {
      startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
      Toast.makeText(this, "No outside browser/app found", Toast.LENGTH_LONG).show()
    }
  }

  private fun downloadCurrentUrl() {
    val url = activeTab()?.url.orEmpty()
    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
      Toast.makeText(this, "Open a downloadable web address first", Toast.LENGTH_LONG).show()
      return
    }
    enqueueDownload(url, DESKTOP_USER_AGENT, null, null)
  }

  private fun enqueueDownload(
    url: String,
    userAgent: String?,
    contentDisposition: String?,
    mimeType: String?
  ) {
    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
      Toast.makeText(this, "This link cannot be downloaded", Toast.LENGTH_LONG).show()
      return
    }

    try {
      val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
      val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle(fileName)
        setDescription("Downloading with Learn With Champak")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
        if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
        val cookies = CookieManager.getInstance().getCookie(url)
        if (!cookies.isNullOrBlank()) addRequestHeader("Cookie", cookies)
      }

      val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val id = manager.enqueue(request)
      prefs.edit().putLong(KEY_LAST_DOWNLOAD_ID, id).apply()
      Toast.makeText(this, "Downloading " + fileName, Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
      Toast.makeText(this, "Download failed: " + (e.message ?: "unknown error"), Toast.LENGTH_LONG).show()
    }
  }

  private fun openLastDownloadedFile() {
    val id = prefs.getLong(KEY_LAST_DOWNLOAD_ID, -1L)
    if (id < 0L) {
      openDownloadsFolder()
      return
    }

    try {
      val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
      val uri = manager.getUriForDownloadedFile(id)
      if (uri == null) {
        Toast.makeText(this, "The last download is not ready yet", Toast.LENGTH_LONG).show()
        openDownloadsFolder()
        return
      }

      val mime = manager.getMimeTypeForDownloadedFile(id) ?: "*/*"
      startActivity(Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      })
    } catch (_: Exception) {
      openDownloadsFolder()
    }
  }

  private fun openDownloadsFolder() {
    try {
      startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
    } catch (_: Exception) {
      Toast.makeText(this, "No Downloads app is available on this device", Toast.LENGTH_LONG).show()
    }
  }

  private fun readList(key: String): MutableList<String> {
    val text = prefs.getString(key, "").orEmpty()
    return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
  }

  private fun saveList(key: String, values: List<String>) {
    prefs.edit().putString(key, values.joinToString("\n")).apply()
  }

  private fun savedLinks(): List<String> {
    val combined = mutableListOf<String>()
    combined.addAll(readList(KEY_BOOKMARKS))
    combined.addAll(readList(KEY_HISTORY))
    combined.add(GOOGLE_SIGN_IN_URL)
    combined.add(GOOGLE_HOME_URL)
    return combined.distinct().filter { it.isNotBlank() && it != "about:blank" }
  }

  private fun refreshAddressSuggestions() {
    if (!::addressBar.isInitialized) return
    val items = savedLinks()
    val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
    addressBar.setAdapter(adapter)
  }

  private fun findSavedLink(input: String): String? {
    val q = input.trim()
    if (q.isEmpty()) return null
    val qLower = q.lowercase()
    return savedLinks().firstOrNull { it.equals(q, ignoreCase = true) }
      ?: savedLinks().firstOrNull { it.lowercase().contains(qLower) }
  }

  private fun addVisitedLink(url: String) {
    if (url.isBlank() || url == "about:blank") return
    val items = readList(KEY_HISTORY)
    items.remove(url)
    items.add(0, url)
    saveList(KEY_HISTORY, items.take(MAX_HISTORY))
    refreshAddressSuggestions()
  }

  private fun addCurrentBookmark() {
    val url = activeTab()?.url.orEmpty()
    if (url.isBlank() || url == "about:blank") {
      Toast.makeText(this, "No page to bookmark", Toast.LENGTH_SHORT).show()
      return
    }
    val items = readList(KEY_BOOKMARKS)
    items.remove(url)
    items.add(0, url)
    saveList(KEY_BOOKMARKS, items)
    refreshAddressSuggestions()
    Toast.makeText(this, "Bookmark saved. It will appear in the address bar suggestions.", Toast.LENGTH_LONG).show()
  }

  private fun showBookmarks() {
    val items = readList(KEY_BOOKMARKS)
    if (items.isEmpty()) {
      AlertDialog.Builder(this)
        .setTitle("Bookmarks")
        .setMessage("No bookmarks yet. Open a page and choose ★ Add Bookmark.")
        .setPositiveButton("OK", null)
        .show()
      return
    }
    AlertDialog.Builder(this)
      .setTitle("Bookmarks")
      .setItems(items.toTypedArray()) { _, which -> loadInCurrent(items[which]); focusWebPage() }
      .setPositiveButton("Add Current") { _, _ -> addCurrentBookmark() }
      .setNeutralButton("Clear All") { _, _ -> saveList(KEY_BOOKMARKS, emptyList()); refreshAddressSuggestions() }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showVisitedLinks() {
    val items = readList(KEY_HISTORY)
    if (items.isEmpty()) {
      AlertDialog.Builder(this)
        .setTitle("Visited Links")
        .setMessage("No visited links yet. Every opened page will be saved and will appear in the address bar suggestions.")
        .setPositiveButton("OK", null)
        .show()
      return
    }
    AlertDialog.Builder(this)
      .setTitle("Visited Links")
      .setItems(items.toTypedArray()) { _, which -> loadInCurrent(items[which]); focusWebPage() }
      .setNeutralButton("Clear All") { _, _ -> saveList(KEY_HISTORY, emptyList()); refreshAddressSuggestions() }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showTimedSiteMenu() {
    val options = arrayOf(
      "Daily at a fixed time",
      "Repeat every N minutes",
      "Disable timed opening"
    )
    AlertDialog.Builder(this)
      .setTitle("Timed Site Open")
      .setMessage("Current: ${TimedSiteScheduler.summary(this)}")
      .setItems(options) { _, which ->
        when (which) {
          0 -> showDailyTimedSiteDialog()
          1 -> showIntervalTimedSiteDialog()
          2 -> {
            TimedSiteScheduler.disable(this)
            Toast.makeText(this, "Timed site opening disabled", Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton("Close", null)
      .show()
  }

  private fun showDailyTimedSiteDialog() {
    val urlInput = EditText(this).apply {
      hint = "https://example.com"
      setSingleLine(true)
      setText(
        TimedSiteScheduler.currentUrl(this@BrowserActivity).ifBlank {
          activeTab()?.url?.takeIf { it != "about:blank" } ?: HOME_URL
        }
      )
      selectAll()
    }

    AlertDialog.Builder(this)
      .setTitle("Daily Timed Site")
      .setMessage("Enter the site, then choose the daily opening time.")
      .setView(urlInput)
      .setPositiveButton("Choose Time") { _, _ ->
        val url = normalizeUrl(urlInput.text.toString())
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
          Toast.makeText(this, "Enter a valid website URL", Toast.LENGTH_LONG).show()
          return@setPositiveButton
        }
        TimePickerDialog(
          this,
          { _, hour, minute ->
            TimedSiteScheduler.saveDaily(this, url, hour, minute)
            Toast.makeText(
              this,
              "Scheduled daily at %02d:%02d".format(hour, minute),
              Toast.LENGTH_LONG
            ).show()
          },
          TimedSiteScheduler.currentHour(this),
          TimedSiteScheduler.currentMinute(this),
          true
        ).show()
      }
      .setNegativeButton("Cancel", null)
      .show()
  }

  private fun showIntervalTimedSiteDialog() {
    val box = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(8), dp(20), 0)
    }
    val urlInput = EditText(this).apply {
      hint = "https://example.com"
      setSingleLine(true)
      setText(
        TimedSiteScheduler.currentUrl(this@BrowserActivity).ifBlank {
          activeTab()?.url?.takeIf { it != "about:blank" } ?: HOME_URL
        }
      )
    }
    val intervalInput = EditText(this).apply {
      hint = "Minutes, e.g. 30"
      inputType = InputType.TYPE_CLASS_NUMBER
      setSingleLine(true)
      setText(TimedSiteScheduler.currentInterval(this@BrowserActivity).toString())
    }
    box.addView(urlInput, LinearLayout.LayoutParams(-1, -2))
    box.addView(intervalInput, LinearLayout.LayoutParams(-1, -2))

    val dialog = AlertDialog.Builder(this)
      .setTitle("Repeat Timed Site")
      .setMessage("Open this site repeatedly at the selected interval.")
      .setView(box)
      .setPositiveButton("Save", null)
      .setNegativeButton("Cancel", null)
      .create()

    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        val url = normalizeUrl(urlInput.text.toString())
        val minutes = intervalInput.text.toString().toIntOrNull() ?: 0
        if (!(url.startsWith("http://") || url.startsWith("https://"))) {
          Toast.makeText(this, "Enter a valid website URL", Toast.LENGTH_LONG).show()
          return@setOnClickListener
        }
        if (minutes !in 1..10080) {
          Toast.makeText(this, "Interval must be 1 to 10080 minutes", Toast.LENGTH_LONG).show()
          return@setOnClickListener
        }
        TimedSiteScheduler.saveInterval(this, url, minutes)
        Toast.makeText(this, "Scheduled every $minutes minutes", Toast.LENGTH_LONG).show()
        dialog.dismiss()
      }
    }
    dialog.show()
  }

  private fun showAddressHints() {
    AlertDialog.Builder(this)
      .setTitle("Address Bar Hints")
      .setMessage(
        "Visited links are saved automatically.\n\n" +
          "Move the yellow pointer to the address bar and press OK. Start typing part of a visited link, then choose the matching saved link.\n\n" +
          "Type google sign in, accounts.google.com or press G to open Google sign-in securely.\n\n" +
          "Google authentication opens in an Android secure browser tab rather than the embedded WebView. Close that tab to return here."
      )
      .setPositiveButton("Open Address Bar") { _, _ -> focusAddressBar() }
      .setNegativeButton("Close", null)
      .show()
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
      screen.post {
        ensurePointerVisible()
        focusWebPage()
      }
    }
    if (enabled) Toast.makeText(this, "Pointer active. Back exits full screen. Long-press OK opens menu.", Toast.LENGTH_LONG).show()
  }

  private fun focusAddressBar() {
    setFullScreenMode(false, false)
    refreshAddressSuggestions()
    addressBar.requestFocus()
    addressBar.setSelection(addressBar.text.length)
    showKeyboard()
    addressBar.postDelayed({ addressBar.showDropDown() }, 200)
    ensurePointerVisible()
  }

  private fun showKeyboard() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(addressBar, InputMethodManager.SHOW_IMPLICIT)
  }

  private fun hideKeyboardAndFocusPage() {
    (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(addressBar.windowToken, 0)
    addressBar.dismissDropDown()
    focusWebPage()
  }

  private fun focusWebPage() {
    addressBar.clearFocus()
    activeWebView()?.requestFocus()
    ensurePointerVisible()
  }

  private fun ensurePointerVisible() {
    if (!::pointer.isInitialized || !::screen.isInitialized) return
    if (pointerX <= 0f || pointerY <= 0f) centerPointerInWebPage()
    clampPointerToScreen()
    pointer.visibility = View.VISIBLE
    pointer.bringToFront()
    updatePointerPosition()
  }

  private fun centerPointerInWebPage() {
    val webOrigin = viewOriginInScreen(webHolder)
    val width = if (webHolder.width > 0) webHolder.width else screen.width
    val height = if (webHolder.height > 0) webHolder.height else screen.height
    pointerX = webOrigin.first + width / 2f
    pointerY = webOrigin.second + height / 2f
    clampPointerToScreen()
    updatePointerPosition()
  }

  private fun clampPointerToScreen() {
    val half = dp(27).toFloat()
    val maxX = max(half, screen.width.toFloat() - half)
    val maxY = max(half, screen.height.toFloat() - half)
    pointerX = min(max(pointerX, half), maxX)
    pointerY = min(max(pointerY, half), maxY)
  }

  private fun updatePointerPosition() {
    if (!::pointer.isInitialized) return
    clampPointerToScreen()
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
    val webPoint = pointInsideView(webHolder, pointerX, pointerY) ?: return
    val edge = dp(42)
    val scroll = dp(260)
    when {
      dy > 0 && webPoint.second >= webHolder.height - edge -> performPageScroll(scroll)
      dy < 0 && webPoint.second <= edge -> performPageScroll(-scroll)
      dx > 0 && webPoint.first >= webHolder.width - edge -> performHorizontalPageScroll(dp(220))
      dx < 0 && webPoint.first <= edge -> performHorizontalPageScroll(-dp(220))
    }
  }

  private fun performHorizontalPageScroll(amount: Int) {
    activeWebView()?.let { web ->
      web.scrollBy(amount, 0)
      val js = """
        (function(){
          var amount = $amount;
          var el = document.scrollingElement || document.documentElement || document.body;
          if (el && el.scrollWidth > el.clientWidth) {
            el.scrollBy({left: amount, top: 0, behavior: 'smooth'});
          }
          var midX = amount > 0 ? window.innerWidth - 12 : 12;
          var midY = Math.floor(window.innerHeight / 2);
          var hit = document.elementFromPoint(midX, midY);
          while (hit && hit !== document.body && hit !== document.documentElement) {
            var style = window.getComputedStyle(hit);
            var canScroll = /(auto|scroll)/.test(style.overflowX) && hit.scrollWidth > hit.clientWidth;
            if (canScroll) {
              hit.scrollBy({left: amount, top: 0, behavior: 'smooth'});
              break;
            }
            hit = hit.parentElement;
          }
        })();
      """.trimIndent()
      web.evaluateJavascript(js, null)
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
    ensurePointerVisible()

    if (!fullScreen) {
      val hit = findClickableViewAt(root, pointerX, pointerY)
      when (hit) {
        addressBar -> {
          focusAddressBar()
          return
        }
        is Button -> {
          hit.requestFocus()
          hit.performClick()
          return
        }
      }
    }

    if (pointInsideView(webHolder, pointerX, pointerY) != null) {
      clickWebAtPointer()
    } else {
      focusWebPage()
    }
  }

  private fun clickWebAtPointer() {
    val web = activeWebView() ?: return
    val p = pointInsideView(web, pointerX, pointerY) ?: pointInsideView(webHolder, pointerX, pointerY) ?: return
    focusWebPage()
    val x = p.first.coerceIn(1f, max(1f, web.width - 1f))
    val y = p.second.coerceIn(1f, max(1f, web.height - 1f))
    val now = System.currentTimeMillis()
    val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
    val up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0)
    web.dispatchTouchEvent(down)
    web.dispatchTouchEvent(up)
    down.recycle()
    up.recycle()
  }

  private fun findClickableViewAt(view: View, x: Float, y: Float): View? {
    if (view.visibility != View.VISIBLE || pointInsideView(view, x, y) == null) return null
    if (view is ViewGroup) {
      for (i in view.childCount - 1 downTo 0) {
        val child = view.getChildAt(i)
        if (child == pointer) continue
        val found = findClickableViewAt(child, x, y)
        if (found != null) return found
      }
    }
    return when {
      view == addressBar -> view
      view is Button && view.isEnabled -> view
      else -> null
    }
  }

  private fun pointInsideView(view: View, x: Float, y: Float): Pair<Float, Float>? {
    if (view.visibility != View.VISIBLE || view.width <= 0 || view.height <= 0) return null
    val origin = viewOriginInScreen(view)
    val localX = x - origin.first
    val localY = y - origin.second
    return if (localX >= 0f && localY >= 0f && localX <= view.width && localY <= view.height) Pair(localX, localY) else null
  }

  private fun viewOriginInScreen(view: View): Pair<Float, Float> {
    val viewLocation = IntArray(2)
    val screenLocation = IntArray(2)
    view.getLocationOnScreen(viewLocation)
    screen.getLocationOnScreen(screenLocation)
    return Pair((viewLocation[0] - screenLocation[0]).toFloat(), (viewLocation[1] - screenLocation[1]).toFloat())
  }

  private fun showBrowserCommandMenu() {
    longPressMenuShown = true
    val options = arrayOf(
      "Return to Browser Buttons",
      "New Blank Tab",
      if (fullScreen) "Show Controls" else "Full Screen Web Page",
      "Focus Web Page / Pointer",
      "Open Keyboard / Address Bar",
      "Google Sign-In (Secure)",
      "Google Sign-In (System Browser)",
      "Address Bar Hints",
      "Add Bookmark",
      "Bookmarks",
      "Visited Links",
      "Timed Site Open",
      "Rotate Tabs",
      "Quick Links",
      "Find on Page",
      "Share Page",
      "Copy Link",
      if (desktopMode) "Use Mobile Site" else "Use Desktop Site",
      "Browser Settings",
      "Back in Web Page",
      "Home",
      "Open Outside",
      "Download Current File/Page",
      "Open Last Downloaded File",
      if (activeTab()?.privacyBlur == true) "Disable Privacy Blur for This Tab" else "Enable Privacy Blur for This Tab",
      "Exit Browser",
      "Cancel"
    )
    AlertDialog.Builder(this)
      .setTitle("Browser Menu")
      .setItems(options) { _, which ->
        longPressMenuShown = false
        when (which) {
          0 -> returnToBrowserButtons()
          1 -> newTab("about:blank")
          2 -> setFullScreenMode(!fullScreen, true)
          3 -> focusWebPage()
          4 -> focusAddressBar()
          5 -> openGoogleSignInSecurely()
          6 -> openGoogleSignInOutside()
          7 -> showAddressHints()
          8 -> addCurrentBookmark()
          9 -> showBookmarks()
          10 -> showVisitedLinks()
          11 -> showTimedSiteMenu()
          12 -> showTabRotationDialog()
          13 -> showQuickLinksMenu()
          14 -> showFindOnPageDialog()
          15 -> shareCurrentPage()
          16 -> copyCurrentLink()
          17 -> toggleDesktopMode()
          18 -> showBrowserSettings()
          19 -> goBackOrClose()
          20 -> loadInCurrent(HOME_URL)
          21 -> openOutside(activeTab()?.url ?: HOME_URL)
          22 -> downloadCurrentUrl()
          23 -> openLastDownloadedFile()
          24 -> togglePrivacyBlur()
          25 -> finish()
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
    returnToFirstScreen()
  }

  private fun returnToBrowserButtons() {
    setFullScreenMode(false, false)
    header.visibility = View.VISIBLE
    ensurePointerVisible()
    Toast.makeText(this, "Browser buttons are visible. Move pointer to any button or address bar and press OK.", Toast.LENGTH_LONG).show()
  }

  private fun returnToFirstScreen() {
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
      if (event.action == KeyEvent.ACTION_DOWN) {
        when (event.keyCode) {
          KeyEvent.KEYCODE_DPAD_UP -> { movePointer(0f, -dp(28).toFloat()); return true }
          KeyEvent.KEYCODE_DPAD_DOWN -> { movePointer(0f, dp(28).toFloat()); return true }
          KeyEvent.KEYCODE_DPAD_LEFT -> { movePointer(-dp(28).toFloat(), 0f); return true }
          KeyEvent.KEYCODE_DPAD_RIGHT -> { movePointer(dp(28).toFloat(), 0f); return true }
          KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { hideKeyboardAndFocusPage(); return true }
        }
      }
      return super.dispatchKeyEvent(event)
    }

    if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
      longPressMenuShown = false
      return true
    }

    if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

    when (event.keyCode) {
      KeyEvent.KEYCODE_DPAD_UP -> { movePointer(0f, -dp(28).toFloat()); return true }
      KeyEvent.KEYCODE_DPAD_DOWN -> { movePointer(0f, dp(28).toFloat()); return true }
      KeyEvent.KEYCODE_DPAD_LEFT -> { movePointer(-dp(28).toFloat(), 0f); return true }
      KeyEvent.KEYCODE_DPAD_RIGHT -> { movePointer(dp(28).toFloat(), 0f); return true }
      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        if (event.repeatCount >= 6 && !longPressMenuShown) {
          showBrowserCommandMenu()
        } else if (event.repeatCount == 0) {
          clickAtPointer()
        }
        return true
      }
      KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> { goBackOrClose(); return true }
      KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_GUIDE -> { returnToBrowserButtons(); return true }
    }
    return super.dispatchKeyEvent(event)
  }

  private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
