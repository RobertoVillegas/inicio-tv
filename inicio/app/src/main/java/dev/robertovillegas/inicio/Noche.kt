package dev.robertovillegas.inicio

import android.content.Context
import android.provider.Settings
import android.util.Log
import java.time.LocalDateTime

/**
 * De noche la TV se apaga antes. Escribir sleep_timeout requiere WRITE_SECURE_SETTINGS,
 * que se concede por ADB (`pm grant dev.robertovillegas.inicio android.permission.WRITE_SECURE_SETTINGS`).
 */
object Noche {
    private const val APAGADO_DIA = 1_139_000      // ~19 min, como venía de fábrica
    private const val APAGADO_NOCHE = 600_000      // 10 min

    fun esDeNoche(ahora: LocalDateTime = LocalDateTime.now()): Boolean =
        ClimaLocal.ultimo?.esDeNoche(ahora) ?: (ahora.hour >= 21 || ahora.hour < 7)

    fun ajustarApagado(ctx: Context, noche: Boolean) {
        val deseado = if (noche) APAGADO_NOCHE else APAGADO_DIA
        try {
            val cr = ctx.contentResolver
            if (Settings.Secure.getInt(cr, "sleep_timeout", -1) != deseado) {
                Settings.Secure.putInt(cr, "sleep_timeout", deseado)
            }
        } catch (e: SecurityException) {
            Log.i("Noche", "Sin WRITE_SECURE_SETTINGS; el apagado queda como esté")
        }
    }
}
