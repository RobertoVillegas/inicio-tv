package dev.robertovillegas.inicio

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentCallbacks2
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class HomeActivity : Activity() {
    private lateinit var scene: Scene
    private lateinit var dock: HorizontalScrollView
    private lateinit var row: LinearLayout
    private val dockSpring = Spring { dock.scrollTo(it.toInt(), 0) }
    private lateinit var photoMenu: TextView
    private var currentIsFavorite = false
    private lateinit var nowPlaying: NowPlaying
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var frameMode = false
    private var dockVersion = -1
    private var onScreen = false
    private var inputsCallback: TvInputManager.TvInputCallback? = null

    // The tile being moved within the dock.
    private var moving: Tile? = null

    // The key that ends a move is released afterwards; that release must not open the app.
    private var swallowKeyUp = false

    // With no remote input, the dock hides and only the landscape is left.
    private val hideDock = Runnable { enterFrameMode() }
    private val refreshWeather = object : Runnable {
        override fun run() {
            worker.execute {
                val w = LocalWeather.fetch(this@HomeActivity)
                main.post { scene.setWeather(w); checkNight() }
            }
            main.postDelayed(this, 30 * 60_000L)
        }
    }
    private val everyMinute = object : Runnable {
        override fun run() {
            checkNight()
            main.postDelayed(this, 60_000L)
        }
    }
    private val refreshPhotos = object : Runnable {
        override fun run() {
            worker.execute {
                Photos.refresh(this@HomeActivity)
                Updater.check(this@HomeActivity)
                main.post { showUpdateBadge() }
            }
            main.postDelayed(this, 6 * 3600_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scene = Scene(this)
        val root = FrameLayout(this)
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))

        row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(scene.dp(40), scene.dp(14), scene.dp(40), scene.dp(44))
        }
        // A focused tile keeps some air on both sides, so the first and last show whole, margin included,
        // without another press toward the edge. The spring does the scrolling: Android's own scroll always
        // takes the same time and cuts off on repeated presses.
        val air = scene.dp(40)
        dock = object : HorizontalScrollView(this) {
            override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int {
                val total = getChildAt(0)?.width ?: return 0
                var target = dockSpring.target.toInt()
                if (rect.left - air < target) target = rect.left - air
                if (rect.right + air > target + width) target = rect.right + air - width
                target = target.coerceIn(0, maxOf(0, total - width))
                if (target != dockSpring.target.toInt()) dockSpring.animateTo(target.toFloat(), scrollX.toFloat())
                return 0
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            isFocusable = false
            descendantFocusability = FrameLayout.FOCUS_AFTER_DESCENDANTS
            addView(row)
        }
        root.addView(dock, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))

        // Top right, a ••• button with the photo's options, reached with ↑ from the dock.
        photoMenu = pillButton("•••") { showPhotoOptions() }
        root.addView(photoMenu, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, scene.dp(32), scene.dp(48), 0)
        })
        scene.onPhotoChange = { photo -> currentIsFavorite = photo.favorite }

        root.clipChildren = false
        setContentView(root)
        nowPlaying = NowPlaying(this) { scene.setSong(it) }
        layoutOverlay()
        main.post(refreshPhotos)
        // Plugging something into an HDMI port adds its tile to the dock, and unplugging removes it.
        inputsCallback = Inputs.watch(this) {
            if (onScreen && moving == null) fillDock() else dockVersion = -1
        }
        warnIfHomeLost(intent)
    }

    private fun warnIfHomeLost(intent: Intent) {
        if (!intent.getBooleanExtra(HomeGuard.EXTRA_LOST_HOME, false)) return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Google TV volvió a ser la pantalla de inicio")
            .setMessage("Seguramente se actualizó la TV. Para que el botón Home vuelva a abrir Inicio, " +
                "corre FIX=1 ./check.sh desde la computadora.")
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun pillButton(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        isFocusable = true
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.1f
        setPadding(scene.dp(14), scene.dp(6), scene.dp(14), scene.dp(6))
        val normal = GradientDrawable().apply { cornerRadius = scene.dp(20).toFloat(); setColor(0x40000000) }
        val focused = GradientDrawable().apply { cornerRadius = scene.dp(20).toFloat(); setColor(Color.WHITE) }
        background = normal
        setTextColor(Color.WHITE)
        alpha = 0.75f
        setOnFocusChangeListener { _, hasFocus ->
            background = if (hasFocus) focused else normal
            setTextColor(if (hasFocus) 0xFF101418.toInt() else Color.WHITE)
            animate().alpha(if (hasFocus) 1f else 0.75f).scaleX(if (hasFocus) 1.06f else 1f)
                .scaleY(if (hasFocus) 1.06f else 1f).setDuration(150).start()
        }
        setOnClickListener { action() }
    }

    private fun showPhotoOptions() {
        val photo = scene.current ?: return
        val pinned = Photos.pinned(this) != null
        val options = buildList<Pair<String, () -> Unit>> {
            add((if (currentIsFavorite) "♥  Quitar de guardadas" else "♡  Guardar esta foto") to { toggleFavorite(photo) })
            if (pinned) {
                add("◎  Volver a cambiar de foto" to { pin(null) })
            } else {
                add("↻  Otra foto" to { scene.skip() })
                add("◎  Dejar siempre esta foto" to { pin(photo) })
            }
            add((if (photo.own) "✕  Borrar esta foto" else "✕  No volver a mostrar") to { hideCurrentPhoto() })
            if (photo.own) add((if (photo.fill) "⤢  Verla completa" else "⤢  Llenar la pantalla") to {
                worker.execute {
                    Photos.setFill(this@HomeActivity, photo, !photo.fill)
                    main.post { scene.redrawCurrent() }
                }
            })
            add("▦  Mis fotos" to { startActivity(Intent(this@HomeActivity, GalleryActivity::class.java)) })
            Updater.available?.let { v -> add("↑  Actualizar Inicio a ${v.name}" to { update(v) }) }
            add("Cancelar" to {})
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(photo.place.ifEmpty { if (photo.own) "Tu foto" else "Esta foto" })
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> options[i].second() }
            .show()
    }

    private fun pin(photo: Photo?) {
        Photos.setPinned(this, photo)
        scene.reload(advanceNow = photo == null)
    }

    // With a new version out, the button reads "↑ •••" so it shows without opening the menu.
    private fun showUpdateBadge() {
        photoMenu.text = if (Updater.available != null) "↑  •••" else "•••"
    }

    private fun update(v: Updater.Version) {
        Toast.makeText(this, "Descargando Inicio ${v.name}…", Toast.LENGTH_LONG).show()
        worker.execute {
            try {
                Updater.install(this, v)
            } catch (e: Exception) {
                main.post { Toast.makeText(this, "No se pudo actualizar: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun toggleFavorite(photo: Photo) {
        val favorite = !currentIsFavorite
        currentIsFavorite = favorite
        worker.execute {
            Photos.setFavorite(this, photo, favorite)
            main.post { scene.reload(advanceNow = false) }
        }
    }

    private fun hideCurrentPhoto() {
        val photo = scene.current ?: return
        worker.execute {
            Photos.hide(this, photo)
            main.post { scene.reload(advanceNow = true) }
        }
    }

    private fun layoutOverlay() {
        val m = scene.dp(48)
        val aboveDock = if (frameMode) m else scene.dp(170)
        (scene.info.layoutParams as FrameLayout.LayoutParams).setMargins(m, 0, 0, aboveDock)
        (scene.credit.layoutParams as FrameLayout.LayoutParams).setMargins(0, 0, m, aboveDock)
        scene.info.requestLayout()
        scene.credit.requestLayout()
    }

    private fun fillDock() {
        dockVersion = Dock.version
        row.removeAllViews()
        val hdmi = Inputs.hdmi(this)
        val fixed = listOf(App("Todas las apps", icon(android.R.drawable.ic_dialog_dialer),
            Intent(this, AppsActivity::class.java))) +
            hdmi.ifEmpty { listOf(App("Entradas", icon(android.R.drawable.ic_menu_slideshow), Apps.inputs())) } +
            App("Ajustes", icon(android.R.drawable.ic_menu_preferences), Apps.settings())
        Apps.docked(this).forEach { app ->
            val t = Tile(this, app, 128, 72)
            t.onLongPress = { showDockMenu(t, app) }
            addToDock(t)
        }
        fixed.forEach { addToDock(Tile(this, it, 128, 72)) }
        row.getChildAt(0)?.requestFocus()
    }

    private fun addToDock(t: Tile) =
        row.addView(t, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = scene.dp(14) })

    private fun showDockMenu(t: Tile, app: App) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.name)
            .setItems(arrayOf("Mover", "Quitar del inicio", "Cancelar")) { _, i ->
                when (i) {
                    0 -> startMoving(t)
                    1 -> app.packageName?.let { Dock.remove(this, it); fillDock() }
                }
            }
            .show()
    }

    private fun startMoving(t: Tile) {
        moving = t
        t.setWiggling(true)
        t.requestFocus()
    }

    private fun moveBy(step: Int) {
        val t = moving ?: return
        val i = row.indexOfChild(t)
        val docked = Dock.list(this).size.coerceAtMost(row.childCount)
        val j = i + step
        // Only among the docked apps, not the fixed tiles at the end.
        if (j < 0 || j >= docked) return
        row.removeViewAt(i)
        row.addView(t, j, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = scene.dp(14) })
        t.requestFocus()
    }

    private fun finishMoving() {
        val t = moving ?: return
        moving = null
        t.setWiggling(false)
        val order = (0 until row.childCount).mapNotNull { (row.getChildAt(it) as? Tile)?.app?.packageName }
            .filter { Dock.contains(this, it) }
        Dock.save(this, order)
        dockVersion = Dock.version
    }

    private fun icon(res: Int) = LayerDrawable(arrayOf(
        GradientDrawable().apply { setColor(0x40FFFFFF) },
        getDrawable(res)!!.mutate().apply { setTint(Color.WHITE) },
    )).apply { setLayerInset(1, scene.dp(52), scene.dp(22), scene.dp(52), scene.dp(22)) }

    private fun checkNight() {
        val night = Night.isNight()
        scene.setNight(night)
        worker.execute { Night.applySleepTimeout(this, night) }
    }

    override fun onStart() {
        super.onStart()
        onScreen = true
    }

    override fun onStop() {
        super.onStop()
        onScreen = false
    }

    // The two background photos take about 16 MB. They're released only when Android asks for memory while
    // another app is in front. Inicio stays a foreground process then (the system keeps MediaListener bound),
    // so it gets the RUNNING_* levels, never TRIM_MEMORY_BACKGROUND.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (onScreen) return
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) scene.releasePhotos()
    }

    override fun onResume() {
        super.onResume()
        if (!intent.getBooleanExtra(HomeGuard.EXTRA_LOST_HOME, false)) HomeGuard.remember(this)
        if (dockVersion != Dock.version) fillDock()
        scene.start(180_000L)
        nowPlaying.start()
        main.post(refreshWeather)
        main.post(everyMinute)
        exitFrameMode()
    }

    override fun onPause() {
        super.onPause()
        finishMoving()
        scene.stop()
        nowPlaying.stop()
        main.removeCallbacks(refreshWeather)
        main.removeCallbacks(everyMinute)
        main.removeCallbacks(hideDock)
    }

    override fun onDestroy() {
        super.onDestroy()
        main.removeCallbacksAndMessages(null)
        Inputs.unwatch(this, inputsCallback)
        scene.release()
        worker.shutdownNow()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        main.removeCallbacks(hideDock)
        main.postDelayed(hideDock, 45_000L)
        if (frameMode) {
            // The first key only brings the dock back; it opens nothing.
            if (event.action == KeyEvent.ACTION_UP) exitFrameMode()
            return true
        }
        if (swallowKeyUp && event.action == KeyEvent.ACTION_UP) {
            swallowKeyUp = false
            return true
        }
        if (moving != null) {
            if (event.action == KeyEvent.ACTION_DOWN) when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> moveBy(-1)
                KeyEvent.KEYCODE_DPAD_RIGHT -> moveBy(1)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK -> {
                    finishMoving()
                    swallowKeyUp = true
                }
            }
            return true
        }
        // At the dock's ends focus doesn't jump to the ••• above: the tile just tilts.
        val focused = currentFocus as? Tile
        if (focused != null && event.action == KeyEvent.ACTION_DOWN && focused.parent == row) {
            val i = row.indexOfChild(focused)
            when {
                event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && i == row.childCount - 1 -> {
                    focused.tilt(View.FOCUS_RIGHT); return true
                }
                event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && i == 0 -> {
                    focused.tilt(View.FOCUS_LEFT); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // This is the home screen: Back has nowhere to go.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = Unit

    // Home pressed while already home: focus goes back to the first app.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        warnIfHomeLost(intent)
        if (Intent.ACTION_MAIN == intent.action) {
            finishMoving()
            exitFrameMode()
            dockSpring.animateTo(0f, dock.scrollX.toFloat())
            row.getChildAt(0)?.requestFocus()
        }
    }

    private fun enterFrameMode() {
        if (moving != null) return
        frameMode = true
        dock.animate().alpha(0f).translationY(scene.dp(40).toFloat()).setDuration(600).start()
        photoMenu.animate().alpha(0f).setDuration(600).start()
        layoutOverlay()
    }

    private fun exitFrameMode() {
        frameMode = false
        dock.animate().alpha(1f).translationY(0f).setDuration(250).start()
        photoMenu.animate().alpha(1f).setDuration(250).start()
        layoutOverlay()
        main.removeCallbacks(hideDock)
        main.postDelayed(hideDock, 45_000L)
        if (currentFocus == null) row.getChildAt(0)?.requestFocus()
    }
}
