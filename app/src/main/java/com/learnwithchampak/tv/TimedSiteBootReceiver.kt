package com.learnwithchampak.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimedSiteBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent?) {
    if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
      TimedSiteScheduler.scheduleNext(context)
    }
  }
}
