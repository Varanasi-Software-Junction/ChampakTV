package com.learnwithchampak.tv

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Compatibility activity kept for older shortcuts/installations.
 * New launches go directly to GeckoBrowserActivity from AndroidManifest.xml.
 */
class MainActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startActivity(Intent(this, GeckoBrowserActivity::class.java))
    finish()
  }
}
