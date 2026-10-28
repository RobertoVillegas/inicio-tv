package dev.robertovillegas.inicio

import android.content.Context
import android.util.Log
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class Clima(
    val ciudad: String,
    val temperatura: Int,
    val maxima: Int,
    val minima: Int,
    val estado: String,
    val proximas: List<Pair<LocalDateTime, Int>>,
    val amanecer: LocalDateTime,
    val atardecer: LocalDateTime,
    val amanecerManana: LocalDateTime,
) {
    /** Noche de verdad, según el sol de la ciudad, no un horario fijo. */
    fun esDeNoche(ahora: LocalDateTime = LocalDateTime.now()) = ahora.isAfter(atardecer) || ahora.isBefore(amanecer)
}

/** Clima local con Open-Meteo; la ubicación de la TV sale de su IP pública. Ninguno pide llave. */
object ClimaLocal {
    private const val TAG = "Clima"
    private const val UN_DIA = 24L * 3600 * 1000

    @Volatile
    var ultimo: Clima? = null
        private set

    fun obtener(ctx: Context): Clima? = try {
        val (lat, lon, ciudad) = ubicacion(ctx)
        val r = Red.json(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&timezone=auto&forecast_days=2"
        )
        val actual = r.getJSONObject("current")
        val diario = r.getJSONObject("daily")
        val horas = r.getJSONObject("hourly")
        val tiempos = horas.getJSONArray("time")
        val temps = horas.getJSONArray("temperature_2m")
        val ahora = LocalDateTime.now()
        // Tres puntos del pronóstico, cada tres horas a partir de la siguiente hora en punto.
        val proximas = (0 until tiempos.length())
            .map { LocalDateTime.parse(tiempos.getString(it)) to temps.getDouble(it).roundToInt() }
            .filter { it.first.isAfter(ahora) }
            .filterIndexed { i, _ -> i % 3 == 2 }
            .take(3)
        Clima(
            ciudad,
            actual.getDouble("temperature_2m").roundToInt(),
            diario.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            diario.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            estado(actual.getInt("weather_code")),
            proximas,
            LocalDateTime.parse(diario.getJSONArray("sunrise").getString(0)),
            LocalDateTime.parse(diario.getJSONArray("sunset").getString(0)),
            LocalDateTime.parse(diario.getJSONArray("sunrise").getString(1)),
        ).also { ultimo = it }
    } catch (e: Exception) {
        Log.w(TAG, "Sin clima", e)
        null
    }

    private fun ubicacion(ctx: Context): Triple<Double, Double, String> {
        val prefs = ctx.getSharedPreferences("clima", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("cuando", 0) < UN_DIA) {
            return Triple(
                prefs.getFloat("lat", 0f).toDouble(),
                prefs.getFloat("lon", 0f).toDouble(),
                prefs.getString("ciudad", "")!!,
            )
        }
        val r = Red.json("https://ipapi.co/json/")
        val lat = r.getDouble("latitude")
        val lon = r.getDouble("longitude")
        val ciudad = r.optString("city")
        prefs.edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .putString("ciudad", ciudad)
            .putLong("cuando", System.currentTimeMillis())
            .apply()
        return Triple(lat, lon, ciudad)
    }

    // Códigos WMO que usa Open-Meteo.
    private fun estado(codigo: Int) = when (codigo) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        51, 53, 55, 56, 57 -> "Llovizna"
        61, 63, 66 -> "Lluvia"
        65, 67 -> "Lluvia fuerte"
        71, 73, 75, 77 -> "Nieve"
        80, 81, 82 -> "Chubascos"
        85, 86 -> "Chubascos de nieve"
        95, 96, 99 -> "Tormenta"
        else -> ""
    }
}
