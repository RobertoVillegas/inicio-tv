package dev.robertovillegas.inicio

import android.view.Choreographer
import kotlin.math.abs

/**
 * Un resorte para mover algo hacia un destino, como los desplazamientos de la app de Apple TV:
 * masa 1, rigidez 150, amortiguamiento 30. Arranca rápido, frena suave y no rebota. Si el destino
 * cambia a medio camino (el control se mantiene presionado), sigue desde donde va, sin cortes.
 */
class Resorte(private val aplicar: (Float) -> Unit) : Choreographer.FrameCallback {
    var destino = 0f
        private set
    private var valor = 0f
    private var velocidad = 0f
    private var anterior = 0L
    private var corriendo = false

    fun ir(hacia: Float, desde: Float) {
        destino = hacia
        if (corriendo) return
        valor = desde
        velocidad = 0f
        anterior = 0L
        corriendo = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    /** Fija el valor sin animar. */
    fun saltar(a: Float) {
        Choreographer.getInstance().removeFrameCallback(this)
        corriendo = false
        destino = a
        valor = a
        velocidad = 0f
        aplicar(a)
    }

    override fun doFrame(ahora: Long) {
        // Si la TV se atrasa un cuadro, el resorte avanza el tiempo real (con tope) en pasos chicos.
        var dt = if (anterior == 0L) 1 / 60f else ((ahora - anterior) / 1e9f).coerceAtMost(1 / 15f)
        anterior = ahora
        while (dt > 0f) {
            val h = minOf(dt, 1 / 240f)
            velocidad += (-RIGIDEZ * (valor - destino) - AMORTIGUAMIENTO * velocidad) * h
            valor += velocidad * h
            dt -= h
        }
        if (abs(valor - destino) < 0.5f && abs(velocidad) < 10f) {
            valor = destino
            corriendo = false
        } else {
            Choreographer.getInstance().postFrameCallback(this)
        }
        aplicar(valor)
    }

    private companion object {
        const val RIGIDEZ = 150f
        const val AMORTIGUAMIENTO = 30f
    }
}
