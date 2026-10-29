package dev.robertovillegas.inicio

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import kotlin.math.abs

/**
 * Una actualización de la TV puede regresarle el botón Home a Google TV. Android vuelve a conectar
 * a Escucha al arrancar y cada vez que el proceso muere, así que esto corre aunque nadie abra Inicio:
 * si Inicio era la pantalla de inicio y dejó de serlo, se abre una vez por arranque y dice cómo
 * arreglarlo. La idea es de GuardRevival, en cleanyourtv.
 */
object Guardia {
    const val AVISO = "aviso_home"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("guardia", Context.MODE_PRIVATE)

    private fun esInicio(ctx: Context) = ctx.packageManager.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName == ctx.packageName

    /** Lo llama el inicio al mostrarse. Abierto como app normal, después de rescate-launcher.sh, deja de vigilar. */
    fun anotar(ctx: Context) = prefs(ctx).edit().putBoolean("era", esInicio(ctx)).apply()

    fun revisar(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("era", false) || esInicio(ctx)) return
        // La hora del arranque calculada así varía unos milisegundos; un minuto de margen basta.
        val arranque = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        if (abs(p.getLong("avisado", 0) - arranque) < 60_000) return
        p.edit().putLong("avisado", arranque).apply()
        ctx.startActivity(
            Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(AVISO, true),
        )
    }
}
