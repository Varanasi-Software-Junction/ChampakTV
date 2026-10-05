package com.learnwithchampak.tv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Compatibility activity kept for older shortcuts/installations.
 * New launches go directly to BrowserActivity from AndroidManifest.xml.
 */
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(Intent(this, BrowserActivity::class.java))
    finish()
  }
}
