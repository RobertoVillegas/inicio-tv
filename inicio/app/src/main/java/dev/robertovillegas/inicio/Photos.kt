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

data class Photo(
    val file: File,
    val place: String,
    val description: String,
    val author: String,
    val license: String,
    val favorite: Boolean,
    /** Uploaded from a phone; never deleted to make room. */
    val own: Boolean = false,
    /** Whether it's in the rotation (only own photos can be taken out). */
    val included: Boolean = true,
    /** An own photo cropped to fill the screen; otherwise shown whole over a blurred copy of itself. */
    val fill: Boolean = true,
)

enum class Mode { MIX, MINE, LANDSCAPES }

/**
 * Featured nature photos from Wikimedia Commons, kept on the TV so the home screen and the screensaver
 * work offline, plus the photos uploaded from a phone.
 */
object Photos {
    private const val TAG = "Photos"
    private const val API = "https://commons.wikimedia.org/w/api.php"
    private val CATEGORIES = listOf(
        "Featured_pictures_of_mountains",
        "Featured_pictures_of_lakes",
        "Featured_pictures_of_forests",
        "Featured_pictures_of_beaches",
        "Featured_pictures_of_waterfalls",
        "Featured_pictures_of_coasts",
    )

    // Those categories also hold trains, towns and monuments; they're filtered out by text.
    private val NOT_NATURE = listOf(
        "train", "railway", "station", "emu", "locomotive", "car ", "bus ", "airport",
        "church", "chapel", "cathedral", "castle", "temple", "monument", "museum",
        "village", "town", "city", "street", "building", "house", "bridge", "harbour", "port of",
        "neighbourhood", "neighborhood", "commune", "district", "skyline", "wind farm", "windmill",
        "monumento", "iglesia", "castillo", "pueblo", "ciudad",
    )

    // The TV's UI renders at 1920x1080; anything bigger only costs memory.
    private const val WIDTH = 1920
    private const val HEIGHT = 1080
    private const val MAX_KEPT = 40
    private const val NEW_PER_ROUND = 6
    private const val ONE_WEEK = 7L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "photos").apply { mkdirs() }
    private fun index(ctx: Context) = File(dir(ctx), "index.json")
    private fun titlesCache(ctx: Context) = File(dir(ctx), "titles.json")
    private fun banned(ctx: Context) = File(dir(ctx), "banned.json")

    @Synchronized
    fun list(ctx: Context): List<Photo> {
        val f = index(ctx)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
            val file = File(dir(ctx), o.getString("file"))
            if (!file.exists()) null else Photo(
                file,
                o.optString("place"),
                o.optString("description"),
                o.optString("author"),
                o.optString("license"),
                o.optBoolean("favorite"),
                o.optBoolean("own"),
                o.optBoolean("included", true),
                o.optBoolean("fill", true),
            )
        }
    }

    /** The photos to show: the pinned one if any, otherwise by mode (minus own photos taken out). */
    fun rotation(ctx: Context): List<Photo> {
        val all = list(ctx)
        pinned(ctx)?.let { name -> all.firstOrNull { it.file.name == name }?.let { return listOf(it) } }
        val mine = all.filter { it.own && it.included }
        val landscapes = all.filter { !it.own }
        val base = when (mode(ctx)) {
            Mode.MINE -> mine.ifEmpty { landscapes }
            Mode.LANDSCAPES -> landscapes
            Mode.MIX -> landscapes + mine
        }
        // Favorites go in twice, so they show twice as often.
        return (base + base.filter { it.favorite }).shuffled()
    }

    private fun settings(ctx: Context) = ctx.getSharedPreferences("photos", Context.MODE_PRIVATE)
    fun pinned(ctx: Context): String? = settings(ctx).getString("pinned", null)
    fun setPinned(ctx: Context, photo: Photo?) = settings(ctx).edit().putString("pinned", photo?.file?.name).apply()
    fun mode(ctx: Context) = Mode.entries.firstOrNull { it.name == settings(ctx).getString("mode", null) } ?: Mode.MIX
    fun setMode(ctx: Context, m: Mode) = settings(ctx).edit().putString("mode", m.name).apply()

    @Synchronized
    fun setIncluded(ctx: Context, photo: Photo, included: Boolean) {
        editIndex(ctx) { o -> if (o.getString("file") == photo.file.name) o.put("included", included); o }
    }

    /** Switches between filling the screen and showing it whole, recomposing from the original. */
    fun setFill(ctx: Context, photo: Photo, fill: Boolean) {
        val bmp = BitmapFactory.decodeFile(original(photo).let { if (it.exists()) it else photo.file }.path) ?: return
        save(compose(bmp, fill), photo.file)
        bmp.recycle()
        synchronized(this) {
            editIndex(ctx) { o -> if (o.getString("file") == photo.file.name) o.put("fill", fill); o }
        }
    }

    // Next to each own photo sits the rotated, uncropped original, so how it shows can change later.
    private fun original(photo: Photo) = File(photo.file.path.removeSuffix(".jpg") + ".orig.jpg")

    fun decode(photo: Photo): Bitmap? =
        BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })

    /** Downloads a few new photos and drops the oldest. Blocking: call off the main thread. */
    fun refresh(ctx: Context) {
        val known = list(ctx).map { it.file.name }.toSet() + readBanned(ctx)
        var added = 0
        for (title in availableTitles(ctx).shuffled()) {
            if (added >= NEW_PER_ROUND) break
            if (fileName(title) in known) continue
            try {
                if (download(ctx, title)) added++
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't download $title", e)
            }
        }
        trim(ctx)
    }

    /** A favorite is never deleted to make room and shows twice as often. */
    @Synchronized
    fun setFavorite(ctx: Context, photo: Photo, favorite: Boolean) {
        editIndex(ctx) { o -> if (o.getString("file") == photo.file.name) o.put("favorite", favorite); o }
    }

    /** Takes the photo out for good: deletes it and, for a Commons photo, never downloads it again. */
    @Synchronized
    fun hide(ctx: Context, photo: Photo) {
        editIndex(ctx) { o -> if (o.getString("file") == photo.file.name) null else o }
        photo.file.delete()
        original(photo).delete()
        if (pinned(ctx) == photo.file.name) setPinned(ctx, null)
        if (!photo.own) banned(ctx).writeText(JSONArray(readBanned(ctx) + photo.file.name).toString())
    }

    /**
     * Saves a photo uploaded from a phone, rotated upright and filling 1920x1080. A GPS position becomes the
     * place name, and the date it was taken becomes the description.
     */
    fun import(ctx: Context, source: File): Boolean {
        val exif = try { ExifInterface(source.path) } catch (e: Exception) { null }
        val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0) return false
        // Upright dimensions: subsample while the result still covers 1920x1080.
        val (w, h) = if (rotation == 90f || rotation == 270f) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
        var sample = 1
        while (w / (sample * 2) >= WIDTH && h / (sample * 2) >= HEIGHT) sample *= 2
        var bmp = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }) ?: return false
        if (rotation != 0f) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            bmp.recycle()
            bmp = rotated
        }

        // The original is kept just big enough to cover 1920x1080, so a portrait photo can still fill the screen.
        val scale = minOf(1f, maxOf(WIDTH.toFloat() / bmp.width, HEIGHT.toFloat() / bmp.height))
        if (scale < 1f) {
            val smaller = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
            bmp.recycle()
            bmp = smaller
        }
        // The gallery sorts own photos by this name.
        val base = "own-${System.currentTimeMillis()}"
        save(bmp, File(dir(ctx), "$base.orig.jpg"))
        val name = "$base.jpg"
        save(compose(bmp, fill = true), File(dir(ctx), name))
        bmp.recycle()

        val gps = FloatArray(2)
        @Suppress("DEPRECATION")
        val place = if (exif?.getLatLong(gps) == true) placeName(gps[0].toDouble(), gps[1].toDouble()) else ""
        // Some phones strip the location when uploading from the browser; the log tells which.
        Log.i(TAG, "Upload: ${w}x$h, make=${exif?.getAttribute(ExifInterface.TAG_MAKE)}, " +
            "date=${exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)}, gps=${gps.toList()}, place=$place")
        val taken = exif?.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let {
            try {
                LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                    .format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")))
            } catch (e: Exception) { null }
        } ?: ""
        append(ctx, JSONObject().apply {
            put("file", name)
            put("place", place)
            put("description", taken)
            put("own", true)
        })
        return true
    }

    private fun save(bmp: Bitmap, target: File) {
        target.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    // At 1920x1080: filled (cropping the excess) or whole over a blurred copy of itself.
    private fun compose(bmp: Bitmap, fill: Boolean): Bitmap {
        val canvasBmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val c = Canvas(canvasBmp)
        val smooth = Paint(Paint.FILTER_BITMAP_FLAG)
        if (fill) {
            c.drawBitmap(bmp, null, cover(bmp, WIDTH, HEIGHT), smooth)
        } else {
            val tiny = Bitmap.createBitmap(48, 27, Bitmap.Config.ARGB_8888)
            Canvas(tiny).drawBitmap(bmp, null, cover(bmp, 48, 27), smooth)
            c.drawBitmap(tiny, null, Rect(0, 0, WIDTH, HEIGHT), smooth)
            tiny.recycle()
            c.drawColor(0x73000000)
            val scale = minOf(WIDTH.toFloat() / bmp.width, HEIGHT.toFloat() / bmp.height)
            val w = (bmp.width * scale).toInt()
            val h = (bmp.height * scale).toInt()
            c.drawBitmap(bmp, null, Rect((WIDTH - w) / 2, (HEIGHT - h) / 2, (WIDTH + w) / 2, (HEIGHT + h) / 2), smooth)
        }
        return canvasBmp
    }

    // Where to draw the whole bitmap so it covers w x h, centered.
    private fun cover(bmp: Bitmap, w: Int, h: Int): Rect {
        val scale = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
        val sw = (bmp.width * scale).toInt()
        val sh = (bmp.height * scale).toInt()
        return Rect((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2)
    }

    private fun readBanned(ctx: Context): Set<String> {
        val f = banned(ctx)
        if (!f.exists()) return emptySet()
        val arr = JSONArray(f.readText())
        return (0 until arr.length()).map { arr.getString(it) }.toSet()
    }

    private fun editIndex(ctx: Context, change: (JSONObject) -> JSONObject?) {
        val f = index(ctx)
        if (!f.exists()) return
        val arr = JSONArray(f.readText())
        val updated = JSONArray()
        for (i in 0 until arr.length()) change(arr.getJSONObject(i))?.let { updated.put(it) }
        f.writeText(updated.toString())
    }

    private fun availableTitles(ctx: Context): List<String> {
        val f = titlesCache(ctx)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < ONE_WEEK) {
            val arr = JSONArray(f.readText())
            return (0 until arr.length()).map { arr.getString(it) }
        }
        val all = mutableListOf<String>()
        for (cat in CATEGORIES) {
            var cont: String? = null
            do {
                val url = "$API?action=query&list=categorymembers&cmtitle=Category:$cat" +
                    "&cmtype=file&cmlimit=500&format=json" +
                    (cont?.let { "&cmcontinue=" + enc(it) } ?: "")
                val r = Net.json(url)
                val members = r.getJSONObject("query").getJSONArray("categorymembers")
                for (i in 0 until members.length()) all += members.getJSONObject(i).getString("title")
                cont = r.optJSONObject("continue")?.optString("cmcontinue")
            } while (!cont.isNullOrEmpty())
        }
        f.writeText(JSONArray(all).toString())
        return all
    }

    private fun download(ctx: Context, title: String): Boolean {
        val url = "$API?action=query&titles=${enc(title)}&prop=imageinfo|coordinates" +
            "&iiprop=url|size|extmetadata&iiurlwidth=$WIDTH" +
            "&iiextmetadatafilter=ObjectName|ImageDescription|Artist|LicenseShortName" +
            "&format=json"
        val page = Net.json(url).getJSONObject("query").getJSONObject("pages")
            .let { it.getJSONObject(it.keys().next()) }
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return false
        val width = info.optInt("width")
        val height = info.optInt("height")
        // Only landscape photos that fill the screen without extreme cropping.
        val ratio = width.toFloat() / height
        if (width < WIDTH || ratio < 1.4f || ratio > 2.1f) return false
        val thumb = info.optString("thumburl").ifEmpty { return false }

        val meta = info.optJSONObject("extmetadata") ?: JSONObject()
        val description = text(meta, "ImageDescription").ifEmpty { text(meta, "ObjectName") }
        val haystack = "$title $description".lowercase()
        if (NOT_NATURE.any { it in haystack }) return false
        val place = page.optJSONArray("coordinates")?.optJSONObject(0)?.let {
            placeName(it.getDouble("lat"), it.getDouble("lon"))
        } ?: ""

        val name = fileName(title)
        Net.download(thumb, File(dir(ctx), name))
        append(ctx, JSONObject().apply {
            put("file", name)
            put("place", place)
            put("description", description.take(140))
            put("author", text(meta, "Artist").take(60))
            put("license", text(meta, "LicenseShortName"))
        })
        return true
    }

    private fun placeName(lat: Double, lon: Double): String = try {
        val r = Net.json(
            "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon" +
                "&format=json&zoom=10&accept-language=es"
        )
        val a = r.optJSONObject("address") ?: JSONObject()
        val locality = listOf("village", "town", "city", "municipality", "county")
            .map { a.optString(it) }.firstOrNull { it.isNotEmpty() }
        listOfNotNull(locality, a.optString("state").ifEmpty { null }, a.optString("country").ifEmpty { null })
            .distinct().joinToString(", ")
    } catch (e: Exception) {
        Log.w(TAG, "No reverse geocoding for $lat,$lon", e)
        ""
    }

    @Synchronized
    private fun append(ctx: Context, o: JSONObject) {
        val f = index(ctx)
        val arr = if (f.exists()) JSONArray(f.readText()) else JSONArray()
        arr.put(o)
        f.writeText(arr.toString())
    }

    @Synchronized
    private fun trim(ctx: Context) {
        val f = index(ctx)
        if (!f.exists()) return
        val arr = JSONArray(f.readText())
        val all = (0 until arr.length()).map { arr.getJSONObject(it) }
        val (valid, rejected) = all.partition { o ->
            o.optBoolean("own") || NOT_NATURE.none { it in o.optString("description").lowercase() }
        }
        val pinned = pinned(ctx)
        val (kept, regular) = valid.partition {
            it.optBoolean("favorite") || it.optBoolean("own") || it.getString("file") == pinned
        }
        val excess = (valid.size - MAX_KEPT).coerceAtLeast(0).coerceAtMost(regular.size)
        val remaining = JSONArray()
        (rejected + regular.take(excess)).forEach { File(dir(ctx), it.getString("file")).delete() }
        (kept + regular.drop(excess)).forEach { remaining.put(it) }
        f.writeText(remaining.toString())
    }

    private fun text(meta: JSONObject, field: String): String {
        val html = meta.optJSONObject(field)?.optString("value") ?: return ""
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("\\s+"), " ").trim()
    }

    private fun fileName(title: String) = Integer.toHexString(title.hashCode()) + ".jpg"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
