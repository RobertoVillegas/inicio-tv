package dev.robertovillegas.inicio

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

data class App(val name: String, val image: Drawable?, val launch: Intent) {
    val packageName: String? get() = launch.component?.packageName
}

/** The dock's apps and their order, stored on the TV. */
object Dock {
    private const val KEY = "packages"

    /** Goes up whenever the dock changes, so the home screen knows to redraw it. */
    var version = 0
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, null)?.split(",")?.filter { it.isNotEmpty() } ?: Apps.DEFAULT_DOCK

    fun save(ctx: Context, packages: List<String>) {
        prefs(ctx).edit().putString(KEY, packages.joinToString(",")).apply()
        version++
    }

    fun contains(ctx: Context, pkg: String) = pkg in list(ctx)

    fun add(ctx: Context, pkg: String) {
        if (!contains(ctx, pkg)) save(ctx, list(ctx) + pkg)
    }

    fun remove(ctx: Context, pkg: String) = save(ctx, list(ctx) - pkg)
}

object Apps {
    val DEFAULT_DOCK = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.crunchyroll.crunchyroid",
        "com.apple.atve.androidtv.appletv",
        "com.plexapp.android",
        "com.app.future",
        "com.spotify.tv.android",
        "org.smarttube.stable",
    )

    private val HIDDEN = setOf(
        "dev.robertovillegas.inicio",
        "com.tcl.partnercustomizer",
        "com.tcl.guard",
        "com.tcl.suspension",
        "com.tcl.t_solo",
    )

    fun docked(ctx: Context): List<App> {
        val byPackage = all(ctx).associateBy { it.packageName }
        return Dock.list(ctx).mapNotNull { byPackage[it] }
    }

    fun all(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName !in HIDDEN }
            .map { ri ->
                val ai = ri.activityInfo
                App(
                    name = ri.loadLabel(pm).toString(),
                    image = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) ?: ri.loadIcon(pm),
                    launch = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(ComponentName(ai.packageName, ai.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    // TCL's TV app owns the HDMI inputs.
    fun inputs() = Intent().setComponent(ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settings() = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launch(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, "No se pudo abrir", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(ctx, "No se pudo abrir", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * An app tile in the Apple TV style: rounded corners and a hairline edge. Focus lifts it with a shadow;
 * pushing against an edge with the remote tilts it toward that side and back.
 */
class Tile(ctx: Context, val app: App, width: Int, height: Int) : FrameLayout(ctx) {
    private val frame = FrameLayout(ctx)
    private val label = TextView(ctx)
    private var held = false
    private var wiggle: ObjectAnimator? = null

    var onLongPress: (() -> Unit)? = null

    private companion object {
        // How much it grows on focus, matching the Apple TV app's small tiles.
        const val FOCUS_SCALE = 1.1f
    }

    init {
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        val radius = dp(12).toFloat()

        frame.apply {
            background = GradientDrawable().apply { cornerRadius = radius; setColor(0x33FFFFFF) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            outlineSpotShadowColor = Color.BLACK
            outlineAmbientShadowColor = Color.BLACK
            translationZ = dp(2).toFloat()
            cameraDistance = 8000 * resources.displayMetrics.density
        }
        val img = ImageView(ctx).apply {
            setImageDrawable(app.image)
            scaleType = if (app.image != null && app.image.intrinsicWidth > app.image.intrinsicHeight)
                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (scaleType == ImageView.ScaleType.FIT_CENTER) setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        frame.addView(img, LayoutParams(-1, -1))
        frame.addView(Edge(ctx, radius), LayoutParams(-1, -1))
        addView(frame, LayoutParams(dp(width), dp(height)))

        label.text = app.name
        label.setTextColor(Color.WHITE)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        label.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        label.setShadowLayer(dp(4).toFloat(), 0f, 0f, 0xCC000000.toInt())
        label.gravity = Gravity.CENTER
        label.maxLines = 1
        label.ellipsize = TextUtils.TruncateAt.END
        label.alpha = 0f
        addView(label, LayoutParams(dp(width), -2, Gravity.TOP).apply { topMargin = dp(height + 14) })

        setOnFocusChangeListener { _, focused -> if (focused) focus() else blur() }
        setOnClickListener { Apps.launch(ctx, app.launch) }
        setOnKeyListener { _, code, ev -> onKey(code, ev) }
    }

    private fun focus() {
        // Like the Apple TV app: grows fast with no bounce, and settles back slowly on blur.
        frame.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).translationZ(dp(18).toFloat())
            .rotationX(0f).rotationY(0f)
            .setInterpolator(AccelerateInterpolator()).setDuration(85).start()
        label.translationY = dp(-6).toFloat()
        label.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun blur() {
        frame.animate().scaleX(1f).scaleY(1f).translationZ(dp(2).toFloat()).rotationX(0f).rotationY(0f)
            .setInterpolator(DecelerateInterpolator()).setDuration(230).start()
        label.animate().alpha(0f).setDuration(120).start()
    }

    private fun onKey(code: Int, ev: KeyEvent): Boolean {
        val center = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (center && ev.action == KeyEvent.ACTION_DOWN) {
            if (ev.repeatCount == 0) {
                held = false
                // A press gives back half of the focus growth; release restores it.
                val pressed = 1f + (FOCUS_SCALE - 1f) / 2
                frame.animate().scaleX(pressed).scaleY(pressed).setInterpolator(DecelerateInterpolator())
                    .setDuration(80).start()
            } else if (ev.isLongPress && !held && onLongPress != null) {
                held = true
                frame.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setInterpolator(AccelerateInterpolator())
                    .setDuration(80).start()
                onLongPress?.invoke()
            }
            return true
        }
        if (center && ev.action == KeyEvent.ACTION_UP) {
            if (!held) {
                frame.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE).setInterpolator(AccelerateInterpolator())
                    .setDuration(80).start()
                performClick()
            }
            held = false
            return true
        }
        if (ev.action == KeyEvent.ACTION_DOWN) {
            val direction = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> FOCUS_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FOCUS_DOWN
                else -> return false
            }
            if (focusSearch(direction) == null) tilt(direction)
        }
        return false
    }

    /** Wiggles like iOS icons while it's being moved within the dock. */
    fun setWiggling(on: Boolean) {
        wiggle?.cancel()
        wiggle = null
        frame.rotation = 0f
        if (!on) return
        wiggle = ObjectAnimator.ofFloat(frame, View.ROTATION, -1.8f, 1.8f).apply {
            duration = 130
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun tilt(direction: Int) {
        val (rx, ry) = when (direction) {
            FOCUS_LEFT -> 0f to -10f
            FOCUS_RIGHT -> 0f to 10f
            FOCUS_UP -> 10f to 0f
            else -> -10f to 0f
        }
        frame.animate().rotationX(rx).rotationY(ry).setInterpolator(DecelerateInterpolator())
            .setDuration(110).withEndAction {
                frame.animate().rotationX(0f).rotationY(0f).setInterpolator(OvershootInterpolator(2.5f))
                    .setDuration(380).start()
            }.start()
    }
}

/** A tile's 1px edge, white at 8 %, unchanged by focus. */
private class Edge(ctx: Context, private val radius: Float) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x14FFFFFF
    }
    private val box = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = box.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)

    override fun onDraw(c: Canvas) = c.drawRoundRect(box, radius, radius, paint)
}
