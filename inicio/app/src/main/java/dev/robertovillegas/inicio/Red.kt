package dev.robertovillegas.inicio

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Wikimedia y Nominatim piden un User-Agent que identifique la app.
private const val USER_AGENT = "InicioTV/0.1 (https://github.com/RobertoVillegas/tcl-tv)"

object Red {
    private fun abrir(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    fun json(url: String): JSONObject {
        val c = abrir(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} en $url")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    fun descargar(url: String, destino: File) {
        val c = abrir(url)
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode} en $url")
            val tmp = File(destino.path + ".tmp")
            c.inputStream.use { entrada -> tmp.outputStream().use { entrada.copyTo(it) } }
            tmp.renameTo(destino)
        } finally {
            c.disconnect()
        }
    }
}
