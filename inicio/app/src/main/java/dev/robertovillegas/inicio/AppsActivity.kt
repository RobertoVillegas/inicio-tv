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
    private lateinit var scene: Scene

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)

        // The home screen's landscape behind, dimmed, so it feels like the same screen.
        scene = Scene(this)
        scene.info.visibility = View.GONE
        scene.credit.visibility = View.GONE
        root.addView(scene, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { setBackgroundColor(0xD90B0F14.toInt()) }, FrameLayout.LayoutParams(-1, -1))

        // Columns sized from the real width so no tile gets cut off.
        val columns = 5
        val margin = 72
        val gap = 28
        val width = (resources.configuration.screenWidthDp - 2 * margin - (columns - 1) * gap) / columns
        val height = width * 9 / 16

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(margin), dp(44), dp(margin), dp(56))
        }
        content.addView(TextView(this).apply {
            text = "Todas las apps"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        val grid = GridLayout(this).apply {
            columnCount = columns
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(28), 0, 0)
        }
        Apps.all(this).forEachIndexed { i, app ->
            val t = Tile(this, app, width, height)
            t.onLongPress = { menu(app) }
            grid.addView(t, GridLayout.LayoutParams().apply {
                this.width = dp(width)
                setMargins(0, 0, if (i % columns == columns - 1) 0 else dp(gap), dp(44))
            })
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
        grid.getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        scene.start(180_000L)
    }

    override fun onPause() {
        super.onPause()
        scene.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        scene.release()
    }

    private fun menu(app: App) {
        val pkg = app.packageName ?: return
        val docked = Dock.contains(this, pkg)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(app.name)
            .setItems(arrayOf(if (docked) "Quitar del inicio" else "Agregar al inicio", "Cancelar")) { _, i ->
                if (i == 0) if (docked) Dock.remove(this, pkg) else Dock.add(this, pkg)
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
