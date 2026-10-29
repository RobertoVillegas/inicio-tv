package dev.robertovillegas.inicio

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Wikimedia and Nominatim ask for a User-Agent that identifies the app.
private const val USER_AGENT = "InicioTV/0.5 (https://github.com/RobertoVillegas/inicio-tv)"

object Net {
    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    fun json(url: String): JSONObject {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} for $url")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    fun download(url: String, target: File) {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} for $url")
            val tmp = File(target.path + ".tmp")
            c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(target)
        } finally {
            c.disconnect()
        }
    }
}
