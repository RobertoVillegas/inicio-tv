package dev.robertovillegas.inicio

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Código QR mínimo para el link de subir fotos: modo byte, corrección L, versiones 1 a 5
 * (hasta 106 caracteres, todas de un solo bloque). Sigue el algoritmo de Project Nayuki.
 */
object Qr {
    // Por versión: codewords de datos y de corrección (nivel L, un bloque).
    private val DATOS = intArrayOf(0, 19, 34, 55, 80, 108)
    private val CORRECCION = intArrayOf(0, 7, 10, 15, 20, 26)

    fun bitmap(texto: String, escala: Int): Bitmap {
        val m = matriz(texto.toByteArray(Charsets.UTF_8))
        val borde = 4
        val lado = (m.size + 2 * borde) * escala
        val bmp = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in m.indices) for (x in m.indices) if (m[y][x]) {
            for (dy in 0 until escala) for (dx in 0 until escala) {
                bmp.setPixel((x + borde) * escala + dx, (y + borde) * escala + dy, Color.BLACK)
            }
        }
        return bmp
    }

    private fun matriz(datos: ByteArray): Array<BooleanArray> {
        val version = (1..5).firstOrNull { 4 + 8 + datos.size * 8 <= DATOS[it] * 8 }
            ?: error("Texto demasiado largo para el QR")
        val n = 17 + 4 * version
        val modulos = Array(n) { BooleanArray(n) }
        val funcion = Array(n) { BooleanArray(n) }
        fun poner(x: Int, y: Int, negro: Boolean) { modulos[y][x] = negro; funcion[y][x] = true }

        // Patrones fijos: sincronía, buscadores, alineación y lugar reservado para el formato.
        for (i in 0 until n) { poner(6, i, i % 2 == 0); poner(i, 6, i % 2 == 0) }
        for ((cx, cy) in listOf(3 to 3, n - 4 to 3, 3 to n - 4)) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx; val y = cy + dy
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                if (x in 0 until n && y in 0 until n) poner(x, y, d != 2 && d != 4)
            }
        }
        if (version >= 2) {
            val c = n - 7
            for (dy in -2..2) for (dx in -2..2) poner(c + dx, c + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
        }
        formato(n, 0, ::poner)

        // Datos: modo byte, longitud, bytes, terminador y relleno.
        val bits = ArrayList<Boolean>()
        fun agregar(valor: Int, largo: Int) { for (i in largo - 1 downTo 0) bits += (valor ushr i) and 1 == 1 }
        agregar(0b0100, 4)
        agregar(datos.size, 8)
        datos.forEach { agregar(it.toInt() and 0xFF, 8) }
        val capacidad = DATOS[version] * 8
        agregar(0, minOf(4, capacidad - bits.size))
        agregar(0, (8 - bits.size % 8) % 8)
        var relleno = 0xEC
        while (bits.size < capacidad) { agregar(relleno, 8); relleno = relleno xor 0xEC xor 0x11 }
        val codewords = IntArray(DATOS[version]) { i -> (0 until 8).fold(0) { acc, b -> (acc shl 1) or if (bits[i * 8 + b]) 1 else 0 } }
        val todo = codewords + resto(codewords, divisor(CORRECCION[version]))

        // Zigzag de dos columnas, de abajo hacia arriba y de regreso, saltando la columna 6.
        var i = 0
        var derecha = n - 1
        while (derecha >= 1) {
            if (derecha == 6) derecha = 5
            for (vert in 0 until n) for (j in 0..1) {
                val x = derecha - j
                val arriba = (derecha + 1) and 2 == 0
                val y = if (arriba) n - 1 - vert else vert
                if (!funcion[y][x] && i < todo.size * 8) {
                    modulos[y][x] = (todo[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                    i++
                }
            }
            derecha -= 2
        }
        // Máscara 0: invierte los módulos de datos donde (x + y) es par.
        for (y in 0 until n) for (x in 0 until n) if (!funcion[y][x] && (x + y) % 2 == 0) modulos[y][x] = !modulos[y][x]
        formato(n, 0, ::poner)
        return modulos
    }

    private fun formato(n: Int, mascara: Int, poner: (Int, Int, Boolean) -> Unit) {
        val dato = (1 shl 3) or mascara  // nivel L = 01
        var r = dato
        repeat(10) { r = (r shl 1) xor ((r ushr 9) * 0x537) }
        val bits = ((dato shl 10) or r) xor 0x5412
        fun bit(i: Int) = (bits ushr i) and 1 == 1
        for (i in 0..5) poner(8, i, bit(i))
        poner(8, 7, bit(6)); poner(8, 8, bit(7)); poner(7, 8, bit(8))
        for (i in 9..14) poner(14 - i, 8, bit(i))
        for (i in 0..7) poner(n - 1 - i, 8, bit(i))
        for (i in 8..14) poner(8, n - 15 + i, bit(i))
        poner(8, n - 8, true)
    }

    // Reed-Solomon sobre GF(256) con el polinomio 0x11D.
    private fun mult(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(grado: Int): IntArray {
        val r = IntArray(grado); r[grado - 1] = 1
        var raiz = 1
        repeat(grado) {
            for (j in 0 until grado) {
                r[j] = mult(r[j], raiz)
                if (j + 1 < grado) r[j] = r[j] xor r[j + 1]
            }
            raiz = mult(raiz, 0x02)
        }
        return r
    }

    private fun resto(datos: IntArray, div: IntArray): IntArray {
        val r = IntArray(div.size)
        for (b in datos) {
            val factor = b xor r[0]
            System.arraycopy(r, 1, r, 0, r.size - 1)
            r[r.size - 1] = 0
            for (i in r.indices) r[i] = r[i] xor mult(div[i], factor)
        }
        return r
    }
}
