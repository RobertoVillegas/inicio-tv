package dev.robertovillegas.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import kotlin.random.Random

fun View.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/**
 * Slowly drifting landscapes (a Ken Burns effect) with the time, weather, what's playing and the photo's
 * credit on top. Shared by the home screen and the screensaver. The constant motion keeps the panel from
 * holding a still image.
 */
class Scene(ctx: Context) : FrameLayout(ctx) {
    private val layers = arrayOf(layer(), layer())
    private var front = 0
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var photos: List<Photo> = emptyList()
    private var next = 0
    private var interval = 180_000L
    private var running = false
    private var night = false

    var current: Photo? = null
        private set
    var onPhotoChange: ((Photo) -> Unit)? = null

    val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val credit = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }

    private val clock = TextClock(ctx).apply {
        format24Hour = "H:mm"
        format12Hour = "h:mm"
        style(this, 84, Typeface.create("sans-serif-light", Typeface.NORMAL))
        letterSpacing = -0.02f
    }
    private val date = TextClock(ctx).apply {
        format24Hour = "EEEE d 'de' MMMM"
        format12Hour = "EEEE d 'de' MMMM"
        style(this, 20, Typeface.create("sans-serif", Typeface.NORMAL))
        alpha = 0.9f
    }
    private val weather = TextView(ctx).apply {
        style(this, 18, Typeface.create("sans-serif", Typeface.NORMAL))
        alpha = 0.85f
        visibility = GONE
    }
    private val forecast = TextView(ctx).apply {
        style(this, 14, Typeface.create("sans-serif", Typeface.NORMAL))
        alpha = 0.7f
        visibility = GONE
    }

    private val nowPlaying = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = GONE
        alpha = 0f
    }
    private val cover = ImageView(ctx).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = GradientDrawable().apply { cornerRadius = dp(6).toFloat(); setColor(0x33FFFFFF) }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }
    private val songTitle = TextView(ctx).apply {
        style(this, 15, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(320)
    }
    private val songArtist = TextView(ctx).apply {
        style(this, 13, Typeface.create("sans-serif", Typeface.NORMAL))
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(320)
        alpha = 0.75f
    }

    private val place = TextView(ctx).apply {
        style(this, 16, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        gravity = Gravity.END
    }
    private val description = TextView(ctx).apply {
        style(this, 13, Typeface.create("sans-serif", Typeface.NORMAL))
        gravity = Gravity.END
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(380)
        alpha = 0.8f
    }
    private val author = TextView(ctx).apply {
        style(this, 11, Typeface.create("sans-serif", Typeface.NORMAL))
        gravity = Gravity.END
        maxLines = 1
        alpha = 0.6f
    }

    init {
        setBackgroundColor(Color.BLACK)
        layers.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        // Gradients so the text reads over any photo.
        addView(View(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xB3000000.toInt(), 0x40000000, 0x00000000),
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(340), Gravity.BOTTOM))
        addView(View(ctx).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x00000000),
            )
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(120), Gravity.TOP))

        val songText = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        songText.addView(songTitle)
        songText.addView(songArtist)
        nowPlaying.addView(cover, LinearLayout.LayoutParams(dp(44), dp(44)))
        nowPlaying.addView(songText, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12) })

        info.addView(nowPlaying, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(14) })
        info.addView(clock)
        info.addView(date)
        info.addView(weather, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        info.addView(forecast, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        // Each line gets its own width: inheriting the author's would hide them all when there's no author (own photos).
        credit.addView(place, LinearLayout.LayoutParams(-2, -2))
        credit.addView(description, LinearLayout.LayoutParams(-2, -2))
        credit.addView(author, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        addView(info, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        addView(credit, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
    }

    private fun layer() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun style(t: TextView, sp: Int, typeface: Typeface) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        t.typeface = typeface
        t.setTextColor(Color.WHITE)
        t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())
    }

    fun start(intervalMs: Long) {
        interval = intervalMs
        running = true
        worker.execute {
            photos = Photos.rotation(context)
            main.post { advance() }
            // With few photos stored, download more and add them to the rotation.
            if (Photos.list(context).size < 12) {
                Photos.refresh(context)
                photos = Photos.rotation(context)
                main.post { if (layers.all { it.drawable == null }) advance() }
            }
        }
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        layers.forEach { it.animate().cancel() }
    }

    fun release() {
        stop()
        worker.shutdownNow()
    }

    /** Drops both photos from memory. On the next start, the next photo fades in from black. */
    fun releasePhotos() {
        stop()
        layers.forEach { it.setImageDrawable(null); it.alpha = 0f }
        // The pixels live outside the Java heap and only go back to the system after a GC.
        Runtime.getRuntime().gc()
    }

    private val tick = Runnable { advance() }

    fun skip() = advance()

    /** Re-reads the photo list, for example after a favorite or a hidden photo. */
    fun reload(advanceNow: Boolean) {
        worker.execute {
            photos = Photos.rotation(context)
            next = 0
            if (advanceNow) main.post { advance() }
        }
    }

    /** Redraws the current photo from disk, for example after changing how it's shown. */
    fun redrawCurrent() {
        val name = current?.file?.name ?: return
        worker.execute {
            val photo = Photos.list(context).firstOrNull { it.file.name == name } ?: return@execute
            val bmp = Photos.decode(photo) ?: return@execute
            main.post { if (running) show(bmp, photo) }
        }
    }

    private fun advance() {
        if (!running) return
        main.removeCallbacks(tick)
        main.postDelayed(tick, interval)
        val list = photos
        if (list.isEmpty()) return
        var photo = list[next++ % list.size]
        // Favorites appear twice in the list; avoid showing the same one back to back.
        if (list.size > 1 && photo.file == current?.file) photo = list[next++ % list.size]
        worker.execute {
            val bmp = Photos.decode(photo) ?: return@execute
            main.post { if (running) show(bmp, photo) }
        }
    }

    private fun show(bmp: Bitmap, photo: Photo) {
        val leaving = layers[front]
        front = 1 - front
        val entering = layers[front]
        entering.animate().cancel()
        entering.setImageBitmap(bmp)
        entering.colorFilter = if (night) NIGHT_FILTER else null

        // Starts slightly zoomed in and drifts toward a random corner for its whole life.
        val drift = dp(24).toFloat()
        entering.scaleX = 1.06f; entering.scaleY = 1.06f
        entering.translationX = Random.nextFloat() * 2 * drift - drift
        entering.translationY = Random.nextFloat() * 2 * drift - drift
        entering.alpha = 0f
        entering.animate().alpha(1f).setDuration(2500).withEndAction {
            if (leaving.alpha == 0f) leaving.setImageDrawable(null)
        }.start()
        entering.animate()
            .scaleX(1.14f).scaleY(1.14f)
            .translationX(Random.nextFloat() * 2 * drift - drift)
            .translationY(Random.nextFloat() * 2 * drift - drift)
            .setInterpolator(LinearInterpolator())
            .setDuration(interval + 3000)
            .start()
        leaving.animate().alpha(0f).setDuration(2500).withEndAction { leaving.setImageDrawable(null) }.start()

        current = photo
        place.text = photo.place
        place.visibility = if (photo.place.isEmpty()) GONE else VISIBLE
        description.text = photo.description
        description.visibility = if (photo.description.isEmpty()) GONE else VISIBLE
        author.text = listOf(photo.author, photo.license).filter { it.isNotEmpty() }
            .joinToString(" · ").let { if (it.isEmpty()) "" else "Foto: $it · Wikimedia Commons" }
        author.visibility = if (author.text.isEmpty()) GONE else VISIBLE
        onPhotoChange?.invoke(photo)
    }

    fun setWeather(w: Weather?) {
        if (w == null) return
        weather.text = buildString {
            append("${w.temperature}°  ${w.condition}")
            append("   ↑${w.high}° ↓${w.low}°")
            if (w.city.isNotEmpty()) append("   ·   ${w.city}")
        }
        weather.visibility = VISIBLE
        val hhmm = DateTimeFormatter.ofPattern("H:mm")
        val sun = if (w.isNight()) {
            val sunrise = if (LocalDateTime.now().isBefore(w.sunrise)) w.sunrise else w.sunriseTomorrow
            "Amanece ${sunrise.format(hhmm)}"
        } else {
            "Atardece ${w.sunset.format(hhmm)}"
        }
        val hours = w.nextHours.joinToString("   ") { (h, t) -> "${h.hour} h  $t°" }
        forecast.text = if (hours.isEmpty()) sun else "$sun      $hours"
        forecast.visibility = VISIBLE
    }

    fun setSong(s: Song?) {
        if (s == null) {
            nowPlaying.animate().alpha(0f).setDuration(400).withEndAction { nowPlaying.visibility = GONE }.start()
            return
        }
        songTitle.text = s.title
        songArtist.text = listOf(s.artist, s.app).filter { it.isNotEmpty() }.joinToString("  ·  ")
        cover.setImageBitmap(s.cover)
        cover.visibility = if (s.cover == null) GONE else VISIBLE
        nowPlaying.visibility = VISIBLE
        nowPlaying.animate().alpha(1f).setDuration(400).start()
    }

    /** At night: darker, warmer photos and a dimmer clock. */
    fun setNight(on: Boolean) {
        if (on == night) return
        night = on
        layers.forEach { it.colorFilter = if (on) NIGHT_FILTER else null }
        info.animate().alpha(if (on) 0.7f else 1f).setDuration(1500).start()
        credit.animate().alpha(if (on) 0.6f else 1f).setDuration(1500).start()
    }

    private companion object {
        // 55 % brightness, with blue a little lower so the light feels warm.
        val NIGHT_FILTER = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            0.58f, 0f, 0f, 0f, 0f,
            0f, 0.53f, 0f, 0f, 0f,
            0f, 0f, 0.42f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
}
