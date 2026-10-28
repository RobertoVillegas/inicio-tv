package dev.robertovillegas.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

data class Foto(
    val archivo: File,
    val lugar: String,
    val descripcion: String,
    val autor: String,
    val licencia: String,
)

/**
 * Fotos de naturaleza destacadas de Wikimedia Commons, guardadas en el equipo para que
 * el inicio y el protector de pantalla funcionen sin red.
 */
object Fondos {
    private const val TAG = "Fondos"
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private val CATEGORIAS = listOf(
        "Featured_pictures_of_mountains",
        "Featured_pictures_of_lakes",
        "Featured_pictures_of_forests",
        "Featured_pictures_of_beaches",
        "Featured_pictures_of_waterfalls",
        "Featured_pictures_of_coasts",
    )

    // Las categorías de Commons incluyen trenes, pueblos y monumentos; se descartan por texto.
    private val NO_NATURALEZA = listOf(
        "train", "railway", "station", "emu", "locomotive", "car ", "bus ", "airport",
        "church", "chapel", "cathedral", "castle", "temple", "monument", "museum",
        "village", "town", "city", "street", "building", "house", "bridge", "harbour", "port of",
        "monumento", "iglesia", "castillo", "pueblo", "ciudad",
    )

    // La interfaz de la TV se dibuja a 1920x1080; una foto más grande solo gasta memoria.
    private const val ANCHO = 1920
    private const val MAXIMO = 40
    private const val NUEVAS_POR_RONDA = 6
    private const val UNA_SEMANA = 7L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "fondos").apply { mkdirs() }
    private fun indice(ctx: Context) = File(dir(ctx), "indice.json")
    private fun titulos(ctx: Context) = File(dir(ctx), "titulos.json")

    @Synchronized
    fun listar(ctx: Context): List<Foto> {
        val f = indice(ctx)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
            val archivo = File(dir(ctx), o.getString("archivo"))
            if (!archivo.exists()) null else Foto(
                archivo,
                o.optString("lugar"),
                o.optString("descripcion"),
                o.optString("autor"),
                o.optString("licencia"),
            )
        }
    }

    fun decodificar(foto: Foto): Bitmap? =
        BitmapFactory.decodeFile(foto.archivo.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })

    /** Baja algunas fotos nuevas y descarta las más viejas. Bloquea: llamar fuera del hilo principal. */
    fun refrescar(ctx: Context) {
        val conocidas = listar(ctx).map { it.archivo.name }.toSet()
        val candidatos = titulosDisponibles(ctx).shuffled()
        var nuevas = 0
        for (titulo in candidatos) {
            if (nuevas >= NUEVAS_POR_RONDA) break
            if (nombreArchivo(titulo) in conocidas) continue
            try {
                if (bajar(ctx, titulo)) nuevas++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo bajar $titulo", e)
            }
        }
        recortar(ctx)
    }

    private fun titulosDisponibles(ctx: Context): List<String> {
        val f = titulos(ctx)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < UNA_SEMANA) {
            val arr = JSONArray(f.readText())
            return (0 until arr.length()).map { arr.getString(it) }
        }
        val todos = mutableListOf<String>()
        for (cat in CATEGORIAS) {
            var cont: String? = null
            do {
                val url = "$API?action=query&list=categorymembers&cmtitle=Category:$cat" +
                    "&cmtype=file&cmlimit=500&format=json" +
                    (cont?.let { "&cmcontinue=" + enc(it) } ?: "")
                val r = Red.json(url)
                val miembros = r.getJSONObject("query").getJSONArray("categorymembers")
                for (i in 0 until miembros.length()) todos += miembros.getJSONObject(i).getString("title")
                cont = r.optJSONObject("continue")?.optString("cmcontinue")
            } while (!cont.isNullOrEmpty())
        }
        f.writeText(JSONArray(todos).toString())
        return todos
    }

    private fun bajar(ctx: Context, titulo: String): Boolean {
        val url = "$API?action=query&titles=${enc(titulo)}&prop=imageinfo|coordinates" +
            "&iiprop=url|size|extmetadata&iiurlwidth=$ANCHO" +
            "&iiextmetadatafilter=ObjectName|ImageDescription|Artist|LicenseShortName" +
            "&format=json"
        val pagina = Red.json(url).getJSONObject("query").getJSONObject("pages")
            .let { it.getJSONObject(it.keys().next()) }
        val info = pagina.optJSONArray("imageinfo")?.optJSONObject(0) ?: return false
        val ancho = info.optInt("width")
        val alto = info.optInt("height")
        // Solo fotos horizontales que llenen la pantalla sin recortes extremos.
        val proporcion = ancho.toFloat() / alto
        if (ancho < ANCHO || proporcion < 1.4f || proporcion > 2.1f) return false
        val thumb = info.optString("thumburl").ifEmpty { return false }

        val meta = info.optJSONObject("extmetadata") ?: JSONObject()
        val descripcion = texto(meta, "ImageDescription").ifEmpty { texto(meta, "ObjectName") }
        val todo = "$titulo $descripcion".lowercase()
        if (NO_NATURALEZA.any { it in todo }) return false
        val lugar = pagina.optJSONArray("coordinates")?.optJSONObject(0)?.let {
            lugarDe(it.getDouble("lat"), it.getDouble("lon"))
        } ?: ""

        val archivo = nombreArchivo(titulo)
        Red.descargar(thumb, File(dir(ctx), archivo))
        agregar(ctx, JSONObject().apply {
            put("archivo", archivo)
            put("lugar", lugar)
            put("descripcion", descripcion.take(140))
            put("autor", texto(meta, "Artist").take(60))
            put("licencia", texto(meta, "LicenseShortName"))
        })
        return true
    }

    private fun lugarDe(lat: Double, lon: Double): String = try {
        val r = Red.json(
            "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon" +
                "&format=json&zoom=10&accept-language=es"
        )
        val a = r.optJSONObject("address") ?: JSONObject()
        val sitio = listOf("village", "town", "city", "municipality", "county")
            .map { a.optString(it) }.firstOrNull { it.isNotEmpty() }
        listOfNotNull(sitio, a.optString("state").ifEmpty { null }, a.optString("country").ifEmpty { null })
            .distinct().joinToString(", ")
    } catch (e: Exception) {
        Log.w(TAG, "Sin geocodificación para $lat,$lon", e)
        ""
    }

    @Synchronized
    private fun agregar(ctx: Context, o: JSONObject) {
        val f = indice(ctx)
        val arr = if (f.exists()) JSONArray(f.readText()) else JSONArray()
        arr.put(o)
        f.writeText(arr.toString())
    }

    @Synchronized
    private fun recortar(ctx: Context) {
        val f = indice(ctx)
        if (!f.exists()) return
        val arr = JSONArray(f.readText())
        val todas = (0 until arr.length()).map { arr.getJSONObject(it) }
        val (validas, descartadas) = todas.partition { o ->
            NO_NATURALEZA.none { it in o.optString("descripcion").lowercase() }
        }
        val sobran = (validas.size - MAXIMO).coerceAtLeast(0)
        val quedan = JSONArray()
        (descartadas + validas.take(sobran)).forEach { File(dir(ctx), it.getString("archivo")).delete() }
        validas.drop(sobran).forEach { quedan.put(it) }
        f.writeText(quedan.toString())
    }

    private fun texto(meta: JSONObject, campo: String): String {
        val html = meta.optJSONObject(campo)?.optString("value") ?: return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun nombreArchivo(titulo: String) = Integer.toHexString(titulo.hashCode()) + ".jpg"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
