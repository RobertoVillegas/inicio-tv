package dev.robertovillegas.inicio

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

data class App(val nombre: String, val imagen: Drawable?, val abrir: Intent)

object Apps {
    // El orden del dock. Las que no estén instaladas simplemente no aparecen.
    private val FAVORITAS = listOf(
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

    private val OCULTAS = setOf(
        "dev.robertovillegas.inicio",
        "com.tcl.partnercustomizer",
        "com.tcl.guard",
        "com.tcl.suspension",
        "com.tcl.t_solo",
    )

    fun favoritas(ctx: Context): List<App> {
        val porPaquete = todas(ctx).associateBy { it.abrir.component?.packageName }
        return FAVORITAS.mapNotNull { porPaquete[it] }
    }

    fun todas(ctx: Context): List<App> {
        val pm = ctx.packageManager
        val consulta = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(consulta, 0)
            .filter { it.activityInfo.packageName !in OCULTAS }
            .map { ri ->
                val ai = ri.activityInfo
                App(
                    nombre = ri.loadLabel(pm).toString(),
                    imagen = ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) ?: ri.loadIcon(pm),
                    abrir = Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(ComponentName(ai.packageName, ai.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
                )
            }
            .sortedBy { it.nombre.lowercase() }
    }

    // La app de TV de TCL es la que muestra las entradas HDMI.
    fun entradas() = Intent().setComponent(ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun ajustes() = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun lanzar(ctx: Context, intent: Intent) {
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
 * Una tarjeta de app al estilo Apple TV: al recibir el foco se eleva con un pequeño rebote,
 * proyecta sombra y un brillo cruza la superficie. Si el control empuja contra una orilla,
 * la tarjeta se inclina un poco hacia ese lado y regresa.
 */
class Tarjeta(ctx: Context, app: App, ancho: Int, alto: Int) : FrameLayout(ctx) {
    private val marco = FrameLayout(ctx)
    private val brillo = View(ctx)
    private val reflejo = View(ctx)
    private val nombre = TextView(ctx)
    private val anchoPx = dp(ancho).toFloat()

    init {
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        val radio = dp(12).toFloat()

        marco.apply {
            background = GradientDrawable().apply { cornerRadius = radio; setColor(0x33FFFFFF) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            outlineSpotShadowColor = Color.BLACK
            outlineAmbientShadowColor = Color.BLACK
            cameraDistance = 8000 * resources.displayMetrics.density
        }
        val img = ImageView(ctx).apply {
            setImageDrawable(app.imagen)
            scaleType = if (app.imagen != null && app.imagen.intrinsicWidth > app.imagen.intrinsicHeight)
                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (scaleType == ImageView.ScaleType.FIT_CENTER) setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        marco.addView(img, LayoutParams(-1, -1))

        // Luz suave desde arriba mientras la tarjeta tiene el foco.
        reflejo.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x30FFFFFF, 0x00FFFFFF, 0x00FFFFFF),
        )
        reflejo.alpha = 0f
        marco.addView(reflejo, LayoutParams(-1, -1))

        // Franja diagonal que cruza la tarjeta una vez al enfocarla.
        brillo.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(0x00FFFFFF, 0x50FFFFFF, 0x00FFFFFF),
        )
        brillo.rotation = 18f
        brillo.alpha = 0f
        marco.addView(brillo, LayoutParams(dp(ancho / 3), dp(alto * 2), Gravity.CENTER_VERTICAL))
        addView(marco, LayoutParams(dp(ancho), dp(alto)))

        nombre.text = app.nombre
        nombre.setTextColor(Color.WHITE)
        nombre.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nombre.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        nombre.setShadowLayer(dp(4).toFloat(), 0f, 0f, 0xCC000000.toInt())
        nombre.gravity = Gravity.CENTER
        nombre.maxLines = 1
        nombre.ellipsize = TextUtils.TruncateAt.END
        nombre.alpha = 0f
        addView(nombre, LayoutParams(dp(ancho), -2, Gravity.TOP).apply { topMargin = dp(alto + 14) })

        setOnFocusChangeListener { _, foco -> if (foco) enfocar() else desenfocar() }
        setOnClickListener { Apps.lanzar(ctx, app.abrir) }
        setOnKeyListener { _, code, ev -> tecla(code, ev) }
    }

    private fun enfocar() {
        marco.animate().scaleX(1.12f).scaleY(1.12f).translationZ(dp(18).toFloat())
            .rotationX(0f).rotationY(0f)
            .setInterpolator(OvershootInterpolator(1.6f)).setDuration(320).start()
        reflejo.animate().alpha(1f).setDuration(200).start()
        nombre.translationY = dp(-6).toFloat()
        nombre.animate().alpha(1f).translationY(0f).setDuration(220).start()
        brillo.translationX = -anchoPx / 2
        brillo.alpha = 1f
        brillo.animate().translationX(anchoPx * 1.2f).setInterpolator(DecelerateInterpolator())
            .setDuration(650).withEndAction { brillo.alpha = 0f }.start()
    }

    private fun desenfocar() {
        marco.animate().scaleX(1f).scaleY(1f).translationZ(0f).rotationX(0f).rotationY(0f)
            .setInterpolator(DecelerateInterpolator()).setDuration(200).start()
        reflejo.animate().alpha(0f).setDuration(150).start()
        nombre.animate().alpha(0f).setDuration(120).start()
        brillo.animate().cancel()
        brillo.alpha = 0f
    }

    private fun tecla(code: Int, ev: KeyEvent): Boolean {
        val centro = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (centro && ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
            marco.animate().scaleX(1.05f).scaleY(1.05f).setInterpolator(DecelerateInterpolator())
                .setDuration(90).start()
            return true
        }
        if (centro && ev.action == KeyEvent.ACTION_UP) {
            marco.animate().scaleX(1.12f).scaleY(1.12f).setInterpolator(OvershootInterpolator(2f))
                .setDuration(220).start()
            performClick()
            return true
        }
        if (ev.action == KeyEvent.ACTION_DOWN) {
            val direccion = when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT -> FOCUS_LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FOCUS_RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> FOCUS_UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FOCUS_DOWN
                else -> return false
            }
            if (focusSearch(direccion) == null) inclinar(direccion)
        }
        return false
    }

    private fun inclinar(direccion: Int) {
        val (rx, ry) = when (direccion) {
            FOCUS_LEFT -> 0f to -10f
            FOCUS_RIGHT -> 0f to 10f
            FOCUS_UP -> 10f to 0f
            else -> -10f to 0f
        }
        marco.animate().rotationX(rx).rotationY(ry).setInterpolator(DecelerateInterpolator())
            .setDuration(110).withEndAction {
                marco.animate().rotationX(0f).rotationY(0f).setInterpolator(OvershootInterpolator(2.5f))
                    .setDuration(380).start()
            }.start()
    }
}
