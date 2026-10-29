package dev.robertovillegas.inicio

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private lateinit var escena: Escena
    private lateinit var dock: HorizontalScrollView
    private lateinit var fila: LinearLayout
    private val resorteDock = Resorte { dock.scrollTo(it.toInt(), 0) }
    private lateinit var menuFoto: TextView
    private var favoritaActual = false
    private lateinit var sonando: Sonando
    private val principal = Handler(Looper.getMainLooper())
    private val trabajo = Executors.newSingleThreadExecutor()
    private var modoCuadro = false
    private var versionDock = -1

    // Mientras se mueve una app dentro del dock: cuál es y en qué posición va.
    private var moviendo: Tarjeta? = null

    // La tecla que termina de mover se suelta después; ese "soltar" no debe abrir la app.
    private var tragarSoltar = false

    // Sin tocar el control, el dock se esconde y queda solo el paisaje.
    private val ocultarDock = Runnable { entrarModoCuadro() }
    private val actualizarClima = object : Runnable {
        override fun run() {
            trabajo.execute {
                val c = ClimaLocal.obtener(this@HomeActivity)
                principal.post { escena.ponerClima(c); revisarNoche() }
            }
            principal.postDelayed(this, 30 * 60_000L)
        }
    }
    private val cadaMinuto = object : Runnable {
        override fun run() {
            revisarNoche()
            principal.postDelayed(this, 60_000L)
        }
    }
    private val refrescarFondos = object : Runnable {
        override fun run() {
            trabajo.execute {
                Fondos.refrescar(this@HomeActivity)
                Actualizacion.revisar(this@HomeActivity)
                principal.post { avisarActualizacion() }
            }
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
        // Al enfocar una tarjeta el dock deja aire a los lados. En la primera y la última eso
        // alcanza la orilla, así que se ve completa (con su margen) sin otro clic hacia el borde.
        // El desplazamiento lo hace el resorte, no el scroll de Android, que dura siempre lo mismo
        // y se corta al presionar seguido.
        val aire = escena.dp(72)
        dock = object : HorizontalScrollView(this) {
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
                val total = getChildAt(0)?.width ?: return 0
                var destino = resorteDock.destino.toInt()
                if (rect.left - aire < destino) destino = rect.left - aire
                if (rect.right + aire > destino + width) destino = rect.right + aire - width
                destino = destino.coerceIn(0, maxOf(0, total - width))
                if (destino != resorteDock.destino.toInt()) resorteDock.ir(destino.toFloat(), scrollX.toFloat())
                return 0
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            descendantFocusability = FrameLayout.FOCUS_AFTER_DESCENDANTS
            addView(fila)
        }
        raiz.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        // Arriba a la derecha, un botón ⋯ con las opciones de la foto. Se llega con ↑ desde el dock.
        menuFoto = boton("•••") { opcionesFoto() }
        raiz.addView(menuFoto, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, escena.dp(32), escena.dp(48), 0)
        })
        escena.alCambiarFoto = { foto -> favoritaActual = foto.favorita }

        raiz.clipChildren = false
        setContentView(raiz)
        sonando = Sonando(this) { escena.ponerCancion(it) }
        acomodar()
        principal.post(refrescarFondos)
    }

    private fun boton(texto: String, accion: () -> Unit) = TextView(this).apply {
        text = texto
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.1f
        setPadding(escena.dp(14), escena.dp(6), escena.dp(14), escena.dp(6))
        val normal = GradientDrawable().apply { cornerRadius = escena.dp(20).toFloat(); setColor(0x40000000) }
        val enfocado = GradientDrawable().apply { cornerRadius = escena.dp(20).toFloat(); setColor(Color.WHITE) }
        background = normal
        setTextColor(Color.WHITE)
        alpha = 0.75f
        setOnFocusChangeListener { _, foco ->
            background = if (foco) enfocado else normal
            setTextColor(if (foco) 0xFF101418.toInt() else Color.WHITE)
            animate().alpha(if (foco) 1f else 0.75f).scaleX(if (foco) 1.06f else 1f)
                .scaleY(if (foco) 1.06f else 1f).setDuration(150).start()
        }
        setOnClickListener { accion() }
    }

    private fun opcionesFoto() {
        val foto = escena.actual ?: return
        val fija = Fondos.fija(this) != null
        val opciones = buildList<Pair<String, () -> Unit>> {
            add((if (favoritaActual) "♥  Quitar de guardadas" else "♡  Guardar esta foto") to { alternarFavorita(foto) })
            if (fija) {
                add("◎  Volver a cambiar de foto" to { fijar(null) })
            } else {
                add("↻  Otra foto" to { escena.otra() })
                add("◎  Dejar siempre esta foto" to { fijar(foto) })
            }
            add((if (foto.propia) "✕  Borrar esta foto" else "✕  No volver a mostrar") to { ocultarFoto() })
            if (foto.propia) add((if (foto.llenar) "⤢  Verla completa" else "⤢  Llenar la pantalla") to {
                trabajo.execute {
                    Fondos.ponerLlenar(this@HomeActivity, foto, !foto.llenar)
                    principal.post { escena.mostrarDeNuevo() }
                }
            })
            add("▦  Mis fotos" to { startActivity(Intent(this@HomeActivity, GaleriaActivity::class.java)) })
            Actualizacion.disponible?.let { v -> add("↑  Actualizar Inicio a ${v.nombre}" to { actualizar(v) }) }
            add("Cancelar" to {})
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(foto.lugar.ifEmpty { if (foto.propia) "Tu foto" else "Esta foto" })
            .setItems(opciones.map { it.first }.toTypedArray()) { _, i -> opciones[i].second() }
            .show()
    }

    private fun fijar(foto: Foto?) {
        Fondos.ponerFija(this, foto)
        escena.recargar(saltar = foto == null)
    }

    // Con versión nueva el botón dice "↑ •••", para que se note sin abrir el menú.
    private fun avisarActualizacion() {
        menuFoto.text = if (Actualizacion.disponible != null) "↑  •••" else "•••"
    }

    private fun actualizar(v: Actualizacion.Version) {
        Toast.makeText(this, "Descargando Inicio ${v.nombre}…", Toast.LENGTH_LONG).show()
        trabajo.execute {
            try {
                Actualizacion.instalar(this, v)
            } catch (e: Exception) {
                principal.post { Toast.makeText(this, "No se pudo actualizar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun alternarFavorita(foto: Foto) {
        val nueva = !favoritaActual
        favoritaActual = nueva
        trabajo.execute {
            Fondos.marcarFavorita(this, foto, nueva)
            principal.post { escena.recargar(saltar = false) }
        }
    }

    private fun ocultarFoto() {
        val foto = escena.actual ?: return
        trabajo.execute {
            Fondos.ocultar(this, foto)
            principal.post { escena.recargar(saltar = true) }
        }
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
        versionDock = Dock.version
        fila.removeAllViews()
        val hdmi = Entradas.hdmi(this)
        val extras = listOf(App("Todas las apps", icono(android.R.drawable.ic_dialog_dialer),
            Intent(this, AppsActivity::class.java))) +
            hdmi.ifEmpty { listOf(App("Entradas", icono(android.R.drawable.ic_menu_slideshow), Apps.entradas())) } +
            App("Ajustes", icono(android.R.drawable.ic_menu_preferences), Apps.ajustes())
        Apps.favoritas(this).forEach { app ->
            val t = Tarjeta(this, app, 128, 72)
            t.alMantener = { menuDock(t, app) }
            agregarAlDock(t)
        }
        extras.forEach { agregarAlDock(Tarjeta(this, it, 128, 72)) }
        fila.getChildAt(0)?.requestFocus()
    }

    private fun agregarAlDock(t: Tarjeta) =
        fila.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = escena.dp(14) })

    private fun menuDock(t: Tarjeta, app: App) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.nombre)
            .setItems(arrayOf("Mover", "Quitar del inicio", "Cancelar")) { _, i ->
                when (i) {
                    0 -> empezarAMover(t)
                    1 -> app.paquete?.let { Dock.quitar(this, it); llenarDock() }
                }
            }
            .show()
    }

    private fun empezarAMover(t: Tarjeta) {
        moviendo = t
        t.temblar(true)
        t.requestFocus()
    }

    private fun moverA(direccion: Int) {
        val t = moviendo ?: return
        val i = fila.indexOfChild(t)
        val favoritas = Dock.lista(this).size.coerceAtMost(fila.childCount)
        val j = i + direccion
        // Solo se mueve entre las apps del dock, no entre los accesos fijos del final.
        if (j < 0 || j >= favoritas) return
        fila.removeViewAt(i)
        fila.addView(t, j, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = escena.dp(14) })
        t.requestFocus()
    }

    private fun terminarDeMover() {
        val t = moviendo ?: return
        moviendo = null
        t.temblar(false)
        val orden = (0 until fila.childCount).mapNotNull { (fila.getChildAt(it) as? Tarjeta)?.app?.paquete }
            .filter { Dock.contiene(this, it) }
        Dock.guardar(this, orden)
        versionDock = Dock.version
    }

    private fun icono(res: Int) = LayerDrawable(arrayOf(
        GradientDrawable().apply { setColor(0x40FFFFFF) },
        getDrawable(res)!!.mutate().apply { setTint(Color.WHITE) },
    )).apply { setLayerInset(1, escena.dp(52), escena.dp(22), escena.dp(52), escena.dp(22)) }

    private fun revisarNoche() {
        val noche = Noche.esDeNoche()
        escena.ponerNoche(noche)
        trabajo.execute { Noche.ajustarApagado(this, noche) }
    }

    override fun onResume() {
        super.onResume()
        if (versionDock != Dock.version) llenarDock()
        escena.iniciar(180_000L)
        sonando.iniciar()
        principal.post(actualizarClima)
        principal.post(cadaMinuto)
        salirModoCuadro()
    }

    override fun onPause() {
        super.onPause()
        terminarDeMover()
        escena.detener()
        sonando.detener()
        principal.removeCallbacks(actualizarClima)
        principal.removeCallbacks(cadaMinuto)
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
        if (tragarSoltar && event.action == KeyEvent.ACTION_UP) {
            tragarSoltar = false
            return true
        }
        if (moviendo != null) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moverA(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moverA(1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                    terminarDeMover()
                    tragarSoltar = true
                }
            }
            return true
        }
        // En las orillas del dock el foco no salta al ••• de arriba: la tarjeta solo se inclina.
        val enfocada = currentFocus as? Tarjeta
        if (enfocada != null && event.action == KeyEvent.ACTION_DOWN && enfocada.parent == fila) {
            val i = fila.indexOfChild(enfocada)
            when {
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && i == fila.childCount - 1 -> {
                    enfocada.inclinar(android.view.View.FOCUS_RIGHT); return true
                }
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && i == 0 -> {
                    enfocada.inclinar(android.view.View.FOCUS_LEFT); return true
                }
            }
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
            terminarDeMover()
            salirModoCuadro()
            resorteDock.ir(0f, dock.scrollX.toFloat())
            fila.getChildAt(0)?.requestFocus()
        }
    }

    private fun entrarModoCuadro() {
        if (moviendo != null) return
        modoCuadro = true
        dock.animate().alpha(0f).translationY(escena.dp(40).toFloat()).setDuration(600).start()
        menuFoto.animate().alpha(0f).setDuration(600).start()
        acomodar()
    }

    private fun salirModoCuadro() {
        modoCuadro = false
        dock.animate().alpha(1f).translationY(0f).setDuration(250).start()
        menuFoto.animate().alpha(1f).setDuration(250).start()
        acomodar()
        principal.removeCallbacks(ocultarDock)
        principal.postDelayed(ocultarDock, 45_000L)
        if (currentFocus == null) fila.getChildAt(0)?.requestFocus()
    }
}
