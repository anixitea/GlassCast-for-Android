package com.glasscast.app.tv

/**
 * A QR code, made here rather than with a library: byte mode, error
 * correction level M, versions 1 to 10 (up to 213 bytes), the mask chosen by
 * the standard's penalty rules. Enough for a sign-in address.
 *
 * [modules] is the square of dark (true) and light modules, without the quiet
 * zone; [size] is its side.
 */
class QrCode private constructor(val size: Int, private val dark: BooleanArray) {

    operator fun get(x: Int, y: Int): Boolean = dark[y * size + x]

    companion object {
        /** The smallest code that holds [text] (UTF-8), or null if it's too long. */
        fun encode(text: String): QrCode? {
            val data = text.toByteArray(Charsets.UTF_8)
            val version = (1..10).firstOrNull { v -> dataCapacity(v) * 8 >= 4 + countBits(v) + data.size * 8 } ?: return null
            val codewords = interleave(version, dataCodewords(version, data))
            val builder = Builder(version)
            builder.drawFunctionPatterns()
            builder.drawCodewords(codewords)
            // The mask with the lowest penalty, as the standard asks.
            var best: Builder? = null
            var bestPenalty = Int.MAX_VALUE
            for (mask in 0 until 8) {
                val candidate = builder.copy()
                candidate.applyMask(mask)
                candidate.drawFormat(mask)
                val penalty = candidate.penalty()
                if (penalty < bestPenalty) {
                    bestPenalty = penalty
                    best = candidate
                }
            }
            val chosen = best ?: return null
            return QrCode(chosen.size, chosen.modules)
        }

        // ---- capacity (level M) ----

        /** Error correction codewords per block, level M, versions 1 to 10. */
        private val EC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16, 18, 22, 22, 26)

        /** Blocks per version, level M: group 1 count, data per block; group 2 count, data per block. */
        private val BLOCKS = arrayOf(
            intArrayOf(1, 16, 0, 0),
            intArrayOf(1, 28, 0, 0),
            intArrayOf(1, 44, 0, 0),
            intArrayOf(2, 32, 0, 0),
            intArrayOf(2, 43, 0, 0),
            intArrayOf(4, 27, 0, 0),
            intArrayOf(4, 31, 0, 0),
            intArrayOf(2, 38, 2, 39),
            intArrayOf(3, 36, 2, 37),
            intArrayOf(4, 43, 1, 44)
        )

        private val ALIGNMENT = arrayOf(
            intArrayOf(),
            intArrayOf(6, 18),
            intArrayOf(6, 22),
            intArrayOf(6, 26),
            intArrayOf(6, 30),
            intArrayOf(6, 34),
            intArrayOf(6, 22, 38),
            intArrayOf(6, 24, 42),
            intArrayOf(6, 26, 46),
            intArrayOf(6, 28, 50)
        )

        private fun dataCapacity(version: Int): Int {
            val b = BLOCKS[version - 1]
            return b[0] * b[1] + b[2] * b[3]
        }

        private fun countBits(version: Int) = if (version <= 9) 8 else 16

        // ---- data ----

        private fun dataCodewords(version: Int, data: ByteArray): IntArray {
            val capacity = dataCapacity(version)
            val bits = ArrayList<Boolean>(capacity * 8)
            fun put(value: Int, length: Int) {
                for (i in length - 1 downTo 0) bits += ((value ushr i) and 1) == 1
            }
            put(0b0100, 4) // byte mode
            put(data.size, countBits(version))
            data.forEach { put(it.toInt() and 0xFF, 8) }
            put(0, minOf(4, capacity * 8 - bits.size)) // terminator
            while (bits.size % 8 != 0) bits += false
            val out = IntArray(capacity)
            var i = 0
            while (i * 8 < bits.size) {
                var v = 0
                for (j in 0 until 8) v = (v shl 1) or (if (bits[i * 8 + j]) 1 else 0)
                out[i] = v
                i++
            }
            var pad = true
            while (i < capacity) {
                out[i++] = if (pad) 0xEC else 0x11
                pad = !pad
            }
            return out
        }

        /** Splits into blocks, adds each block's error correction, and interleaves them. */
        private fun interleave(version: Int, data: IntArray): IntArray {
            val b = BLOCKS[version - 1]
            val ecLength = EC_PER_BLOCK[version - 1]
            val blocks = ArrayList<IntArray>()
            var offset = 0
            repeat(b[0]) {
                blocks += data.copyOfRange(offset, offset + b[1])
                offset += b[1]
            }
            repeat(b[2]) {
                blocks += data.copyOfRange(offset, offset + b[3])
                offset += b[3]
            }
            val generator = generator(ecLength)
            val ecBlocks = blocks.map { remainder(it, generator) }
            val out = ArrayList<Int>()
            val longest = blocks.maxOf { it.size }
            for (i in 0 until longest) blocks.forEach { if (i < it.size) out += it[i] }
            for (i in 0 until ecLength) ecBlocks.forEach { out += it[i] }
            return out.toIntArray()
        }

        // ---- Reed-Solomon over GF(256), polynomial 0x11D ----

        private val EXP = IntArray(512)
        private val LOG = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                EXP[i] = x
                LOG[x] = i
                x = x shl 1
                if (x and 0x100 != 0) x = x xor 0x11D
            }
            for (i in 255 until 512) EXP[i] = EXP[i - 255]
        }

        private fun multiply(a: Int, b: Int) = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

        /** The generator polynomial of [degree], highest power first (leading 1 left out). */
        private fun generator(degree: Int): IntArray {
            var poly = intArrayOf(1)
            for (i in 0 until degree) {
                val next = IntArray(poly.size + 1)
                for (j in poly.indices) {
                    next[j] = next[j] xor poly[j]
                    next[j + 1] = next[j + 1] xor multiply(poly[j], EXP[i])
                }
                poly = next
            }
            return poly.copyOfRange(1, poly.size)
        }

        private fun remainder(data: IntArray, generator: IntArray): IntArray {
            val result = IntArray(generator.size)
            for (byte in data) {
                val factor = byte xor result[0]
                System.arraycopy(result, 1, result, 0, result.size - 1)
                result[result.size - 1] = 0
                for (i in generator.indices) result[i] = result[i] xor multiply(generator[i], factor)
            }
            return result
        }

        private class Builder(val version: Int) {
            val size = version * 4 + 17
            var modules = BooleanArray(size * size)
            var function = BooleanArray(size * size)

            fun copy(): Builder = Builder(version).also {
                it.modules = modules.copyOf()
                it.function = function.copyOf()
            }

            private fun set(x: Int, y: Int, dark: Boolean) {
                modules[y * size + x] = dark
                function[y * size + x] = true
            }

            fun drawFunctionPatterns() {
                for (i in 0 until size) {
                    set(6, i, i % 2 == 0)
                    set(i, 6, i % 2 == 0)
                }
                finder(3, 3)
                finder(size - 4, 3)
                finder(3, size - 4)
                val align = ALIGNMENT[version - 1]
                for (i in align.indices) for (j in align.indices) {
                    val corner = (i == 0 && j == 0) || (i == 0 && j == align.lastIndex) || (i == align.lastIndex && j == 0)
                    if (!corner) alignment(align[i], align[j])
                }
                // Reserve the format areas (drawn per mask) and the dark module.
                drawFormat(0)
                if (version >= 7) drawVersion()
            }

            private fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    set(x, y, d != 2 && d != 4)
                }
            }

            private fun alignment(cx: Int, cy: Int) {
                for (dy in -2..2) for (dx in -2..2) {
                    set(cx + dx, cy + dy, maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1)
                }
            }

            fun drawFormat(mask: Int) {
                val data = (0b00 shl 3) or mask // level M is 00
                var rem = data
                repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
                val bits = ((data shl 10) or rem) xor 0x5412
                fun bit(i: Int) = ((bits ushr i) and 1) == 1
                for (i in 0..5) set(8, i, bit(i))
                set(8, 7, bit(6))
                set(8, 8, bit(7))
                set(7, 8, bit(8))
                for (i in 9 until 15) set(14 - i, 8, bit(i))
                for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
                for (i in 8 until 15) set(8, size - 15 + i, bit(i))
                set(8, size - 8, true)
            }

            private fun drawVersion() {
                var rem = version
                repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
                val bits = (version shl 12) or rem
                for (i in 0 until 18) {
                    val dark = ((bits ushr i) and 1) == 1
                    val a = size - 11 + i % 3
                    val b = i / 3
                    set(a, b, dark)
                    set(b, a, dark)
                }
            }

            fun drawCodewords(codewords: IntArray) {
                var i = 0
                val total = codewords.size * 8
                var right = size - 1
                while (right >= 1) {
                    if (right == 6) right = 5
                    for (vert in 0 until size) {
                        for (j in 0 until 2) {
                            val x = right - j
                            val upward = ((right + 1) and 2) == 0
                            val y = if (upward) size - 1 - vert else vert
                            if (!function[y * size + x] && i < total) {
                                modules[y * size + x] = ((codewords[i ushr 3] ushr (7 - (i and 7))) and 1) == 1
                                i++
                            }
                        }
                    }
                    right -= 2
                }
            }

            fun applyMask(mask: Int) {
                for (y in 0 until size) for (x in 0 until size) {
                    if (function[y * size + x]) continue
                    val invert = when (mask) {
                        0 -> (x + y) % 2 == 0
                        1 -> y % 2 == 0
                        2 -> x % 3 == 0
                        3 -> (x + y) % 3 == 0
                        4 -> (x / 3 + y / 2) % 2 == 0
                        5 -> x * y % 2 + x * y % 3 == 0
                        6 -> (x * y % 2 + x * y % 3) % 2 == 0
                        else -> ((x + y) % 2 + x * y % 3) % 2 == 0
                    }
                    if (invert) modules[y * size + x] = !modules[y * size + x]
                }
            }

            fun penalty(): Int {
                var result = 0
                fun at(x: Int, y: Int) = modules[y * size + x]
                // Runs of five or more in a row or column.
                for (y in 0 until size) {
                    var run = 1
                    for (x in 1 until size) {
                        if (at(x, y) == at(x - 1, y)) {
                            run++
                            if (run == 5) result += 3 else if (run > 5) result++
                        } else {
                            run = 1
                        }
                    }
                }
                for (x in 0 until size) {
                    var run = 1
                    for (y in 1 until size) {
                        if (at(x, y) == at(x, y - 1)) {
                            run++
                            if (run == 5) result += 3 else if (run > 5) result++
                        } else {
                            run = 1
                        }
                    }
                }
                // Two-by-two blocks of one color.
                for (y in 0 until size - 1) for (x in 0 until size - 1) {
                    val c = at(x, y)
                    if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) result += 3
                }
                // Finder-like patterns.
                val pattern = booleanArrayOf(true, false, true, true, true, false, true)
                for (y in 0 until size) for (x in 0..size - 7) {
                    if ((0 until 7).all { at(x + it, y) == pattern[it] }) {
                        val before = (1..4).all { x - it < 0 || !at(x - it, y) }
                        val after = (1..4).all { x + 6 + it >= size || !at(x + 6 + it, y) }
                        if (before || after) result += 40
                    }
                }
                for (x in 0 until size) for (y in 0..size - 7) {
                    if ((0 until 7).all { at(x, y + it) == pattern[it] }) {
                        val before = (1..4).all { y - it < 0 || !at(x, y - it) }
                        val after = (1..4).all { y + 6 + it >= size || !at(x, y + 6 + it) }
                        if (before || after) result += 40
                    }
                }
                // Balance of dark and light.
                val darkCount = modules.count { it }
                val percent = darkCount * 100 / (size * size)
                result += kotlin.math.abs(percent - 50) / 5 * 10
                return result
            }
        }
    }
}
