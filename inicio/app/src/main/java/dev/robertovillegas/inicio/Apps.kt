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
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
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

/** Una tarjeta de app que crece y se marca con borde blanco al recibir el foco del control. */
class Tarjeta(ctx: Context, app: App, ancho: Int, alto: Int) : FrameLayout(ctx) {
    private val nombre = TextView(ctx)

    init {
        isFocusable = true
        clipChildren = false
        val radio = dp(10).toFloat()
        val marco = FrameLayout(ctx).apply {
            background = GradientDrawable().apply { cornerRadius = radio; setColor(0x33FFFFFF) }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }
        val img = ImageView(ctx).apply {
            setImageDrawable(app.imagen)
            scaleType = if (app.imagen != null && app.imagen.intrinsicWidth > app.imagen.intrinsicHeight)
                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (scaleType == ImageView.ScaleType.FIT_CENTER) setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        marco.addView(img, LayoutParams(-1, -1))
        val borde = View(ctx).apply {
            background = GradientDrawable().apply {
                cornerRadius = radio
                setStroke(dp(3), Color.WHITE)
            }
            visibility = INVISIBLE
        }
        marco.addView(borde, LayoutParams(-1, -1))
        addView(marco, LayoutParams(dp(ancho), dp(alto)))

        nombre.text = app.nombre
        nombre.setTextColor(Color.WHITE)
        nombre.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nombre.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        nombre.setShadowLayer(dp(4).toFloat(), 0f, 0f, 0xCC000000.toInt())
        nombre.gravity = Gravity.CENTER
        nombre.maxLines = 1
        nombre.alpha = 0f
        addView(nombre, LayoutParams(dp(ancho), -2, Gravity.TOP).apply { topMargin = dp(alto + 10) })

        setOnFocusChangeListener { _, foco ->
            val escala = if (foco) 1.1f else 1f
            animate().scaleX(escala).scaleY(escala).setDuration(150).start()
            nombre.animate().alpha(if (foco) 1f else 0f).setDuration(150).start()
            borde.visibility = if (foco) VISIBLE else INVISIBLE
            elevation = if (foco) dp(8).toFloat() else 0f
        }
        setOnClickListener { Apps.lanzar(ctx, app.abrir) }
        setOnKeyListener { _, code, ev ->
            if (ev.action == KeyEvent.ACTION_UP &&
                (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
            ) { performClick(); true } else false
        }
    }
}
