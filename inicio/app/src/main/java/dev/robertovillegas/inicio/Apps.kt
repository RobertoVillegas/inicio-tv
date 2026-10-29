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

data class App(val nombre: String, val imagen: Drawable?, val abrir: Intent) {
    val paquete: String? get() = abrir.component?.packageName
}

/** Las apps del dock y su orden, guardados en la TV. */
object Dock {
    private const val CLAVE = "paquetes"

    /** Sube cada vez que cambia el dock, para que el inicio sepa que debe redibujarlo. */
    var version = 0
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dock", Context.MODE_PRIVATE)

    fun lista(ctx: Context): List<String> =
        prefs(ctx).getString(CLAVE, null)?.split(",")?.filter { it.isNotEmpty() } ?: Apps.FAVORITAS

    fun guardar(ctx: Context, paquetes: List<String>) {
        prefs(ctx).edit().putString(CLAVE, paquetes.joinToString(",")).apply()
        version++
    }

    fun contiene(ctx: Context, paquete: String) = paquete in lista(ctx)

    fun agregar(ctx: Context, paquete: String) {
        if (!contiene(ctx, paquete)) guardar(ctx, lista(ctx) + paquete)
    }

    fun quitar(ctx: Context, paquete: String) = guardar(ctx, lista(ctx) - paquete)
}

object Apps {
    // El dock de fábrica. Se edita desde la TV (mantener OK sobre una app) y queda en Dock.
    val FAVORITAS = listOf(
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
        val porPaquete = todas(ctx).associateBy { it.paquete }
        return Dock.lista(ctx).mapNotNull { porPaquete[it] }
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
 * Una tarjeta de app al estilo Apple TV: esquinas redondas con un filo de luz arriba que le da
 * relieve, y al recibir el foco se eleva con un pequeño rebote y proyecta sombra. Si el control empuja contra una orilla,
 * la tarjeta se inclina un poco hacia ese lado y regresa.
 */
class Tarjeta(ctx: Context, val app: App, ancho: Int, alto: Int) : FrameLayout(ctx) {
    private val marco = FrameLayout(ctx)
    private val filo = Filo(ctx, dp(12).toFloat())
    private val nombre = TextView(ctx)
    private var mantenida = false
    private var temblor: ObjectAnimator? = null

    /** Qué hacer al dejar presionado OK sobre la tarjeta. */
    var alMantener: (() -> Unit)? = null

    private companion object {
        // Cuánto crece al enfocarla: lo de la app de Apple TV con sus tarjetas chicas.
        const val ESCALA = 1.1f
    }

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
            translationZ = dp(2).toFloat()
            cameraDistance = 8000 * resources.displayMetrics.density
        }
        val img = ImageView(ctx).apply {
            setImageDrawable(app.imagen)
            scaleType = if (app.imagen != null && app.imagen.intrinsicWidth > app.imagen.intrinsicHeight)
                ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            if (scaleType == ImageView.ScaleType.FIT_CENTER) setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        marco.addView(img, LayoutParams(-1, -1))

        marco.addView(filo, LayoutParams(-1, -1))
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
        // Como en la app de Apple TV: crece rápido y sin rebote, y al soltar el foco regresa despacio.
        marco.animate().scaleX(ESCALA).scaleY(ESCALA).translationZ(dp(18).toFloat())
            .rotationX(0f).rotationY(0f)
            .setInterpolator(AccelerateInterpolator()).setDuration(85).start()
        nombre.translationY = dp(-6).toFloat()
        nombre.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun desenfocar() {
        marco.animate().scaleX(1f).scaleY(1f).translationZ(dp(2).toFloat()).rotationX(0f).rotationY(0f)
            .setInterpolator(DecelerateInterpolator()).setDuration(230).start()
        nombre.animate().alpha(0f).setDuration(120).start()
    }

    private fun tecla(code: Int, ev: KeyEvent): Boolean {
        val centro = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        if (centro && ev.action == KeyEvent.ACTION_DOWN) {
            if (ev.repeatCount == 0) {
                mantenida = false
                // Al presionar pierde la mitad de lo que creció, y al soltar lo recupera.
                val medio = 1f + (ESCALA - 1f) / 2
                marco.animate().scaleX(medio).scaleY(medio).setInterpolator(DecelerateInterpolator())
                    .setDuration(80).start()
            } else if (ev.isLongPress && !mantenida && alMantener != null) {
                mantenida = true
                marco.animate().scaleX(ESCALA).scaleY(ESCALA).setInterpolator(AccelerateInterpolator())
                    .setDuration(80).start()
                alMantener?.invoke()
            }
            return true
        }
        if (centro && ev.action == KeyEvent.ACTION_UP) {
            if (!mantenida) {
                marco.animate().scaleX(ESCALA).scaleY(ESCALA).setInterpolator(AccelerateInterpolator())
                    .setDuration(80).start()
                performClick()
            }
            mantenida = false
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

    /** Tiembla como los iconos de iOS mientras se está moviendo dentro del dock. */
    fun temblar(si: Boolean) {
        temblor?.cancel()
        temblor = null
        marco.rotation = 0f
        if (!si) return
        temblor = ObjectAnimator.ofFloat(marco, View.ROTATION, -1.8f, 1.8f).apply {
            duration = 130
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun inclinar(direccion: Int) {
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

/** El borde de una tarjeta: una línea de un pixel, blanca al 8 %, que no cambia con el foco. */
private class Filo(ctx: Context, private val radio: Float) : View(ctx) {
    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x14FFFFFF
    }
    private val caja = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = caja.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)

    override fun onDraw(c: Canvas) = c.drawRoundRect(caja, radio, radio, pincel)
}
