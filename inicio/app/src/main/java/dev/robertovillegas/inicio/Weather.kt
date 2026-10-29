package dev.robertovillegas.inicio

import android.content.Context
import android.util.Log
import java.time.LocalDateTime
import kotlin.math.roundToInt

data class Weather(
    val city: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val condition: String,
    val nextHours: List<Pair<LocalDateTime, Int>>,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val sunriseTomorrow: LocalDateTime,
) {
    /** Night by the city's actual sun, not a fixed schedule. */
    fun isNight(now: LocalDateTime = LocalDateTime.now()) = now.isAfter(sunset) || now.isBefore(sunrise)
}

/** Local weather from Open-Meteo; the TV's location comes from its public IP. Neither needs a key. */
object LocalWeather {
    private const val TAG = "Weather"
    private const val ONE_DAY = 24L * 3600 * 1000

    @Volatile
    var last: Weather? = null
        private set

    fun fetch(ctx: Context): Weather? = try {
        val (lat, lon, city) = location(ctx)
        val r = Net.json(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&hourly=temperature_2m" +
                "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                "&timezone=auto&forecast_days=2"
        )
        val current = r.getJSONObject("current")
        val daily = r.getJSONObject("daily")
        val hourly = r.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val now = LocalDateTime.now()
        // Three forecast points, every three hours from the next full hour.
        val nextHours = (0 until times.length())
            .map { LocalDateTime.parse(times.getString(it)) to temps.getDouble(it).roundToInt() }
            .filter { it.first.isAfter(now) }
            .filterIndexed { i, _ -> i % 3 == 2 }
            .take(3)
        Weather(
            city,
            current.getDouble("temperature_2m").roundToInt(),
            daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            condition(current.getInt("weather_code")),
            nextHours,
            LocalDateTime.parse(daily.getJSONArray("sunrise").getString(0)),
            LocalDateTime.parse(daily.getJSONArray("sunset").getString(0)),
            LocalDateTime.parse(daily.getJSONArray("sunrise").getString(1)),
        ).also { last = it }
    } catch (e: Exception) {
        Log.w(TAG, "No weather", e)
        null
    }

    private fun location(ctx: Context): Triple<Double, Double, String> {
        val prefs = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)
        if (System.currentTimeMillis() - prefs.getLong("fetched_at", 0) < ONE_DAY) {
            return Triple(
                prefs.getFloat("lat", 0f).toDouble(),
                prefs.getFloat("lon", 0f).toDouble(),
                prefs.getString("city", "")!!,
            )
        }
        val r = Net.json("https://ipapi.co/json/")
        val lat = r.getDouble("latitude")
        val lon = r.getDouble("longitude")
        val city = r.optString("city")
        prefs.edit()
            .putFloat("lat", lat.toFloat())
            .putFloat("lon", lon.toFloat())
            .putString("city", city)
            .putLong("fetched_at", System.currentTimeMillis())
            .apply()
        return Triple(lat, lon, city)
    }

    // WMO codes, as Open-Meteo reports them. Shown on screen, so in Spanish.
    private fun condition(code: Int) = when (code) {
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
