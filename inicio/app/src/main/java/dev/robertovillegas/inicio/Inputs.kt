package dev.robertovillegas.inicio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper

/**
 * Dock tiles for the HDMI inputs with something switched on, named as in the TV's Settings > Inputs
 * ("Xbox"), or "HDMI 1", "HDMI 2"... when unnamed.
 *
 * Only inputs that Android reports as connected count. This TCL reports CONNECTED_STANDBY for an empty
 * port too, or one with a loose cable, so those hide like disconnected ones. With none left, the dock
 * shows a single Inputs tile instead.
 */
object Inputs {
    fun hdmi(ctx: Context): List<App> {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return tim.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(ctx) }
            .sortedBy { it.id }
            .mapIndexed { i, info -> i to info }
            .filter { (_, info) -> tim.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED }
            .map { (i, info) ->
                val name = info.loadCustomLabel(ctx)?.toString()?.takeIf { it.isNotBlank() } ?: "HDMI ${i + 1}"
                App(name, tile(ctx, name), open(info.id))
            }
    }

    fun watch(ctx: Context, onChange: () -> Unit): TvInputManager.TvInputCallback? {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return null
        val callback = object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) = onChange()
            override fun onInputAdded(inputId: String) = onChange()
            override fun onInputRemoved(inputId: String) = onChange()
        }
        tim.registerCallback(callback, Handler(Looper.getMainLooper()))
        return callback
    }

    fun unwatch(ctx: Context, callback: TvInputManager.TvInputCallback?) {
        callback ?: return
        ctx.getSystemService(TvInputManager::class.java)?.unregisterCallback(callback)
    }

    // TCL's TV app owns the HDMI inputs.
    private fun open(id: String) = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
        .setComponent(ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun tile(ctx: Context, name: String): Drawable {
        val d = ctx.resources.displayMetrics.density
        val w = (160 * d).toInt()
        val h = (90 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2B3440.toInt(), 0xFF141A21.toInt(), Shader.TileMode.CLAMP)
        })
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22 * d
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        while (title.measureText(name) > w - 24 * d && title.textSize > 12 * d) title.textSize -= d
        c.drawText(name, w / 2f, h / 2f - (title.descent() + title.ascent()) / 2, title)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 10 * d
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        c.drawText("ENTRADA", w / 2f, h - 14 * d, caption)
        return BitmapDrawable(ctx.resources, bmp)
    }
}
