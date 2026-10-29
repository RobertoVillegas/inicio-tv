package dev.robertovillegas.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.text.Html
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Foto(
    val archivo: File,
    val lugar: String,
    val descripcion: String,
    val autor: String,
    val licencia: String,
    val favorita: Boolean,
    /** Subida desde el celular; nunca se borra al hacer espacio. */
    val propia: Boolean = false,
    /** Si entra en la rotación (solo se puede sacar una propia). */
    val incluida: Boolean = true,
    /** Propia que llena la pantalla recortada; si no, se ve completa sobre un fondo difuminado. */
    val llenar: Boolean = true,
)

enum class Modo { MEZCLA, MIAS, PAISAJES }

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
        "neighbourhood", "neighborhood", "commune", "district", "skyline", "wind farm", "windmill",
        "monumento", "iglesia", "castillo", "pueblo", "ciudad",
    )

    // La interfaz de la TV se dibuja a 1920x1080; una foto más grande solo gasta memoria.
    private const val ANCHO = 1920
    private const val ALTO = 1080
    private const val MAXIMO = 40
    private const val NUEVAS_POR_RONDA = 6
    private const val UNA_SEMANA = 7L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "fondos").apply { mkdirs() }
    private fun indice(ctx: Context) = File(dir(ctx), "indice.json")
    private fun titulos(ctx: Context) = File(dir(ctx), "titulos.json")
    private fun vetadas(ctx: Context) = File(dir(ctx), "vetadas.json")

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
                o.optBoolean("favorita"),
                o.optBoolean("propia"),
                o.optBoolean("incluida", true),
                o.optBoolean("llenar", true),
            )
        }
    }

    /** Las fotos que van a salir: la fija si hay; si no, según el modo (sin las propias que se sacaron). */
    fun rotacion(ctx: Context): List<Foto> {
        val todas = listar(ctx)
        fija(ctx)?.let { nombre -> todas.firstOrNull { it.archivo.name == nombre }?.let { return listOf(it) } }
        val mias = todas.filter { it.propia && it.incluida }
        val paisajes = todas.filter { !it.propia }
        val base = when (modo(ctx)) {
            Modo.MIAS -> mias.ifEmpty { paisajes }
            Modo.PAISAJES -> paisajes
            Modo.MEZCLA -> paisajes + mias
        }
        // Las favoritas entran dos veces, así salen el doble.
        return (base + base.filter { it.favorita }).shuffled()
    }

    private fun ajustes(ctx: Context) = ctx.getSharedPreferences("fotos", Context.MODE_PRIVATE)
    fun fija(ctx: Context): String? = ajustes(ctx).getString("fija", null)
    fun ponerFija(ctx: Context, foto: Foto?) = ajustes(ctx).edit().putString("fija", foto?.archivo?.name).apply()
    fun modo(ctx: Context) = Modo.entries.firstOrNull { it.name == ajustes(ctx).getString("modo", null) } ?: Modo.MEZCLA
    fun ponerModo(ctx: Context, m: Modo) = ajustes(ctx).edit().putString("modo", m.name).apply()

    @Synchronized
    fun ponerIncluida(ctx: Context, foto: Foto, si: Boolean) {
        editarIndice(ctx) { o -> if (o.getString("archivo") == foto.archivo.name) o.put("incluida", si); o }
    }

    /** Cambia entre llenar la pantalla y verla completa, volviéndola a componer desde el original. */
    fun ponerLlenar(ctx: Context, foto: Foto, si: Boolean) {
        val bmp = BitmapFactory.decodeFile(original(foto).let { if (it.exists()) it else foto.archivo }.path) ?: return
        guardar(componer(bmp, si), foto.archivo)
        bmp.recycle()
        synchronized(this) {
            editarIndice(ctx) { o -> if (o.getString("archivo") == foto.archivo.name) o.put("llenar", si); o }
        }
    }

    // Junto a cada propia se guarda la foto girada y sin recortar, para poder cambiar cómo se ve.
    private fun original(foto: Foto) = File(foto.archivo.path.removeSuffix(".jpg") + ".orig.jpg")

    fun decodificar(foto: Foto): Bitmap? =
        BitmapFactory.decodeFile(foto.archivo.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })

    /** Baja algunas fotos nuevas y descarta las más viejas. Bloquea: llamar fuera del hilo principal. */
    fun refrescar(ctx: Context) {
        val conocidas = listar(ctx).map { it.archivo.name }.toSet() + leerVetadas(ctx)
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

    /** Una favorita nunca se borra al hacer espacio y sale el doble de veces en la rotación. */
    @Synchronized
    fun marcarFavorita(ctx: Context, foto: Foto, favorita: Boolean) {
        editarIndice(ctx) { o -> if (o.getString("archivo") == foto.archivo.name) o.put("favorita", favorita); o }
    }

    /** Saca la foto de la rotación para siempre: se borra y, si es de Commons, no se vuelve a bajar. */
    @Synchronized
    fun ocultar(ctx: Context, foto: Foto) {
        editarIndice(ctx) { o -> if (o.getString("archivo") == foto.archivo.name) null else o }
        foto.archivo.delete()
        original(foto).delete()
        if (fija(ctx) == foto.archivo.name) ponerFija(ctx, null)
        if (!foto.propia) vetadas(ctx).writeText(JSONArray(leerVetadas(ctx) + foto.archivo.name).toString())
    }

    /**
     * Guarda una foto subida desde el celular, ya girada y a 1920x1080, llenando la pantalla.
     * Si trae ubicación GPS se busca el lugar, y la fecha en que se tomó va como descripción.
     */
    fun importar(ctx: Context, origen: File): Boolean {
        val exif = try { ExifInterface(origen.path) } catch (e: Exception) { null }
        val giro = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origen.path, limites)
        if (limites.outWidth <= 0) return false
        // Medidas ya giradas: se baja de resolución mientras siga cubriendo 1920x1080.
        val (w, h) = if (giro == 90f || giro == 270f) limites.outHeight to limites.outWidth else limites.outWidth to limites.outHeight
        var muestra = 1
        while (w / (muestra * 2) >= ANCHO && h / (muestra * 2) >= ALTO) muestra *= 2
        var bmp = BitmapFactory.decodeFile(origen.path, BitmapFactory.Options().apply {
            inSampleSize = muestra
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return false
        if (giro != 0f) {
            val girada = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(giro) }, true)
            bmp.recycle()
            bmp = girada
        }

        // El original se guarda a lo justo para cubrir 1920x1080, así una vertical aún puede llenar la pantalla.
        val escala = minOf(1f, maxOf(ANCHO.toFloat() / bmp.width, ALTO.toFloat() / bmp.height))
        if (escala < 1f) {
            val chica = Bitmap.createScaledBitmap(bmp, (bmp.width * escala).toInt(), (bmp.height * escala).toInt(), true)
            bmp.recycle()
            bmp = chica
        }
        val nombre = "propia-${System.currentTimeMillis()}"
        guardar(bmp, File(dir(ctx), "$nombre.orig.jpg"))
        val archivo = "$nombre.jpg"
        guardar(componer(bmp, llenar = true), File(dir(ctx), archivo))
        bmp.recycle()

        val gps = FloatArray(2)
        @Suppress("DEPRECATION")
        val lugar = if (exif?.getLatLong(gps) == true) lugarDe(gps[0].toDouble(), gps[1].toDouble()) else ""
        // Algunos celulares quitan la ubicación al subir desde el navegador; queda en el log para saberlo.
        Log.i(TAG, "Subida: ${w}x$h, cámara=${exif?.getAttribute(ExifInterface.TAG_MAKE)}, " +
            "fecha=${exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)}, gps=${gps.toList()}, lugar=$lugar")
        val tomada = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")))
            } catch (e: Exception) { null }
        } ?: ""
        agregar(ctx, JSONObject().apply {
            put("archivo", archivo)
            put("lugar", lugar)
            put("descripcion", tomada)
            put("propia", true)
        })
        return true
    }

    private fun guardar(bmp: Bitmap, destino: File) {
        destino.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    // A 1920x1080: llenando (recorta lo que sobra) o completa sobre una copia suya desenfocada.
    private fun componer(bmp: Bitmap, llenar: Boolean): Bitmap {
        val lienzo = Bitmap.createBitmap(ANCHO, ALTO, Bitmap.Config.ARGB_8888)
        val c = Canvas(lienzo)
        val suave = Paint(Paint.FILTER_BITMAP_FLAG)
        if (llenar) {
            c.drawBitmap(bmp, null, cubrir(bmp, ANCHO, ALTO), suave)
        } else {
            val chica = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888)
            Canvas(chica).drawBitmap(bmp, null, cubrir(bmp, 48, 27), suave)
            c.drawBitmap(chica, null, Rect(0, 0, ANCHO, ALTO), suave)
            chica.recycle()
            c.drawColor(0x73000000)
            val escala = minOf(ANCHO.toFloat() / bmp.width, ALTO.toFloat() / bmp.height)
            val w = (bmp.width * escala).toInt()
            val h = (bmp.height * escala).toInt()
            c.drawBitmap(bmp, null, Rect((ANCHO - w) / 2, (ALTO - h) / 2, (ANCHO + w) / 2, (ALTO + h) / 2), suave)
        }
        return lienzo
    }

    // Destino para dibujar la foto entera escalada hasta cubrir w x h, centrada (lo que sobra queda fuera).
    private fun cubrir(bmp: Bitmap, w: Int, h: Int): Rect {
        val escala = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val sw = (bmp.width * escala).toInt()
        val sh = (bmp.height * escala).toInt()
        return Rect((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2)
    }

    private fun leerVetadas(ctx: Context): Set<String> {
        val f = vetadas(ctx)
        if (!f.exists()) return emptySet()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    private fun editarIndice(ctx: Context, cambio: (JSONObject) -> JSONObject?) {
        val f = indice(ctx)
        if (!f.exists()) return
        val arr = JSONArray(f.readText())
        val nuevo = JSONArray()
        for (i in 0 until arr.length()) cambio(arr.getJSONObject(i))?.let { nuevo.put(it) }
        f.writeText(nuevo.toString())
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
            o.optBoolean("propia") || NO_NATURALEZA.none { it in o.optString("descripcion").lowercase() }
        }
        val (favoritas, normales) = validas.partition { it.optBoolean("favorita") || it.optBoolean("propia") }
        val sobran = (validas.size - MAXIMO).coerceAtLeast(0).coerceAtMost(normales.size)
        val quedan = JSONArray()
        (descartadas + normales.take(sobran)).forEach { File(dir(ctx), it.getString("archivo")).delete() }
        (favoritas + normales.drop(sobran)).forEach { quedan.put(it) }
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
