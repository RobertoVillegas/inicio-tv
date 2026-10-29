package dev.robertovillegas.inicio

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.time.LocalDateTime

/**
 * The TV turns off sooner at night. Writing sleep_timeout needs WRITE_SECURE_SETTINGS, granted over ADB
 * by install-inicio.sh.
 */
object Night {
    private const val SLEEP_DAY = 1_139_000      // ~19 min, the factory value
    private const val SLEEP_NIGHT = 600_000      // 10 min

    fun isNight(now: LocalDateTime = LocalDateTime.now()): Boolean =
        LocalWeather.last?.isNight(now) ?: (now.hour >= 21 || now.hour < 7)

    fun applySleepTimeout(ctx: Context, night: Boolean) {
        val wanted = if (night) SLEEP_NIGHT else SLEEP_DAY
        try {
            val cr = ctx.contentResolver
            if (Settings.Secure.getInt(cr, "sleep_timeout", -1) != wanted) {
                Settings.Secure.putInt(cr, "sleep_timeout", wanted)
            }
        } catch (e: SecurityException) {
            Log.i("Night", "No WRITE_SECURE_SETTINGS; leaving the sleep timeout alone")
        }
    }
}
