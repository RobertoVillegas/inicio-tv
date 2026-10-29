package dev.robertovillegas.inicio

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/**
 * Adds photos from a phone: shows a QR code with a link to the TV itself. While this screen is open,
 * Inicio serves that link on the home network; leaving it shuts the server down. The link carries a
 * random key so nobody else on the network can upload.
 */
class UploadActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val key = (1..8).map { "abcdefghijkmnpqrstuvwxyz23456789"[Random.nextInt(32)] }.joinToString("")
    private var server: ServerSocket? = null
    private var received = 0
    private lateinit var status: TextView
    private lateinit var lastPhoto: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val link = "http://${localIp()}:$PORT/$key"

        val root = FrameLayout(this).apply { setBackgroundColor(0xFF0B0F14.toInt()) }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val qr = ImageView(this).apply {
            setImageBitmap(Qr.bitmap(link, 8))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        row.addView(qr, LinearLayout.LayoutParams(dp(260), dp(260)))

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(text("Agrega tus fotos", 34, "sans-serif-light"))
        column.addView(text(
            "1.  Abre la cámara del celular y apunta al código.\n" +
                "2.  Toca el link y elige las fotos.\n" +
                "3.  Aparecen en el inicio y en el protector de pantalla.",
            17, "sans-serif",
        ).apply { setLineSpacing(0f, 1.4f); alpha = 0.9f }, topMargin(18))
        column.addView(text("El celular tiene que estar en el mismo Wi-Fi que la TV.\n$link", 13, "sans-serif")
            .apply { alpha = 0.55f }, topMargin(14))

        val footer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        lastPhoto = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x22FFFFFF) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            visibility = View.GONE
        }
        status = text("Esperando fotos…", 17, "sans-serif-medium")
        footer.addView(lastPhoto, LinearLayout.LayoutParams(dp(96), dp(54)).apply { marginEnd = dp(14) })
        footer.addView(status)
        column.addView(footer, topMargin(28))

        row.addView(column, LinearLayout.LayoutParams(dp(520), -2).apply { marginStart = dp(56) })
        root.addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        val s = try {
            ServerSocket(PORT)
        } catch (e: Exception) {
            status.text = "No se pudo abrir el puerto $PORT: ${e.message}"
            return
        }
        server = s
        Thread { serve(s) }.start()
    }

    override fun onStop() {
        super.onStop()
        server?.close()
        server = null
    }

    private fun serve(s: ServerSocket) {
        while (!s.isClosed) {
            val conn = try { s.accept() } catch (e: Exception) { break }
            try {
                conn.soTimeout = 30_000
                conn.use { respond(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Request failed", e)
            }
        }
    }

    private fun respond(c: Socket) {
        val input = c.getInputStream()
        val head = readHead(input) ?: return
        val (method, path) = head.lineSequence().first().split(" ").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        val length = Regex("(?im)^content-length:\\s*(\\d+)").find(head)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val out = c.getOutputStream()
        fun send(code: String, type: String, body: ByteArray) {
            out.write("HTTP/1.1 $code\r\nContent-Type: $type\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
        }
        when {
            method == "GET" && path == "/$key" -> send("200 OK", "text/html; charset=utf-8", PAGE.toByteArray())
            method == "POST" && path == "/$key/upload" && length in 1..MAX_BYTES -> {
                Log.i(TAG, "Upload of $length bytes, " + Regex("(?im)^(content-type|user-agent):.*$").findAll(head).joinToString(" | ") { it.value.trim() })
                val tmp = File(cacheDir, "upload.tmp")
                tmp.outputStream().use { copy(input, it, length) }
                val ok = try { Photos.import(this, tmp) } catch (e: Exception) { Log.w(TAG, "Import failed", e); false }
                tmp.delete()
                if (ok) main.post { onReceived() }
                send(if (ok) "200 OK" else "415 Unsupported Media Type", "application/json", "{\"ok\":$ok}".toByteArray())
            }
            else -> send("404 Not Found", "text/plain", "Not found".toByteArray())
        }
    }

    private fun onReceived() {
        received++
        status.text = if (received == 1) "1 foto recibida" else "$received fotos recibidas"
        Photos.list(this).filter { it.own }.maxByOrNull { it.file.name }?.let { photo ->
            lastPhoto.setImageBitmap(BitmapFactory.decodeFile(photo.file.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
            lastPhoto.visibility = View.VISIBLE
        }
    }

    // Reads up to the blank line one byte at a time, so it doesn't swallow the start of the body.
    private fun readHead(input: InputStream): String? {
        val b = StringBuilder()
        while (!b.endsWith("\r\n\r\n")) {
            val x = input.read()
            if (x < 0 || b.length > 16_384) return null
            b.append(x.toChar())
        }
        return b.toString()
    }

    private fun copy(input: InputStream, out: OutputStream, length: Long) {
        val buf = ByteArray(64 * 1024)
        var left = length
        while (left > 0) {
            val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) error("Connection closed")
            out.write(buf, 0, n)
            left -= n
        }
    }

    private fun localIp(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress ?: "0.0.0.0"

    private fun text(t: String, sp: Int, family: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        typeface = Typeface.create(family, Typeface.NORMAL)
        setTextColor(Color.WHITE)
    }

    private fun topMargin(v: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(v) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "Upload"
        const val PORT = 8642
        const val MAX_BYTES = 60L * 1024 * 1024

        // The page the phone opens. Uploads the photos one at a time, as they are.
        val PAGE = """
            <!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Inicio · Agregar fotos</title>
            <style>
              :root{color-scheme:light dark;--bg:#f4f4f2;--fg:#15181c;--muted:#6b7075;--button:#15181c;--on-button:#fff}
              @media (prefers-color-scheme:dark){:root{--bg:#0b0f14;--fg:#f2f2f0;--muted:#9aa0a6;--button:#f2f2f0;--on-button:#0b0f14}}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--bg);color:var(--fg);font:17px/1.5 -apple-system,system-ui,sans-serif}
              main{width:100%;max-width:420px;padding:32px 20px;box-sizing:border-box}
              h1{font-weight:600;font-size:28px;margin:0 0 8px}
              p{color:var(--muted);margin:0 0 28px}
              label{display:block;text-align:center;padding:16px;border-radius:14px;background:var(--button);color:var(--on-button);font-weight:600;cursor:pointer}
              input{display:none}
              #status{margin-top:20px;min-height:1.5em;color:var(--muted);text-align:center}
            </style></head><body><main>
            <h1>Agregar fotos a la TV</h1>
            <p>Elige una o varias. Las horizontales llenan la pantalla; las verticales se ven completas sobre un fondo difuminado.</p>
            <label>Elegir fotos<input id="photos" type="file" accept="image/*" multiple></label>
            <div id="status"></div>
            <script>
              const status=document.getElementById('status');
              document.getElementById('photos').addEventListener('change',async e=>{
                const photos=[...e.target.files];let ok=0;
                for(let i=0;i<photos.length;i++){
                  status.textContent='Subiendo '+(i+1)+' de '+photos.length+'…';
                  try{const r=await fetch(location.pathname+'/upload',{method:'POST',body:photos[i]});if((await r.json()).ok)ok++}catch(_){}
                }
                const failed=photos.length-ok;
                status.textContent=(ok===1?'1 foto agregada':ok+' fotos agregadas')+(failed?' · '+failed+' no se pudieron abrir':'')+'.';
                e.target.value='';
              });
            </script></main></body></html>
        """.trimIndent()
    }
}
