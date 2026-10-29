package dev.robertovillegas.inicio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * A firmware update can hand the Home button back to Google TV. Android reconnects MediaListener after every
 * boot and whenever the process dies, so this runs even when nobody opens Inicio: if Inicio was the home
 * screen and no longer is, it opens once per boot and says how to fix it. The idea comes from
 * GuardRevival in cleanyourtv.
 */
object HomeGuard {
    const val EXTRA_LOST_HOME = "lost_home"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("home_guard", Context.MODE_PRIVATE)

    private fun isHome(ctx: Context) = ctx.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == ctx.packageName

    /** Called whenever the home screen shows. Opened as a plain app (after restore-launcher.sh), it stops guarding. */
    fun remember(ctx: Context) = prefs(ctx).edit().putBoolean("was_home", isHome(ctx)).apply()

    fun check(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("was_home", false) || isHome(ctx)) return
        // The boot time computed this way jitters by a few milliseconds.
        val bootedAt = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (abs(p.getLong("warned_boot", 0) - bootedAt) < 60_000) return
        p.edit().putLong("warned_boot", bootedAt).apply()
        ctx.startActivity(
            Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_LOST_HOME, true),
        )
    }
}
