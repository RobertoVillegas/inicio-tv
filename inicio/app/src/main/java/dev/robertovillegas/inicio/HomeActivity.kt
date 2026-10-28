package dev.robertovillegas.inicio

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private lateinit var escena: Escena
    private lateinit var dock: HorizontalScrollView
    private lateinit var fila: LinearLayout
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var modoCuadro = false

    // Sin tocar el control, el dock se esconde y queda solo el paisaje.
    private val ocultarDock = Runnable { entrarModoCuadro() }
    private val actualizarClima = object : Runnable {
        override fun run() {
            trabajo.execute {
                val c = ClimaLocal.obtener(this@HomeActivity)
                principal.post { escena.ponerClima(c) }
            }
            principal.postDelayed(this, 30 * 60_000L)
        }
    }
    private val refrescarFondos = object : Runnable {
        override fun run() {
            trabajo.execute { Fondos.refrescar(this@HomeActivity) }
            principal.postDelayed(this, 6 * 3600_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        escena = Escena(this)
        val raiz = FrameLayout(this)
        raiz.addView(escena, FrameLayout.LayoutParams(-1, -1))

        fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(escena.dp(48), escena.dp(14), escena.dp(48), escena.dp(44))
        }
        dock = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            descendantFocusability = FrameLayout.FOCUS_AFTER_DESCENDANTS
            addView(fila)
        }
        raiz.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        raiz.clipChildren = false
        setContentView(raiz)
        acomodar()
        llenarDock()
        principal.post(refrescarFondos)
    }

    private fun acomodar() {
        val m = escena.dp(48)
        val sobreDock = if (modoCuadro) m else escena.dp(170)
        (escena.info.layoutParams as FrameLayout.LayoutParams).setMargins(m, 0, 0, sobreDock)
        (escena.credito.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, m, sobreDock)
        escena.info.requestLayout()
        escena.credito.requestLayout()
    }

    private fun llenarDock() {
        fila.removeAllViews()
        val extras = listOf(
            App("Todas las apps", icono(android.R.drawable.ic_dialog_dialer),
                Intent(this, AppsActivity::class.java)),
            App("Entradas", icono(android.R.drawable.ic_menu_slideshow), Apps.entradas()),
            App("Ajustes", icono(android.R.drawable.ic_menu_preferences), Apps.ajustes()),
        )
        (Apps.favoritas(this) + extras).forEach { app ->
            fila.addView(
                Tarjeta(this, app, 128, 72),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = escena.dp(14) },
            )
        }
        fila.getChildAt(0)?.requestFocus()
    }

    private fun icono(res: Int) = LayerDrawable(arrayOf(
        GradientDrawable().apply { setColor(0x40FFFFFF) },
        getDrawable(res)!!.mutate().apply { setTint(Color.WHITE) },
    )).apply { setLayerInset(1, escena.dp(52), escena.dp(22), escena.dp(52), escena.dp(22)) }

    override fun onResume() {
        super.onResume()
        escena.iniciar(180_000L)
        principal.post(actualizarClima)
        salirModoCuadro()
    }

    override fun onPause() {
        super.onPause()
        escena.detener()
        principal.removeCallbacks(actualizarClima)
        principal.removeCallbacks(ocultarDock)
    }

    override fun onDestroy() {
        super.onDestroy()
        principal.removeCallbacksAndMessages(null)
        escena.liberar()
        trabajo.shutdownNow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        principal.removeCallbacks(ocultarDock)
        principal.postDelayed(ocultarDock, 45_000L)
        if (modoCuadro) {
            // La primera tecla solo despierta el dock; no abre nada.
            if (event.action == KeyEvent.ACTION_UP) salirModoCuadro()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // Es la pantalla de inicio: "Atrás" no tiene a dónde regresar.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    // Si ya estamos en inicio y presionan Home, regresa el foco a la primera app.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (Intent.ACTION_MAIN == intent.action) {
            salirModoCuadro()
            dock.smoothScrollTo(0, 0)
            fila.getChildAt(0)?.requestFocus()
        }
    }

    private fun entrarModoCuadro() {
        modoCuadro = true
        dock.animate().alpha(0f).translationY(escena.dp(40).toFloat()).setDuration(600).start()
        acomodar()
    }

    private fun salirModoCuadro() {
        modoCuadro = false
        dock.animate().alpha(1f).translationY(0f).setDuration(250).start()
        acomodar()
        principal.removeCallbacks(ocultarDock)
        principal.postDelayed(ocultarDock, 45_000L)
        if (currentFocus == null) fila.getChildAt(0)?.requestFocus()
    }
}
