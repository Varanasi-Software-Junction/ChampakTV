package com.learnwithchampak.tv

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object TimedSiteScheduler {
  private const val PREFS = "champak_tabs_prefs"
  private const val KEY_ENABLED = "timed_site_enabled"
  private const val KEY_URL = "timed_site_url"
  private const val KEY_MODE = "timed_site_mode"
  private const val KEY_HOUR = "timed_site_hour"
  private const val KEY_MINUTE = "timed_site_minute"
  private const val KEY_INTERVAL = "timed_site_interval_minutes"
  private const val REQUEST_CODE = 21617

  const val MODE_DAILY = "daily"
  const val MODE_INTERVAL = "interval"

  fun currentUrl(context: Context): String =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, "").orEmpty()

  fun currentHour(context: Context): Int =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOUR, 9)

  fun currentMinute(context: Context): Int =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_MINUTE, 0)

  fun currentInterval(context: Context): Int =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_INTERVAL, 30)

  fun saveDaily(context: Context, url: String, hour: Int, minute: Int) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean(KEY_ENABLED, true)
      .putString(KEY_URL, url)
      .putString(KEY_MODE, MODE_DAILY)
      .putInt(KEY_HOUR, hour.coerceIn(0, 23))
      .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
      .apply()
    scheduleNext(context)
  }

  fun saveInterval(context: Context, url: String, minutes: Int) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean(KEY_ENABLED, true)
      .putString(KEY_URL, url)
      .putString(KEY_MODE, MODE_INTERVAL)
      .putInt(KEY_INTERVAL, minutes.coerceIn(1, 10080))
      .apply()
    scheduleNext(context)
  }

  fun disable(context: Context) {
    cancel(context)
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean(KEY_ENABLED, false)
      .apply()
  }

  fun summary(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_ENABLED, false)) return "No timed site is configured."
    val url = prefs.getString(KEY_URL, "").orEmpty()
    return if (prefs.getString(KEY_MODE, MODE_DAILY) == MODE_INTERVAL) {
      "Every ${prefs.getInt(KEY_INTERVAL, 30)} minutes → $url"
    } else {
      val h = prefs.getInt(KEY_HOUR, 9).toString().padStart(2, '0')
      val m = prefs.getInt(KEY_MINUTE, 0).toString().padStart(2, '0')
      "Daily at $h:$m → $url"
    }
  }

  fun scheduleNext(context: Context) {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_ENABLED, false)) {
      cancel(context)
      return
    }
    val url = prefs.getString(KEY_URL, "").orEmpty()
    if (!(url.startsWith("http://") || url.startsWith("https://"))) {
      disable(context)
      return
    }

    val mode = prefs.getString(KEY_MODE, MODE_DAILY)
    val triggerAt = if (mode == MODE_INTERVAL) {
      val minutes = prefs.getInt(KEY_INTERVAL, 30).coerceIn(1, 10080)
      System.currentTimeMillis() + minutes * 60_000L
    } else {
      nextDailyMillis(
        prefs.getInt(KEY_HOUR, 9).coerceIn(0, 23),
        prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
      )
    }

    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pending = pendingIntent(context, url)
    alarm.cancel(pending)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    } else {
      alarm.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }
  }

  fun cancel(context: Context) {
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.cancel(pendingIntent(context, currentUrl(context)))
  }

  private fun pendingIntent(context: Context, url: String): PendingIntent {
    val intent = Intent(context, BrowserActivity::class.java).apply {
      action = "com.learnwithchampak.tv.TIMED_SITE_OPEN"
      putExtra(BrowserActivity.EXTRA_URL, url)
      putExtra(BrowserActivity.EXTRA_TIMED_OPEN, true)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
    return PendingIntent.getActivity(
      context,
      REQUEST_CODE,
      intent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun nextDailyMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    val next = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hour)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    return next.timeInMillis
  }
}
