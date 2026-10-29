package dev.robertovillegas.inicio

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import java.io.File

/**
 * Revisa el último release en GitHub y, si es más nuevo, lo instala con el instalador de Android.
 * La TV pide confirmar con el control; Android no deja a una app normal instalar en silencio.
 * Para poder abrir el instalador hace falta `appops set dev.robertovillegas.inicio REQUEST_INSTALL_PACKAGES allow`.
 */
object Actualizacion {
    private const val ULTIMO = "https://api.github.com/repos/RobertoVillegas/tcl-tv/releases/latest"

    data class Version(val nombre: String, val apk: String)

    /** La versión publicada si es más nueva que la instalada; si no, null. */
    @Volatile var disponible: Version? = null
        private set

    fun revisar(ctx: Context) {
        try {
            val r = Red.json(ULTIMO)
            val nombre = r.getString("tag_name").removePrefix("v")
            val assets = r.getJSONArray("assets")
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name") == "inicio.apk" }
                ?.getString("browser_download_url") ?: return
            disponible = Version(nombre, apk).takeIf { esMasNueva(nombre, instalada(ctx)) }
        } catch (e: Exception) {
            Log.i("Actualizacion", "No se pudo revisar: ${e.message}")
        }
    }

    fun instalada(ctx: Context): String =
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0"

    private fun esMasNueva(publicada: String, instalada: String): Boolean {
        val a = publicada.split('.').map { it.toIntOrNull() ?: 0 }
        val b = instalada.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Descarga e instala. Corre en un hilo de trabajo; la TV muestra la confirmación al final. */
    fun instalar(ctx: Context, v: Version) {
        val archivo = File(ctx.cacheDir, "inicio.apk")
        Red.descargar(v.apk, archivo)
        val instalador = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(ctx.packageName) }
        val id = instalador.createSession(params)
        instalador.openSession(id).use { sesion ->
            sesion.openWrite("inicio.apk", 0, archivo.length()).use { salida ->
                archivo.inputStream().use { it.copyTo(salida) }
                sesion.fsync(salida)
            }
            val aviso = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, Resultado::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            sesion.commit(aviso.intentSender)
        }
        archivo.delete()
    }

    /** El instalador avisa aquí; si pide confirmación, se abre la pantalla para aceptar. */
    class Resultado : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (val estado = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    @Suppress("DEPRECATION")
                    val confirmar = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                    ctx.startActivity(confirmar.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                PackageInstaller.STATUS_SUCCESS -> Unit
                else -> Log.w("Actualizacion", "Falló ($estado): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
            }
        }
    }
}
