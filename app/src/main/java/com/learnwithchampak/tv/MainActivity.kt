package com.learnwithchampak.tv

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  private lateinit var prefs: SharedPreferences
  private val homeUrl = "https://www.learnwithchampak.live"
  private val apkUrl = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv.apk?v=2.10"
  private val versionUrl = "https://programmer-s-picnic.github.io/json-images/tv/champak-tv-version.json"
  private val windowsUrl = "https://programmer-s-picnic.github.io/json-images/windows/learn-with-champak-windows-setup.exe"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = getSharedPreferences("champak_browser_prefs", Context.MODE_PRIVATE)
    if (resources.configuration.screenWidthDp >= 700) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    buildScreen()
    askDefaultOnFirstRun()
  }

  private fun buildScreen() {
    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(dp(24), dp(24), dp(24), dp(24))
      background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(3, 19, 46), Color.rgb(8, 74, 128), Color.rgb(2, 13, 28)))
    }
    root.addView(label("Learn With Champak", 38f, Color.WHITE, true))
    root.addView(label("AI • ML • Python • DSA • Programming", 18f, Color.rgb(255, 221, 128), true))
    root.addView(label("TV Remote Ready Browser and Learning Launcher", 15f, Color.rgb(218, 240, 255), false))
    root.addView(label("Use DPAD Up/Down and OK. The selected button gets a bright border and grows.", 13f, Color.rgb(255, 250, 200), false))
    root.addView(space(16))

    val firstButton = action("Open Blank Browser", "Blank address bar + keyboard") { openInside("Blank Browser", "about:blank") }
    root.addView(firstButton)
    root.addView(action("Learn With Champak", homeUrl) { openInside("Learn With Champak", homeUrl) })
    root.addView(action("Google Sign-in Helper", "Open Google account page in system browser") { openExternal("https://accounts.google.com/") })
    root.addView(action("Set As Default Browser", "Open Android Default apps screen") { openDefaultSettings() })
    root.addView(action("Open Android APK Update", "Download latest Android APK") { openExternal(apkUrl) })
    root.addView(action("Allow APK Install", "Open install unknown apps setting") { openInstallPermissionSettings() })
    root.addView(action("Windows Setup EXE", "Download Windows installer") { openExternal(windowsUrl) })
    root.addView(action("Version JSON", versionUrl) { openExternal(versionUrl) })

    root.addView(space(10))
    root.addView(label("From browser: press Menu or long-press OK, then choose Return to Buttons.", 13f, Color.WHITE, false))
    setContentView(root)
    firstButton.requestFocus()
  }

  private fun askDefaultOnFirstRun() {
    val key = "android_default_browser_prompted_v210"
    if (prefs.getBoolean(key, false)) return
    prefs.edit().putBoolean(key, true).apply()
    AlertDialog.Builder(this)
      .setTitle("Set as default browser?")
      .setMessage("Android does not allow an app to change this silently. Open Default apps and choose Learn With Champak as Browser app for web links.")
      .setPositiveButton("Open Default Apps") { _, _ -> openDefaultSettings() }
      .setNegativeButton("Later", null)
      .show()
  }

  private fun openInside(title: String, url: String) {
    startActivity(Intent(this, BrowserActivity::class.java).apply {
      putExtra(BrowserActivity.EXTRA_TITLE, title)
      putExtra(BrowserActivity.EXTRA_URL, url)
    })
  }

  private fun openDefaultSettings() {
    try { startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) }
    catch (_: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
  }

  private fun openInstallPermissionSettings() {
    try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
    catch (_: Exception) { Toast.makeText(this, "Open Settings > Install unknown apps", Toast.LENGTH_LONG).show() }
  }

  private fun openExternal(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    catch (_: Exception) { Toast.makeText(this, "No browser found", Toast.LENGTH_LONG).show() }
  }

  private fun action(title: String, sub: String, action: () -> Unit): Button = Button(this).apply {
    text = "$title\n$sub"
    textSize = 15f
    isAllCaps = false
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    typeface = Typeface.DEFAULT_BOLD
    background = buttonBg(false)
    setPadding(dp(14), dp(5), dp(14), dp(5))
    isFocusable = true
    isFocusableInTouchMode = true
    minHeight = dp(58)
    setOnFocusChangeListener { v, hasFocus ->
      v.background = buttonBg(hasFocus)
      v.scaleX = if (hasFocus) 1.055f else 1f
      v.scaleY = if (hasFocus) 1.055f else 1f
      v.elevation = if (hasFocus) dp(16).toFloat() else dp(3).toFloat()
      (v as Button).setTextColor(if (hasFocus) Color.rgb(2, 28, 58) else Color.WHITE)
    }
    setOnClickListener { action() }
    layoutParams = LinearLayout.LayoutParams(-1, dp(62)).apply { setMargins(0, dp(4), 0, dp(4)) }
  }

  private fun buttonBg(focused: Boolean): GradientDrawable {
    return if (focused) {
      rounded(Color.rgb(255, 238, 120), dp(18), Color.WHITE, dp(5))
    } else {
      rounded(Color.rgb(8, 77, 138), dp(14), Color.rgb(82, 204, 255), dp(2))
    }
  }

  private fun label(text: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
    this.text = text
    textSize = size
    setTextColor(color)
    gravity = Gravity.CENTER
    if (bold) typeface = Typeface.DEFAULT_BOLD
    setPadding(0, dp(3), 0, dp(3))
  }

  private fun space(h: Int) = TextView(this).apply { height = dp(h) }
  private fun rounded(c: Int, r: Int, s: Int, w: Int) = GradientDrawable().apply { setColor(c); cornerRadius = r.toFloat(); setStroke(w, s) }
  private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
