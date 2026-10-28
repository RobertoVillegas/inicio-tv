package dev.robertovillegas.inicio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import java.util.concurrent.Executors
import kotlin.random.Random

fun View.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/**
 * Fondo de paisajes que se mueve despacio (efecto Ken Burns) y cambia de foto cada cierto
 * tiempo, con la hora, el clima y los datos de la foto encima. La comparten el inicio y el
 * protector de pantalla. El movimiento constante evita dejar una imagen fija en el panel.
 */
class Escena(ctx: Context) : FrameLayout(ctx) {
    private val capas = arrayOf(capa(), capa())
    private var visible = 0
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var fotos: List<Foto> = emptyList()
    private var siguiente = 0
    private var intervalo = 180_000L
    private var activa = false

    val info = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    val credito = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }

    private val hora = TextClock(ctx).apply {
        format24Hour = "H:mm"
        format12Hour = "h:mm"
        estilo(this, 84, Typeface.create("sans-serif-light", Typeface.NORMAL))
        letterSpacing = -0.02f
    }
    private val fecha = TextClock(ctx).apply {
        format24Hour = "EEEE d 'de' MMMM"
        format12Hour = "EEEE d 'de' MMMM"
        estilo(this, 20, Typeface.create("sans-serif", Typeface.NORMAL))
        alpha = 0.9f
    }
    private val clima = TextView(ctx).apply {
        estilo(this, 18, Typeface.create("sans-serif", Typeface.NORMAL))
        alpha = 0.85f
        visibility = GONE
    }
    private val lugar = TextView(ctx).apply {
        estilo(this, 16, Typeface.create("sans-serif-medium", Typeface.NORMAL))
        gravity = Gravity.END
    }
    private val descripcion = TextView(ctx).apply {
        estilo(this, 13, Typeface.create("sans-serif", Typeface.NORMAL))
        gravity = Gravity.END
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        maxWidth = dp(380)
        alpha = 0.8f
    }
    private val autor = TextView(ctx).apply {
        estilo(this, 11, Typeface.create("sans-serif", Typeface.NORMAL))
        gravity = Gravity.END
        maxLines = 1
        alpha = 0.6f
    }

    init {
        setBackgroundColor(Color.BLACK)
        capas.forEach { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }
        // Degradados para que el texto se lea sobre cualquier foto.
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

        info.addView(hora)
        info.addView(fecha)
        info.addView(clima, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6) })
        credito.addView(lugar)
        credito.addView(descripcion)
        credito.addView(autor, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
        addView(info, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START))
        addView(credito, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END))
    }

    private fun capa() = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        alpha = 0f
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun estilo(t: TextView, sp: Int, tipo: Typeface) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        t.typeface = tipo
        t.setTextColor(Color.WHITE)
        t.setShadowLayer(dp(6).toFloat(), 0f, dp(1).toFloat(), 0x80000000.toInt())
    }

    fun iniciar(intervaloMs: Long) {
        intervalo = intervaloMs
        activa = true
        trabajo.execute {
            fotos = Fondos.listar(context).shuffled()
            principal.post { avanzar() }
            // Con pocas fotos guardadas, baja más y las suma a la rotación.
            if (fotos.size < 12) {
                Fondos.refrescar(context)
                fotos = Fondos.listar(context).shuffled()
                principal.post { if (capas.all { it.drawable == null }) avanzar() }
            }
        }
    }

    fun detener() {
        activa = false
        principal.removeCallbacksAndMessages(null)
        capas.forEach { it.animate().cancel() }
    }

    fun liberar() {
        detener()
        trabajo.shutdownNow()
    }

    private val tic = Runnable { avanzar() }

    private fun avanzar() {
        if (!activa) return
        principal.removeCallbacks(tic)
        principal.postDelayed(tic, intervalo)
        val lista = fotos
        if (lista.isEmpty()) return
        val foto = lista[siguiente++ % lista.size]
        trabajo.execute {
            val bmp = Fondos.decodificar(foto) ?: return@execute
            principal.post { if (activa) mostrar(bmp, foto) }
        }
    }

    private fun mostrar(bmp: Bitmap, foto: Foto) {
        val sale = capas[visible]
        visible = 1 - visible
        val entra = capas[visible]
        entra.animate().cancel()
        entra.setImageBitmap(bmp)

        // Arranca un poco ampliada y se desplaza hacia una esquina al azar durante toda su vida.
        val desplazamiento = dp(24).toFloat()
        entra.scaleX = 1.06f; entra.scaleY = 1.06f
        entra.translationX = Random.nextFloat() * 2 * desplazamiento - desplazamiento
        entra.translationY = Random.nextFloat() * 2 * desplazamiento - desplazamiento
        entra.alpha = 0f
        entra.animate().alpha(1f).setDuration(2500).withEndAction {
            if (sale.alpha == 0f) sale.setImageDrawable(null)
        }.start()
        entra.animate()
            .scaleX(1.14f).scaleY(1.14f)
            .translationX(Random.nextFloat() * 2 * desplazamiento - desplazamiento)
            .translationY(Random.nextFloat() * 2 * desplazamiento - desplazamiento)
            .setInterpolator(LinearInterpolator())
            .setDuration(intervalo + 3000)
            .start()
        sale.animate().alpha(0f).setDuration(2500).withEndAction { sale.setImageDrawable(null) }.start()

        lugar.text = foto.lugar
        lugar.visibility = if (foto.lugar.isEmpty()) GONE else VISIBLE
        descripcion.text = foto.descripcion
        descripcion.visibility = if (foto.descripcion.isEmpty()) GONE else VISIBLE
        autor.text = listOf(foto.autor, foto.licencia).filter { it.isNotEmpty() }
            .joinToString(" · ").let { if (it.isEmpty()) "" else "Foto: $it · Wikimedia Commons" }
    }

    fun ponerClima(c: Clima?) {
        if (c == null) return
        clima.text = buildString {
            append("${c.temperatura}°  ${c.estado}")
            append("   ↑${c.maxima}° ↓${c.minima}°")
            if (c.ciudad.isNotEmpty()) append("   ·   ${c.ciudad}")
        }
        clima.visibility = VISIBLE
    }
}
