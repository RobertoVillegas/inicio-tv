package dev.robertovillegas.inicio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Las fotos subidas desde el celular: qué sale en pantalla (mezcla, solo mías o solo paisajes)
 * y, foto por foto, si entra en la rotación, si llena la pantalla, fijarla o borrarla.
 */
class GaleriaActivity : Activity() {
    private lateinit var escena: Escena
    private lateinit var modos: LinearLayout
    private lateinit var resumen: TextView
    private lateinit var rejilla: GridLayout
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var ancho = 0
    private var alto = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = FrameLayout(this)
        escena = Escena(this)
        escena.info.visibility = View.GONE
        escena.credito.visibility = View.GONE
        raiz.addView(escena, FrameLayout.LayoutParams(-1, -1))
        raiz.addView(View(this).apply { setBackgroundColor(0xD90B0F14.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val margen = 72
        ancho = (resources.configuration.screenWidthDp - 2 * margen - (COLUMNAS - 1) * HUECO) / COLUMNAS
        alto = ancho * 9 / 16

        val contenido = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(margen), dp(44), dp(margen), dp(56))
        }
        contenido.addView(texto("Mis fotos", 30f, "sans-serif-light"))
        resumen = texto("", 15f, "sans-serif").apply { alpha = 0.7f }
        contenido.addView(resumen, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })

        modos = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        contenido.addView(modos, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })

        rejilla = GridLayout(this).apply {
            columnCount = COLUMNAS
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(28), 0, 0)
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
    }

    override fun onResume() {
        super.onResume()
        escena.iniciar(180_000L)
        llenar(enfocar = null)
    }

    override fun onPause() {
        super.onPause()
        escena.detener()
    }

    override fun onDestroy() {
        super.onDestroy()
        escena.liberar()
        trabajo.shutdownNow()
    }

    private fun llenar(enfocar: String?) {
        dibujarModos()
        trabajo.execute {
            val fotos = Fondos.listar(this).filter { it.propia }.sortedByDescending { it.archivo.name }
            val miniaturas = fotos.map { f ->
                BitmapFactory.decodeFile(f.archivo.path, BitmapFactory.Options().apply { inSampleSize = 4 })
            }
            principal.post { dibujarFotos(fotos, miniaturas.map { it?.let { b -> BitmapDrawable(resources, b) } }, enfocar) }
        }
    }

    private fun dibujarModos() {
        val actual = Fondos.modo(this)
        modos.removeAllViews()
        listOf(Modo.MEZCLA to "Mezclar con paisajes", Modo.MIAS to "Solo mis fotos", Modo.PAISAJES to "Solo paisajes")
            .forEach { (m, nombre) ->
                modos.addView(boton(nombre, elegido = m == actual) {
                    Fondos.ponerModo(this, m)
                    dibujarModos()
                    modos.getChildAt(m.ordinal)?.requestFocus()
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
            }
        modos.addView(boton("＋  Agregar fotos", elegido = false) {
            startActivity(Intent(this, SubirActivity::class.java))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(24) })
    }

    private fun dibujarFotos(fotos: List<Foto>, miniaturas: List<BitmapDrawable?>, enfocar: String?) {
        val fija = Fondos.fija(this)
        val fuera = fotos.count { !it.incluida }
        resumen.text = when {
            fotos.isEmpty() -> "Todavía no hay fotos tuyas. Usa “Agregar fotos” y elígelas desde el celular."
            fuera == 0 -> "${fotos.size} ${if (fotos.size == 1) "foto" else "fotos"}. Elige una para cambiar cómo se ve o si sale."
            else -> "${fotos.size} fotos, $fuera fuera de la rotación. Elige una para cambiar cómo se ve o si sale."
        }
        val volverARejilla = enfocar != null
        rejilla.removeAllViews()
        fotos.forEachIndexed { i, foto ->
            val nombre = buildString {
                if (foto.archivo.name == fija) append("◎ ")
                if (!foto.incluida) append("Fuera · ")
                append(foto.lugar.ifEmpty { foto.descripcion.ifEmpty { "Foto" } })
            }
            val t = Tarjeta(this, App(nombre, miniaturas[i], Intent()), ancho, alto)
            t.alpha = if (foto.incluida) 1f else 0.4f
            t.setOnClickListener { opciones(foto) }
            rejilla.addView(t, GridLayout.LayoutParams().apply {
                width = dp(ancho)
                setMargins(0, 0, if (i % COLUMNAS == COLUMNAS - 1) 0 else dp(HUECO), dp(44))
            })
            if (foto.archivo.name == enfocar) t.requestFocus()
        }
        // Si la foto enfocada ya no está (se borró), el foco pasa a la que quedó en su lugar.
        if (!rejilla.hasFocus() && (volverARejilla || !modos.hasFocus())) {
            val i = enfocadaAntes.coerceAtMost(rejilla.childCount - 1)
            (rejilla.getChildAt(i) ?: modos.getChildAt(0))?.requestFocus()
        }
    }

    private var enfocadaAntes = 0

    private fun opciones(foto: Foto) {
        enfocadaAntes = (0 until rejilla.childCount).firstOrNull { rejilla.getChildAt(it).hasFocus() } ?: 0
        val fija = Fondos.fija(this) == foto.archivo.name
        val opciones = buildList<Pair<String, () -> Unit>> {
            add((if (foto.incluida) "–  Sacar de la rotación" else "+  Volver a la rotación") to {
                Fondos.ponerIncluida(this@GaleriaActivity, foto, !foto.incluida)
            })
            add((if (foto.llenar) "⤢  Verla completa" else "⤢  Llenar la pantalla") to {
                Fondos.ponerLlenar(this@GaleriaActivity, foto, !foto.llenar)
            })
            add((if (fija) "◎  Volver a cambiar de foto" else "◎  Dejar siempre esta foto") to {
                Fondos.ponerFija(this@GaleriaActivity, if (fija) null else foto)
            })
            add("✕  Borrar" to { Fondos.ocultar(this@GaleriaActivity, foto) })
            add("Cancelar" to {})
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(foto.lugar.ifEmpty { foto.descripcion.ifEmpty { "Tu foto" } })
            .setItems(opciones.map { it.first }.toTypedArray()) { _, i ->
                if (i == opciones.size - 1) return@setItems
                trabajo.execute {
                    opciones[i].second()
                    principal.post { llenar(enfocar = foto.archivo.name) }
                }
            }
            .show()
    }

    private fun boton(texto: String, elegido: Boolean, accion: () -> Unit) = TextView(this).apply {
        text = texto
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(18), dp(9), dp(18), dp(9))
        val normal = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (elegido) 0x59FFFFFF else 0x1FFFFFFF)
            if (elegido) setStroke(dp(2), Color.WHITE)
        }
        val enfocado = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.WHITE) }
        background = normal
        setTextColor(Color.WHITE)
        setOnFocusChangeListener { _, foco ->
            background = if (foco) enfocado else normal
            setTextColor(if (foco) 0xFF101418.toInt() else Color.WHITE)
            animate().scaleX(if (foco) 1.06f else 1f).scaleY(if (foco) 1.06f else 1f).setDuration(150).start()
        }
        setOnClickListener { accion() }
    }

    private fun texto(t: String, sp: Float, familia: String) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(familia, Typeface.NORMAL)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNAS = 5
        const val HUECO = 28
    }
}
