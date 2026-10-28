package dev.robertovillegas.inicio

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AppsActivity : Activity() {
    private lateinit var escena: Escena

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = FrameLayout(this)

        // El mismo paisaje del inicio detrás, oscurecido, para que se sienta parte de la misma pantalla.
        escena = Escena(this)
        escena.info.visibility = View.GONE
        escena.credito.visibility = View.GONE
        raiz.addView(escena, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(View(this).apply { setBackgroundColor(0xD90B0F14.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        // Columnas calculadas con el ancho real para que ninguna tarjeta quede cortada.
        val columnas = 5
        val margen = 72
        val hueco = 28
        val anchoDp = resources.configuration.screenWidthDp
        val ancho = (anchoDp - 2 * margen - (columnas - 1) * hueco) / columnas
        val alto = ancho * 9 / 16

        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(margen), dp(44), dp(margen), dp(56))
        }
        contenido.addView(TextView(this).apply {
            text = "Todas las apps"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        val rejilla = GridLayout(this).apply {
            columnCount = columnas
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(28), 0, 0)
        }
        Apps.todas(this).forEachIndexed { i, app ->
            val t = Tarjeta(this, app, ancho, alto)
            t.alMantener = { menu(app) }
            rejilla.addView(t, GridLayout.LayoutParams().apply {
                width = dp(ancho)
                setMargins(0, 0, if (i % columnas == columnas - 1) 0 else dp(hueco), dp(44))
            })
        }
        contenido.addView(rejilla)
        raiz.addView(ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(contenido)
        }, FrameLayout.LayoutParams(-1, -1))
        raiz.clipChildren = false
        setContentView(raiz)
        rejilla.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        escena.iniciar(180_000L)
    }

    override fun onPause() {
        super.onPause()
        escena.detener()
    }

    override fun onDestroy() {
        super.onDestroy()
        escena.liberar()
    }

    // Mantener OK sobre una app la agrega al dock del inicio o la quita.
    private fun menu(app: App) {
        val paquete = app.paquete ?: return
        val enDock = Dock.contiene(this, paquete)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.nombre)
            .setItems(arrayOf(if (enDock) "Quitar del inicio" else "Agregar al inicio", "Cancelar")) { _, i ->
                if (i == 0) if (enDock) Dock.quitar(this, paquete) else Dock.agregar(this, paquete)
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
