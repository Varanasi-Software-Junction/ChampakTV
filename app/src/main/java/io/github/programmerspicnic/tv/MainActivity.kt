package io.github.programmerspicnic.tv

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

  private lateinit var statusText: TextView
  private lateinit var helloText: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d("ChampakTV", "Hello World TV app started")

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER
      setPadding(64, 48, 64, 48)
      background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.rgb(20, 10, 5), Color.rgb(70, 30, 10))
      )
      isFocusable = true
      isFocusableInTouchMode = true
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }

    helloText = TextView(this).apply {
      text = "Hello Champak TV"
      textSize = 48f
      setTextColor(Color.WHITE)
      typeface = Typeface.DEFAULT_BOLD
      gravity = Gravity.CENTER
    }

    val subtitle = TextView(this).apply {
      text = "A fresh Android TV app begins here"
      textSize = 24f
      setTextColor(Color.rgb(255, 215, 150))
      gravity = Gravity.CENTER
      setPadding(0, 16, 0, 32)
    }

    statusText = TextView(this).apply {
      text = "Use TV remote: Up / Down / OK"
      textSize = 22f
      setTextColor(Color.rgb(255, 238, 180))
      gravity = Gravity.CENTER
      setPadding(0, 0, 0, 36)
    }

    val startButton = tvButton("Start") {
      helloText.text = "Remote OK works"
      statusText.text = "Start button selected"
      Log.d("ChampakTV", "Start button clicked")
    }

    val exitButton = tvButton("Exit") {
      statusText.text = "Exit selected"
      Log.d("ChampakTV", "Exit button clicked")
      finish()
    }

    root.addView(helloText, LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    root.addView(subtitle, LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    root.addView(statusText, LinearLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.WRAP_CONTENT
    ))
    root.addView(startButton, buttonLayoutParams())
    root.addView(exitButton, buttonLayoutParams())

    setContentView(root)

    startButton.post {
      startButton.requestFocus()
    }
  }

  private fun tvButton(label: String, onClick: () -> Unit): Button {
    return Button(this).apply {
      text = label
      textSize = 26f
      setTextColor(Color.WHITE)
      isAllCaps = false
      isFocusable = true
      isFocusableInTouchMode = true
      minHeight = 86
      setPadding(40, 16, 40, 16)
      background = buttonBackground(false)

      setOnFocusChangeListener { view, hasFocus ->
        view.background = buttonBackground(hasFocus)
        statusText.text = if (hasFocus) "Focused: $label" else statusText.text
        Log.d("ChampakTV", "Focus ${if (hasFocus) "entered" else "left"}: $label")
      }

      setOnClickListener {
        onClick()
      }
    }
  }

  private fun buttonLayoutParams(): LinearLayout.LayoutParams {
    return LinearLayout.LayoutParams(420, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
      topMargin = 18
      bottomMargin = 18
    }
  }

  private fun buttonBackground(focused: Boolean): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 24f
      setColor(if (focused) Color.rgb(255, 140, 0) else Color.rgb(95, 45, 20))
      setStroke(
        if (focused) 6 else 2,
        if (focused) Color.WHITE else Color.rgb(180, 120, 70)
      )
    }
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    if (event.action == KeyEvent.ACTION_DOWN) {
      val keyName = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> "UP"
        KeyEvent.KEYCODE_DPAD_DOWN -> "DOWN"
        KeyEvent.KEYCODE_DPAD_LEFT -> "LEFT"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "RIGHT"
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER -> "OK"
        KeyEvent.KEYCODE_BACK -> "BACK"
        else -> null
      }

      if (keyName != null) {
        statusText.text = "Key pressed: $keyName"
        Log.d("ChampakTV", "Key pressed: $keyName")
      }
    }

    return super.dispatchKeyEvent(event)
  }
}
