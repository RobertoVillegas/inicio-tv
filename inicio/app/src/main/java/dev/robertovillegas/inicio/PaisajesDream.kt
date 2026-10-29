package dev.robertovillegas.inicio

import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.FrameLayout
import java.util.concurrent.Executors

/**
 * Protector de pantalla: paisajes en movimiento y la hora, que cambia de esquina cada pocos
 * minutos para que ningún pixel muestre lo mismo mucho tiempo.
 */
class PaisajesDream : DreamService() {
    private lateinit var escena: Escena
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var esquina = 0
    private lateinit var sonando: Sonando

    private val cadaMinuto = object : Runnable {
        override fun run() {
            // El protector también cambia el apagado: la noche puede caer con la TV ya en reposo.
            val noche = Noche.esDeNoche()
            escena.ponerNoche(noche)
            trabajo.execute { Noche.ajustarApagado(this@PaisajesDream, noche) }
            principal.postDelayed(this, 60_000L)
        }
    }

    private val mover = object : Runnable {
        override fun run() {
            esquina = (esquina + 1) % 4
            escena.info.animate().alpha(0f).setDuration(1500).withEndAction {
                val lp = escena.info.layoutParams as FrameLayout.LayoutParams
                val m = escena.dp(56)
                lp.gravity = when (esquina) {
                    0 -> Gravity.BOTTOM or Gravity.START
                    1 -> Gravity.TOP or Gravity.START
                    2 -> Gravity.TOP or Gravity.END
                    else -> Gravity.BOTTOM or Gravity.END
                }
                lp.setMargins(m, m, m, m)
                // El crédito de la foto se va a la esquina contraria a la hora.
                val lc = escena.credito.layoutParams as FrameLayout.LayoutParams
                lc.gravity = when (esquina) {
                    0 -> Gravity.BOTTOM or Gravity.END
                    1 -> Gravity.BOTTOM or Gravity.END
                    2 -> Gravity.BOTTOM or Gravity.START
                    else -> Gravity.TOP or Gravity.START
                }
                lc.setMargins(m, m, m, m)
                escena.info.requestLayout()
                escena.credito.requestLayout()
                escena.info.animate().alpha(1f).setDuration(1500).start()
            }.start()
            principal.postDelayed(this, 4 * 60_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        escena = Escena(this)
        setContentView(escena)
        sonando = Sonando(this) { escena.ponerCancion(it) }
        val m = escena.dp(56)
        (escena.info.layoutParams as FrameLayout.LayoutParams).setMargins(m, m, m, m)
        (escena.credito.layoutParams as FrameLayout.LayoutParams).setMargins(m, m, m, m)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        escena.iniciar(75_000L)
        sonando.iniciar()
        principal.post(cadaMinuto)
        trabajo.execute {
            val c = ClimaLocal.obtener(this)
            principal.post { escena.ponerClima(c); escena.ponerNoche(Noche.esDeNoche()) }
        }
        principal.postDelayed(mover, 4 * 60_000L)
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        principal.removeCallbacksAndMessages(null)
        sonando.detener()
        escena.detener()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        escena.liberar()
        trabajo.shutdownNow()
    }
}
