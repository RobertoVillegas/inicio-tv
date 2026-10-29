package dev.robertovillegas.inicio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import java.io.File

/**
 * Checks the latest GitHub release and installs it through Android's package installer. The TV asks for
 * a confirmation on the remote: Android doesn't let a regular app install silently. Opening the installer
 * needs `appops set dev.robertovillegas.inicio REQUEST_INSTALL_PACKAGES allow` (install-inicio.sh).
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/RobertoVillegas/inicio-tv/releases/latest"

    data class Version(val name: String, val apkUrl: String)

    /** The published version when it's newer than the installed one. */
    @Volatile var available: Version? = null
        private set

    fun check(ctx: Context) {
        try {
            val r = Net.json(LATEST)
            val name = r.getString("tag_name").removePrefix("v")
            val assets = r.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == "inicio.apk" }
                ?.getString("browser_download_url") ?: return
            available = Version(name, apk).takeIf { isNewer(name, installed(ctx)) }
        } catch (e: Exception) {
            Log.i("Updater", "Update check failed: ${e.message}")
        }
    }

    private fun installed(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    private fun isNewer(published: String, installed: String): Boolean {
        val a = published.split('.').map { it.toIntOrNull() ?: 0 }
        val b = installed.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Blocking: call off the main thread. The TV shows the confirmation at the end. */
    fun install(ctx: Context, v: Version) {
        val file = File(ctx.cacheDir, "inicio.apk")
        Net.download(v.apkUrl, file)
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(ctx.packageName) }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("inicio.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val callback = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, Result::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
        file.delete()
    }

    /** The installer reports here; when it needs a confirmation, this opens the screen to accept it. */
    class Result : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                else -> Log.w("Updater", "Install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }
}
