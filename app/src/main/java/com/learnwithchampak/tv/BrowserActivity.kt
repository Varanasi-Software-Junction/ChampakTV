package com.learnwithchampak.tv

import android.app.AlertDialog
import android.app.DownloadManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
    const val EXTRA_TIMED_OPEN = "com.learnwithchampak.tv.EXTRA_TIMED_OPEN"
    const val EXTRA_START_FRESH = "com.learnwithchampak.tv.EXTRA_START_FRESH"
    const val EXTRA_REOPEN_ALL_TABS = "com.learnwithchampak.tv.EXTRA_REOPEN_ALL_TABS"
    private const val HOME_URL = "https://www.learnwithchampak.live"
    private const val GOOGLE_SIGN_IN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fwww.google.com%2F&hl=en"
    private const val GOOGLE_HOME_URL = "https://www.google.com"
    private const val APK_URL = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv.apk"
    private const val PREFS = "champak_tabs_prefs"
    private const val KEY_DEFAULT_ASKED = "default_asked_browser"
    private const val KEY_BOOKMARKS = "browser_bookmarks"
    private const val KEY_HISTORY = "browser_history"
    private const val KEY_OPEN_TABS = "browser_open_tabs"
    private const val KEY_CURRENT_TAB = "browser_current_tab"
    private const val KEY_LAST_DOWNLOAD_ID = "browser_last_download_id"
    private const val MAX_HISTORY = 80
    private const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
  }

  private data class BrowserTab(val webView: WebView, var title: String, var url: String)

  private lateinit var screen: FrameLayout
  private lateinit var root: LinearLayout
  private lateinit var header: LinearLayout
  private lateinit var tabStrip: LinearLayout
  private lateinit var webHolder: FrameLayout
  private lateinit var pointer: TextView
  private lateinit var addressBar: AutoCompleteTextView
  private lateinit var titleText: TextView
  private lateinit var prefs: SharedPreferences

  private val tabs = mutableListOf<BrowserTab>()
  private var currentIndex = -1
  private var fullScreen = false
  private var pointerX = 0f
  private var pointerY = 0f
  private var longPressMenuShown = false
  private var restoringSession = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().flush()
    if (resources.configuration.screenWidthDp >= 700) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildUi()
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

  override fun onDestroy() {
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

  private fun saveOpenTabs() {
    if (restoringSession || tabs.isEmpty()) return
    prefs.edit()
      .putString(KEY_OPEN_TABS, tabs.joinToString("\n") { it.url.ifBlank { "about:blank" } })
      .putInt(KEY_CURRENT_TAB, currentIndex.coerceAtLeast(0))
      .apply()
  }

  private fun clearSavedSession() {
    prefs.edit()
      .remove(KEY_OPEN_TABS)
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
    restoringSession = true
    try {
      savedUrls.forEach { newTab(it) }
      if (tabs.isNotEmpty()) switchTo(savedCurrent.coerceIn(0, tabs.lastIndex))
    } finally {
      restoringSession = false
    }

    saveOpenTabs()
    Toast.makeText(this, "All saved tabs reopened", Toast.LENGTH_SHORT).show()
  }

  private fun restoreSessionSilentlyAndOpen(requestedUrl: String) {
    val savedUrls = savedSessionUrls()
    val target = normalizeUrl(requestedUrl)

    restoringSession = true
    try {
      savedUrls.filter { it.isNotBlank() }.forEach { newTab(it) }
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
          savedUrls.forEach { newTab(it) }
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
      setBackgroundColor(Color.rgb(3, 15, 34))
      isFocusable = true
      isFocusableInTouchMode = true
    }

    root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(Color.rgb(3, 15, 34))
    }
    screen.addView(root, FrameLayout.LayoutParams(-1, -1))

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
    top.addView(btn("▦", "Return to first screen") { returnToFirstScreen() })
    top.addView(btn("＋", "New tab") { newTab(HOME_URL) })
    top.addView(btn("▤", "Tabs") { showTabs() })
    top.addView(btn("×", "Close tab") { closeCurrentTab() })
    top.addView(btn("↓", "Download current file or page") { downloadCurrentUrl() })
    top.addView(btn("File", "Open last downloaded file") { openLastDownloadedFile() })
    top.addView(btn("⛶", "Full screen") { setFullScreenMode(true, true) })

    val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    header.addView(nav, LinearLayout.LayoutParams(-1, dp(44)))
    nav.addView(btn("←", "Back") { goBackOrClose() })
    nav.addView(btn("→", "Forward") { activeWebView()?.let { if (it.canGoForward()) it.goForward() } })
    nav.addView(btn("↻", "Reload") { activeWebView()?.reload() })
    nav.addView(btn("⌂", "Home") { loadInCurrent(HOME_URL) })

    addressBar = AutoCompleteTextView(this).apply {
      hint = "Type website/search/saved link • G opens Google sign-in"
      threshold = 1
      setSingleLine(true)
      textSize = 14f
      setTextColor(Color.rgb(3, 44, 84))
      setHintTextColor(Color.rgb(90, 115, 140))
      setBackgroundColor(Color.WHITE)
      imeOptions = EditorInfo.IME_ACTION_GO
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
    nav.addView(addressBar, LinearLayout.LayoutParams(0, dp(38), 1f))
    nav.addView(btn("▶", "Go") { openAddressBarValue() })

    val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    header.addView(tools, LinearLayout.LayoutParams(-1, dp(38)))
    tools.addView(btn("First", "Return to first app screen") { returnToFirstScreen() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Page", "Focus web page") { focusWebPage() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Keys", "Open keyboard") { focusAddressBar() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("G", "Google sign-in inside") { openGoogleSignInInside() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("★", "Add bookmark") { addCurrentBookmark() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("☆", "Bookmarks") { showBookmarks() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("◷", "Visited") { showVisitedLinks() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("?", "Address bar hints") { showAddressHints() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Time", "Timed site open") { showTimedSiteMenu() }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Out", "Open outside") { openOutside(activeTab()?.url ?: HOME_URL) }, LinearLayout.LayoutParams(0, -1, 1f))
    tools.addView(btn("Upd", "Update app") { openOutside(APK_URL) }, LinearLayout.LayoutParams(0, -1, 1f))

    tabStrip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.rgb(2, 35, 70)) }
    header.addView(tabStrip, LinearLayout.LayoutParams(-1, dp(34)))

    webHolder = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
    root.addView(webHolder, LinearLayout.LayoutParams(-1, 0, 1f))

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
    web.settings.loadWithOverviewMode = true
    web.settings.useWideViewPort = true
    web.settings.builtInZoomControls = true
    web.settings.displayZoomControls = false
    web.settings.cacheMode = WebSettings.LOAD_DEFAULT
    web.settings.mediaPlaybackRequiresUserGesture = false
    web.settings.userAgentString = DESKTOP_USER_AGENT
    web.settings.javaScriptCanOpenWindowsAutomatically = true
    web.settings.setSupportMultipleWindows(true)
    web.settings.allowContentAccess = true
    web.settings.allowFileAccess = true
    web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
  }

  private fun newTab(url: String) {
    val web = newWebView()
    val tab = BrowserTab(web, "New Tab", normalizeUrl(url))
    tabs.add(tab)
    switchTo(tabs.lastIndex)
    web.loadUrl(tab.url)
    saveOpenTabs()
    focusWebPage()
  }

  private fun switchTo(index: Int) {
    if (index !in tabs.indices) return
    currentIndex = index
    webHolder.removeAllViews()
    webHolder.addView(tabs[index].webView, FrameLayout.LayoutParams(-1, -1))
    titleText.text = tabs[index].title
    addressBar.setText(if (tabs[index].url == "about:blank") "" else tabs[index].url, false)
    refreshTabs()
    saveOpenTabs()
    screen.post { ensurePointerVisible() }
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
    return "https://www.google.com/search?q=${URLEncoder.encode(value, "UTF-8")}"
  }

  private fun activeTab(): BrowserTab? = tabs.getOrNull(currentIndex)
  private fun activeWebView(): WebView? = activeTab()?.webView
  private fun activeTabFor(view: WebView): BrowserTab? = tabs.firstOrNull { it.webView == view }

  private fun openGoogleSignInInside() {
    setFullScreenMode(false, false)
    AlertDialog.Builder(this)
      .setTitle("Google Sign-In")
      .setMessage("I will open Google sign-in inside this browser with desktop mode, cookies and pop-ups enabled. If Google still blocks embedded sign-in, use Outside Browser from the next dialog.")
      .setPositiveButton("Open Inside") { _, _ -> loadInCurrent(GOOGLE_SIGN_IN_URL); focusWebPage() }
      .setNeutralButton("Google Home") { _, _ -> loadInCurrent(GOOGLE_HOME_URL); focusWebPage() }
      .setNegativeButton("Outside Browser") { _, _ -> openOutside(GOOGLE_SIGN_IN_URL) }
      .show()
  }

  private fun openGoogleSignInOutside() {
    openOutside(GOOGLE_SIGN_IN_URL)
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
          "Type google sign in, accounts.google.com or press G to open Google sign-in.\n\n" +
          "If Google says this browser is not secure, press Menu and choose Google Sign-In Outside."
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
      dx > 0 && webPoint.first >= webHolder.width - edge -> activeWebView()?.scrollBy(dp(180), 0)
      dx < 0 && webPoint.first <= edge -> activeWebView()?.scrollBy(-dp(180), 0)
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
      "Return to First Screen",
      if (fullScreen) "Show Controls" else "Full Screen Web Page",
      "Focus Web Page / Pointer",
      "Open Keyboard / Address Bar",
      "Google Sign-In Inside",
      "Google Sign-In Outside",
      "Address Bar Hints",
      "Add Bookmark",
      "Bookmarks",
      "Visited Links",
      "Timed Site Open",
      "Back in Web Page",
      "Home",
      "Open Outside",
      "Download Current File/Page",
      "Open Last Downloaded File",
      "Exit Browser",
      "Cancel"
    )
    AlertDialog.Builder(this)
      .setTitle("Browser Menu")
      .setItems(options) { _, which ->
        longPressMenuShown = false
        when (which) {
          0 -> returnToBrowserButtons()
          1 -> returnToFirstScreen()
          2 -> setFullScreenMode(!fullScreen, true)
          3 -> focusWebPage()
          4 -> focusAddressBar()
          5 -> openGoogleSignInInside()
          6 -> openGoogleSignInOutside()
          7 -> showAddressHints()
          8 -> addCurrentBookmark()
          9 -> showBookmarks()
          10 -> showVisitedLinks()
          11 -> showTimedSiteMenu()
          12 -> goBackOrClose()
          13 -> loadInCurrent(HOME_URL)
          14 -> openOutside(activeTab()?.url ?: HOME_URL)
          15 -> downloadCurrentUrl()
          16 -> openLastDownloadedFile()
          17 -> finish()
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
