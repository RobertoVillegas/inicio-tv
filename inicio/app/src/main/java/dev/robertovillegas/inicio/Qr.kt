package dev.robertovillegas.inicio

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * A minimal QR encoder for the upload link: byte mode, error correction L, versions 1 to 5 (up to 106
 * characters, all single-block). Follows Project Nayuki's algorithm.
 */
object Qr {
    // Per version: data and error-correction codewords (level L, one block).
    private val DATA_CODEWORDS = intArrayOf(0, 19, 34, 55, 80, 108)
    private val ECC_CODEWORDS = intArrayOf(0, 7, 10, 15, 20, 26)

    fun bitmap(text: String, scale: Int): Bitmap {
        val m = matrix(text.toByteArray(Charsets.UTF_8))
        val border = 4
        val side = (m.size + 2 * border) * scale
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        for (y in m.indices) for (x in m.indices) if (m[y][x]) {
            for (dy in 0 until scale) for (dx in 0 until scale) {
                bmp.setPixel((x + border) * scale + dx, (y + border) * scale + dy, Color.BLACK)
            }
        }
        return bmp
    }

    private fun matrix(data: ByteArray): Array<BooleanArray> {
        val version = (1..5).firstOrNull { 4 + 8 + data.size * 8 <= DATA_CODEWORDS[it] * 8 }
            ?: error("Text too long for the QR code")
        val n = 17 + 4 * version
        val modules = Array(n) { BooleanArray(n) }
        val isFunction = Array(n) { BooleanArray(n) }
        fun set(x: Int, y: Int, dark: Boolean) { modules[y][x] = dark; isFunction[y][x] = true }

        // Fixed patterns: timing, finders, alignment and the reserved format area.
        for (i in 0 until n) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
        for ((cx, cy) in listOf(3 to 3, n - 4 to 3, 3 to n - 4)) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = cx + dx; val y = cy + dy
                val d = maxOf(abs(dx), abs(dy))
                if (x in 0 until n && y in 0 until n) set(x, y, d != 2 && d != 4)
            }
        }
        if (version >= 2) {
            val c = n - 7
            for (dy in -2..2) for (dx in -2..2) set(c + dx, c + dy, maxOf(abs(dx), abs(dy)) != 1)
        }
        drawFormat(n, 0, ::set)

        // Data: byte mode, length, bytes, terminator and padding.
        val bits = ArrayList<Boolean>()
        fun append(value: Int, length: Int) { for (i in length - 1 downTo 0) bits += (value ushr i) and 1 == 1 }
        append(0b0100, 4)
        append(data.size, 8)
        data.forEach { append(it.toInt() and 0xFF, 8) }
        val capacity = DATA_CODEWORDS[version] * 8
        append(0, minOf(4, capacity - bits.size))
        append(0, (8 - bits.size % 8) % 8)
        var pad = 0xEC
        while (bits.size < capacity) { append(pad, 8); pad = pad xor 0xEC xor 0x11 }
        val codewords = IntArray(DATA_CODEWORDS[version]) { i -> (0 until 8).fold(0) { acc, b -> (acc shl 1) or if (bits[i * 8 + b]) 1 else 0 } }
        val all = codewords + remainder(codewords, divisor(ECC_CODEWORDS[version]))

        // Two-column zigzag, bottom to top and back, skipping column 6.
        var i = 0
        var right = n - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until n) for (j in 0..1) {
                val x = right - j
                val upward = (right + 1) and 2 == 0
                val y = if (upward) n - 1 - vert else vert
                if (!isFunction[y][x] && i < all.size * 8) {
                    modules[y][x] = (all[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                    i++
                }
            }
            right -= 2
        }
        // Mask 0: flips the data modules where (x + y) is even.
        for (y in 0 until n) for (x in 0 until n) if (!isFunction[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
        drawFormat(n, 0, ::set)
        return modules
    }

    private fun drawFormat(n: Int, mask: Int, set: (Int, Int, Boolean) -> Unit) {
        val data = (1 shl 3) or mask  // level L = 01
        var r = data
        repeat(10) { r = (r shl 1) xor ((r ushr 9) * 0x537) }
        val bits = ((data shl 10) or r) xor 0x5412
        fun bit(i: Int) = (bits ushr i) and 1 == 1
        for (i in 0..5) set(8, i, bit(i))
        set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
        for (i in 9..14) set(14 - i, 8, bit(i))
        for (i in 0..7) set(n - 1 - i, 8, bit(i))
        for (i in 8..14) set(8, n - 15 + i, bit(i))
        set(8, n - 8, true)
    }

    // Reed-Solomon over GF(256) with the polynomial 0x11D.
    private fun multiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor ((z ushr 7) * 0x11D)
            z = z xor (((y ushr i) and 1) * x)
        }
        return z
    }

    private fun divisor(degree: Int): IntArray {
        val r = IntArray(degree); r[degree - 1] = 1
        var root = 1
        repeat(degree) {
            for (j in 0 until degree) {
                r[j] = multiply(r[j], root)
                if (j + 1 < degree) r[j] = r[j] xor r[j + 1]
            }
            root = multiply(root, 0x02)
        }
        return r
    }

    private fun remainder(data: IntArray, div: IntArray): IntArray {
        val r = IntArray(div.size)
        for (b in data) {
            val factor = b xor r[0]
            System.arraycopy(r, 1, r, 0, r.size - 1)
            r[r.size - 1] = 0
            for (i in r.indices) r[i] = r[i] xor multiply(div[i], factor)
        }
        return r
    }
}
