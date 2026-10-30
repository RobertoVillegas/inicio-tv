package dev.robertovillegas.inicio

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * The remote's app buttons. On Google TV, TCL's system hands them to Google's home screen, which Inicio
 * replaces and set-home.sh disables, so nothing answered them. Seeing keys from any app takes an
 * accessibility service; install-inicio.sh turns it on.
 *
 * On a TCL the power button may not wake the set while an accessibility service is bound (cleanyourtv saw it
 * on a tc8000), so the service turns itself off with the screen and MediaListener turns it back on.
 */
class RemoteButtons : AccessibilityService() {
    companion object {
        private const val SETTING = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES

        // TCL's own key codes, read off the RC813 remote.
        private val APPS = mapOf(
            4062 to "com.netflix.ninja",
            4065 to "com.google.android.youtube.tv",
            4099 to "com.amazon.amazonvideo.livingroom",
            4160 to "com.apple.atve.androidtv.appletv",
        )

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("remote_buttons", Context.MODE_PRIVATE)

        /** Back on after the screen was off, unless someone turned the service off in Settings meanwhile. */
        fun resume(ctx: Context) {
            if (!prefs(ctx).getBoolean("paused", false)) return
            val me = ComponentName(ctx, RemoteButtons::class.java).flattenToShortString()
            val cr = ctx.contentResolver
            val enabled = Settings.Secure.getString(cr, SETTING).orEmpty().split(":").filter { it.isNotEmpty() }
            try {
                if (me !in enabled) Settings.Secure.putString(cr, SETTING, (enabled + me).joinToString(":"))
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                prefs(ctx).edit().putBoolean("paused", false).apply()
            } catch (e: SecurityException) {
                Log.i("RemoteButtons", "No WRITE_SECURE_SETTINGS; the remote's app buttons stay off")
            }
        }
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            prefs(ctx).edit().putBoolean("paused", true).commit()
            disableSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onDestroy() {
        unregisterReceiver(screenOff)
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val pkg = APPS[event.keyCode] ?: return false
        val launch = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Apps.launch(this, launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}
}
