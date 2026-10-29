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
 * Photos uploaded from a phone and landscapes saved with ♡: what the rotation shows (mixed, mine only or
 * landscapes only) and, photo by photo, whether it's in the rotation, fills the screen, is pinned or deleted.
 */
class GalleryActivity : Activity() {
    private lateinit var scene: Scene
    private lateinit var modes: LinearLayout
    private lateinit var summary: TextView
    private lateinit var grid: GridLayout
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val tiles = mutableListOf<Tile>()
    private var focusedBefore = 0
    private var tileWidth = 0
    private var tileHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        scene = Scene(this)
        scene.info.visibility = View.GONE
        scene.credit.visibility = View.GONE
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(0xD90B0F14.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        val margin = 72
        tileWidth = (resources.configuration.screenWidthDp - 2 * margin - (COLUMNS - 1) * GAP) / COLUMNS
        tileHeight = tileWidth * 9 / 16

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(margin), dp(44), dp(margin), dp(56))
        }
        content.addView(text("Mis fotos", 30f, "sans-serif-light"))
        summary = text("", 15f, "sans-serif").apply { alpha = 0.7f }
        content.addView(summary, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(4) })

        modes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        content.addView(modes, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(20) })

        grid = GridLayout(this).apply {
            columnCount = COLUMNS
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(28), 0, 0)
        }
        content.addView(grid)
        root.addView(ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            addView(content)
        }, FrameLayout.LayoutParams(-1, -1))
        root.clipChildren = false
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        scene.start(180_000L)
        load(focus = null)
    }

    override fun onPause() {
        super.onPause()
        scene.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scene.release()
        worker.shutdownNow()
    }

    private fun load(focus: String?) {
        drawModes()
        worker.execute {
            val all = Photos.list(this)
            val mine = all.filter { it.own }.sortedByDescending { it.file.name }
            val saved = all.filter { it.favorite && !it.own }
            val thumbs = (mine + saved).associate { p ->
                p.file.name to BitmapFactory.decodeFile(p.file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
                    ?.let { BitmapDrawable(resources, it) }
            }
            main.post { drawPhotos(mine, saved, thumbs, focus) }
        }
    }

    private fun drawModes() {
        val current = Photos.mode(this)
        modes.removeAllViews()
        listOf(Mode.MIX to "Mezclar con paisajes", Mode.MINE to "Solo mis fotos", Mode.LANDSCAPES to "Solo paisajes")
            .forEach { (m, name) ->
                modes.addView(button(name, selected = m == current) {
                    Photos.setMode(this, m)
                    drawModes()
                    modes.getChildAt(m.ordinal)?.requestFocus()
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) })
            }
        modes.addView(button("＋  Agregar fotos", selected = false) {
            startActivity(Intent(this, UploadActivity::class.java))
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(24) })
    }

    private fun drawPhotos(mine: List<Photo>, saved: List<Photo>, thumbs: Map<String, BitmapDrawable?>, focus: String?) {
        val pinned = Photos.pinned(this)
        val excluded = mine.count { !it.included }
        summary.text = when {
            mine.isEmpty() -> "Todavía no hay fotos tuyas. Usa “Agregar fotos” y elígelas desde el celular."
            excluded == 0 -> "${mine.size} ${if (mine.size == 1) "foto tuya" else "fotos tuyas"}. Elige una para cambiar cómo se ve o si sale."
            else -> "${mine.size} fotos tuyas, $excluded fuera de la rotación. Elige una para cambiar cómo se ve o si sale."
        }
        val returnToGrid = focus != null
        grid.removeAllViews()
        tiles.clear()

        fun section(photos: List<Photo>) = photos.forEachIndexed { i, photo ->
            val name = buildString {
                if (photo.file.name == pinned) append("◎ ")
                if (!photo.included) append("Fuera · ")
                append(photo.place.ifEmpty { photo.description.ifEmpty { "Foto" } })
            }
            val t = Tile(this, App(name, thumbs[photo.file.name], Intent()), tileWidth, tileHeight)
            t.alpha = if (photo.included) 1f else 0.4f
            t.setOnClickListener { options(photo) }
            grid.addView(t, GridLayout.LayoutParams().apply {
                width = dp(tileWidth)
                setMargins(0, 0, if (i % COLUMNS == COLUMNS - 1) 0 else dp(GAP), dp(44))
            })
            tiles += t
            if (photo.file.name == focus) t.requestFocus()
        }
        section(mine)
        if (saved.isNotEmpty()) {
            val title = "Paisajes guardados   ·   ${saved.size}   ·   salen el doble y nunca se borran"
            grid.addView(text(title, 17f, "sans-serif-medium").apply { alpha = 0.85f },
                GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(0, COLUMNS)).apply {
                    setMargins(0, if (mine.isEmpty()) 0 else dp(8), 0, dp(20))
                })
            section(saved)
        }
        // When the focused photo is gone (deleted or unsaved), focus moves to the one that took its place.
        if (!grid.hasFocus() && (returnToGrid || !modes.hasFocus())) {
            (tiles.getOrNull(focusedBefore.coerceAtMost(tiles.size - 1)) ?: modes.getChildAt(0))?.requestFocus()
        }
    }

    private fun options(photo: Photo) {
        focusedBefore = tiles.indexOfFirst { it.hasFocus() }.coerceAtLeast(0)
        val isPinned = Photos.pinned(this) == photo.file.name
        val pin = (if (isPinned) "◎  Volver a cambiar de foto" else "◎  Dejar siempre esta foto") to {
            Photos.setPinned(this, if (isPinned) null else photo)
        }
        val options = if (!photo.own) listOf(
            "♥  Quitar de guardadas" to { Photos.setFavorite(this, photo, false) },
            pin,
            "✕  No volver a mostrar" to { Photos.hide(this, photo) },
            "Cancelar" to {},
        ) else listOf(
            (if (photo.included) "–  Sacar de la rotación" else "+  Volver a la rotación") to {
                Photos.setIncluded(this, photo, !photo.included)
            },
            (if (photo.fill) "⤢  Verla completa" else "⤢  Llenar la pantalla") to {
                Photos.setFill(this, photo, !photo.fill)
            },
            pin,
            "✕  Borrar" to { Photos.hide(this, photo) },
            "Cancelar" to {},
        )
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(photo.place.ifEmpty { if (photo.own) photo.description.ifEmpty { "Tu foto" } else "Paisaje guardado" })
            .setItems(options.map { it.first }.toTypedArray()) { _, i ->
                if (i == options.size - 1) return@setItems
                worker.execute {
                    options[i].second()
                    main.post { load(focus = photo.file.name) }
                }
            }
            .show()
    }

    private fun button(label: String, selected: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(18), dp(9), dp(18), dp(9))
        val normal = GradientDrawable().apply {
            cornerRadius = dp(22).toFloat()
            setColor(if (selected) 0x59FFFFFF else 0x1FFFFFFF)
            if (selected) setStroke(dp(2), Color.WHITE)
        }
        val focused = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(Color.WHITE) }
        background = normal
        setTextColor(Color.WHITE)
        setOnFocusChangeListener { _, hasFocus ->
            background = if (hasFocus) focused else normal
            setTextColor(if (hasFocus) 0xFF101418.toInt() else Color.WHITE)
            animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(150).start()
        }
        setOnClickListener { action() }
    }

    private fun text(t: String, sp: Float, family: String) = TextView(this).apply {
        text = t
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.create(family, Typeface.NORMAL)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLUMNS = 5
        const val GAP = 28
    }
}
