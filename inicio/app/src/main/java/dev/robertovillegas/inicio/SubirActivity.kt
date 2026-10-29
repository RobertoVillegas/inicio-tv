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
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/**
 * Pantalla para agregar fotos desde el celular: muestra un QR con un link a la propia TV.
 * Mientras está abierta, Inicio atiende ese link en la red de la casa; al salir se apaga.
 * El link lleva una clave al azar para que nadie más en la red pueda subir fotos.
 */
class SubirActivity : Activity() {
    private val principal = Handler(Looper.getMainLooper())
    private val clave = (1..8).map { "abcdefghijkmnpqrstuvwxyz23456789"[Random.nextInt(32)] }.joinToString("")
    private var servidor: ServerSocket? = null
    private var recibidas = 0
    private lateinit var estado: TextView
    private lateinit var ultima: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ip = ipLocal()
        val link = "http://$ip:$PUERTO/$clave"

        val raiz = FrameLayout(this).apply { setBackgroundColor(0xFF0B0F14.toInt()) }
        val fila = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val qr = ImageView(this).apply {
            setImageBitmap(Qr.bitmap(link, 8))
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(Color.WHITE) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        fila.addView(qr, LinearLayout.LayoutParams(dp(260), dp(260)))

        val textos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(texto("Agrega tus fotos", 34, "sans-serif-light"))
        textos.addView(texto(
            "1.  Abre la cámara del celular y apunta al código.\n" +
                "2.  Toca el link y elige las fotos.\n" +
                "3.  Aparecen en el inicio y en el protector de pantalla.",
            17, "sans-serif",
        ).apply { setLineSpacing(0f, 1.4f); alpha = 0.9f }, margenArriba(18))
        textos.addView(texto("El celular tiene que estar en el mismo Wi-Fi que la TV.\n$link", 13, "sans-serif")
            .apply { alpha = 0.55f }, margenArriba(14))

        val pie = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        ultima = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(0x22FFFFFF) }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            visibility = View.GONE
        }
        estado = texto("Esperando fotos…", 17, "sans-serif-medium")
        pie.addView(ultima, LinearLayout.LayoutParams(dp(96), dp(54)).apply { marginEnd = dp(14) })
        pie.addView(estado)
        textos.addView(pie, margenArriba(28))

        fila.addView(textos, LinearLayout.LayoutParams(dp(520), -2).apply { marginStart = dp(56) })
        raiz.addView(fila, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        setContentView(raiz)
    }

    override fun onStart() {
        super.onStart()
        val s = try {
            ServerSocket(PUERTO)
        } catch (e: Exception) {
            estado.text = "No se pudo abrir el puerto $PUERTO: ${e.message}"
            return
        }
        servidor = s
        Thread { atender(s) }.start()
    }

    override fun onStop() {
        super.onStop()
        servidor?.close()
        servidor = null
    }

    private fun atender(s: ServerSocket) {
        while (!s.isClosed) {
            val conexion = try { s.accept() } catch (e: Exception) { break }
            try {
                conexion.soTimeout = 30_000
                conexion.use { responder(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Petición fallida", e)
            }
        }
    }

    private fun responder(c: Socket) {
        val entrada = c.getInputStream()
        val cabecera = leerCabecera(entrada) ?: return
        val (metodo, ruta) = cabecera.lineSequence().first().split(" ").let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
        val largo = Regex("(?im)^content-length:\\s*(\\d+)").find(cabecera)?.groupValues?.get(1)?.toLongOrNull() ?: 0
        val salida = c.getOutputStream()
        fun enviar(codigo: String, tipo: String, cuerpo: ByteArray) {
            salida.write("HTTP/1.1 $codigo\r\nContent-Type: $tipo\r\nContent-Length: ${cuerpo.size}\r\nConnection: close\r\n\r\n".toByteArray())
            salida.write(cuerpo)
            salida.flush()
        }
        when {
            metodo == "GET" && ruta == "/$clave" -> enviar("200 OK", "text/html; charset=utf-8", PAGINA.toByteArray())
            metodo == "POST" && ruta == "/$clave/subir" && largo in 1..MAXIMO -> {
                val tmp = File(cacheDir, "subida.tmp")
                tmp.outputStream().use { copiar(entrada, it, largo) }
                val bien = try { Fondos.importar(this, tmp) } catch (e: Exception) { Log.w(TAG, "No se pudo importar", e); false }
                tmp.delete()
                if (bien) principal.post { recibida() }
                enviar(if (bien) "200 OK" else "415 Unsupported Media Type", "application/json", "{\"ok\":$bien}".toByteArray())
            }
            else -> enviar("404 Not Found", "text/plain", "No encontrado".toByteArray())
        }
    }

    private fun recibida() {
        recibidas++
        estado.text = if (recibidas == 1) "1 foto recibida" else "$recibidas fotos recibidas"
        Fondos.listar(this).filter { it.propia }.maxByOrNull { it.archivo.name }?.let { foto ->
            ultima.setImageBitmap(BitmapFactory.decodeFile(foto.archivo.path, BitmapFactory.Options().apply { inSampleSize = 8 }))
            ultima.visibility = View.VISIBLE
        }
    }

    // Lee hasta la línea en blanco byte por byte, para no comerse el inicio del cuerpo.
    private fun leerCabecera(entrada: InputStream): String? {
        val b = StringBuilder()
        while (!b.endsWith("\r\n\r\n")) {
            val x = entrada.read()
            if (x < 0 || b.length > 16_384) return null
            b.append(x.toChar())
        }
        return b.toString()
    }

    private fun copiar(entrada: InputStream, salida: java.io.OutputStream, largo: Long) {
        val buf = ByteArray(64 * 1024)
        var falta = largo
        while (falta > 0) {
            val n = entrada.read(buf, 0, minOf(buf.size.toLong(), falta).toInt())
            if (n < 0) error("Conexión cortada")
            salida.write(buf, 0, n)
            falta -= n
        }
    }

    private fun ipLocal(): String = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress ?: "0.0.0.0"

    private fun texto(t: String, sp: Int, familia: String) = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp.toFloat())
        typeface = Typeface.create(familia, Typeface.NORMAL)
        setTextColor(Color.WHITE)
    }

    private fun margenArriba(v: Int) = LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(v) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "Subir"
        const val PUERTO = 8642
        const val MAXIMO = 60L * 1024 * 1024

        // La página que abre el celular. Sube las fotos una por una, tal cual.
        val PAGINA = """
            <!doctype html><html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Inicio · Agregar fotos</title>
            <style>
              :root{color-scheme:light dark;--fondo:#f4f4f2;--texto:#15181c;--suave:#6b7075;--boton:#15181c;--sobre:#fff}
              @media (prefers-color-scheme:dark){:root{--fondo:#0b0f14;--texto:#f2f2f0;--suave:#9aa0a6;--boton:#f2f2f0;--sobre:#0b0f14}}
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:var(--fondo);color:var(--texto);font:17px/1.5 -apple-system,system-ui,sans-serif}
              main{width:100%;max-width:420px;padding:32px 20px;box-sizing:border-box}
              h1{font-weight:600;font-size:28px;margin:0 0 8px}
              p{color:var(--suave);margin:0 0 28px}
              label{display:block;text-align:center;padding:16px;border-radius:14px;background:var(--boton);color:var(--sobre);font-weight:600;cursor:pointer}
              input{display:none}
              #estado{margin-top:20px;min-height:1.5em;color:var(--suave);text-align:center}
            </style></head><body><main>
            <h1>Agregar fotos a la TV</h1>
            <p>Elige una o varias. Las horizontales llenan la pantalla; las verticales se ven completas sobre un fondo difuminado.</p>
            <label>Elegir fotos<input id="fotos" type="file" accept="image/*" multiple></label>
            <div id="estado"></div>
            <script>
              const estado=document.getElementById('estado');
              document.getElementById('fotos').addEventListener('change',async e=>{
                const fotos=[...e.target.files];let bien=0;
                for(let i=0;i<fotos.length;i++){
                  estado.textContent='Subiendo '+(i+1)+' de '+fotos.length+'…';
                  try{const r=await fetch(location.pathname+'/subir',{method:'POST',body:fotos[i]});if((await r.json()).ok)bien++}catch(_){}
                }
                const fallaron=fotos.length-bien;
                estado.textContent=(bien===1?'1 foto agregada':bien+' fotos agregadas')+(fallaron?' · '+fallaron+' no se pudieron abrir':'')+'.';
                e.target.value='';
              });
            </script></main></body></html>
        """.trimIndent()
    }
}
