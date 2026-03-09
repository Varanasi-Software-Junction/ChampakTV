package io.github.programmerspicnic.tv

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.graphics.Bitmap
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

  private lateinit var web: WebView
  private val START_URL = "https://github.com/Varanasi-Software-Junction/ChampakTV"
  private val OFFLINE_URL = "file:///android_asset/offline.html"

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    web = WebView(this)
    web.isFocusable = true
    web.isFocusableInTouchMode = true
    web.descendantFocusability = View.FOCUS_AFTER_DESCENDANTS

    val s = web.settings
    s.javaScriptEnabled = true
    s.domStorageEnabled = true
    s.databaseEnabled = true
    s.loadsImagesAutomatically = true
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    s.mediaPlaybackRequiresUserGesture = false
    s.cacheMode = WebSettings.LOAD_DEFAULT
    s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    s.userAgentString = s.userAgentString + " PP-TV-WebView"

    web.webChromeClient = WebChromeClient()

    web.webViewClient = object : WebViewClient() {
      private var hadMainLoadError = false

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (url == START_URL) hadMainLoadError = false
        super.onPageStarted(view, url, favicon)
      }

      override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame && request.url.toString().startsWith(START_URL)) {
          hadMainLoadError = true
        }
        super.onReceivedError(view, request, error)
      }

      override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (request.isForMainFrame && request.url.toString().startsWith(START_URL)) {
          hadMainLoadError = true
        }
        super.onReceivedHttpError(view, request, errorResponse)
      }

      override fun onPageFinished(view: WebView, url: String?) {
        if (url == START_URL && hadMainLoadError) {
          view.loadUrl(OFFLINE_URL)
          return
        }
        super.onPageFinished(view, url)
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        return false
      }
    }

    setContentView(web)
    web.loadUrl(START_URL)
    web.post { web.requestFocus() }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      if (this::web.isInitialized && web.canGoBack()) {
        web.goBack()
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onDestroy() {
    if (this::web.isInitialized) web.destroy()
    super.onDestroy()
  }
}