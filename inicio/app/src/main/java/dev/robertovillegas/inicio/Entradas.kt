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

/**
 * Accesos directos a cada entrada HDMI. El nombre es el que se le ponga en
 * Ajustes > Entradas de la TV (por ejemplo "Xbox"); si no tiene, queda "HDMI 1", "HDMI 2"...
 */
object Entradas {
    fun hdmi(ctx: Context): List<App> {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return tim.tvInputList
            .filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && !it.isHidden(ctx) }
            .sortedBy { it.id }
            .mapIndexed { i, info ->
                val nombre = info.loadCustomLabel(ctx)?.toString()?.takeIf { it.isNotBlank() }
                    ?: "HDMI ${i + 1}"
                App(nombre, tarjeta(ctx, nombre), abrir(info.id))
            }
    }

    private fun abrir(id: String) = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
        .setComponent(ComponentName("com.tcl.tv", "com.tcl.player.TVActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // Una tarjeta dibujada a mano: degradado oscuro con el nombre de la entrada.
    private fun tarjeta(ctx: Context, nombre: String): Drawable {
        val d = ctx.resources.displayMetrics.density
        val w = (160 * d).toInt()
        val h = (90 * d).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF2B3440.toInt(), 0xFF141A21.toInt(), Shader.TileMode.CLAMP)
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22 * d
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        while (p.measureText(nombre) > w - 24 * d && p.textSize > 12 * d) p.textSize -= d
        c.drawText(nombre, w / 2f, h / 2f - (p.descent() + p.ascent()) / 2, p)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()
            textSize = 10 * d
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.15f
        }
        c.drawText("ENTRADA", w / 2f, h - 14 * d, sub)
        return BitmapDrawable(ctx.resources, bmp)
    }
}
