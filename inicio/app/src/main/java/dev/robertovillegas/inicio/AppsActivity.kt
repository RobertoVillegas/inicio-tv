package dev.robertovillegas.inicio

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class AppsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0101418.toInt())
            setPadding(dp(64), dp(40), dp(64), 0)
        }
        raiz.addView(TextView(this).apply {
            text = "Todas las apps"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
        })
        val rejilla = GridLayout(this).apply {
            columnCount = 5
            clipChildren = false
            clipToPadding = false
            setPadding(dp(12), dp(24), dp(12), dp(48))
        }
        Apps.todas(this).forEach { app ->
            rejilla.addView(Tarjeta(this, app, 152, 86), GridLayout.LayoutParams().apply {
                setMargins(0, 0, dp(20), dp(44))
            })
        }
        raiz.addView(ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(rejilla)
        })
        setContentView(raiz)
        rejilla.getChildAt(0)?.requestFocus()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
