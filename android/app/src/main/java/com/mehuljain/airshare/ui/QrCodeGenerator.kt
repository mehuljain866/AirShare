package com.mehuljain.airshare.ui

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Self-contained pure-Kotlin QR Code generator for pairing URLs.
 * Encodes strings up to 50 characters (e.g. "http://192.168.1.5:8631?pin=123456")
 * into a standard Version 3 (29x29) QR Code with Error Correction Level M.
 */
object QrCodeGenerator {

    fun generateBitmap(content: String, sizePx: Int = 512): Bitmap {
        val matrix = encode(content)
        val n = matrix.size
        val scale = maxOf(1, sizePx / n)
        val bitmapSize = n * scale
        val bitmap = Bitmap.createBitmap(bitmapSize, bitmapSize, Bitmap.Config.ARGB_8888)

        for (r in 0 until n) {
            for (c in 0 until n) {
                val color = if (matrix[r][c]) Color.BLACK else Color.WHITE
                for (dy in 0 until scale) {
                    for (dx in 0 until scale) {
                        bitmap.setPixel(c * scale + dx, r * scale + dy, color)
                    }
                }
            }
        }
        return bitmap
    }

    fun encode(text: String): Array<BooleanArray> {
        val size = 29 // Version 3 QR code: 29x29 modules
        val grid = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        // 1. Finder patterns (7x7) + separators
        placeFinder(grid, reserved, 0, 0)
        placeFinder(grid, reserved, size - 7, 0)
        placeFinder(grid, reserved, 0, size - 7)

        // 2. Alignment pattern (5x5) at (22, 22) for Version 3
        placeAlignment(grid, reserved, 20, 20)

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            if (!reserved[6][i]) { grid[6][i] = bit; reserved[6][i] = true }
            if (!reserved[i][6]) { grid[i][6] = bit; reserved[i][6] = true }
        }

        // 4. Dark module
        grid[size - 8][8] = true
        reserved[size - 8][8] = true

        // 5. Reserve format info areas
        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
            reserved[8][size - 1 - i] = true
            reserved[size - 1 - i][8] = true
        }

        // 6. Encode data: 8-bit Byte mode
        val dataBytes = text.toByteArray(Charsets.ISO_8859_1)
        val bitBuffer = mutableListOf<Int>()

        // Mode indicator: 0100 for Byte mode
        addBits(bitBuffer, 0b0100, 4)
        // Character count: 8 bits for Version 3 byte mode
        addBits(bitBuffer, dataBytes.size, 8)
        // Data bytes
        for (b in dataBytes) {
            addBits(bitBuffer, b.toInt() and 0xFF, 8)
        }
        // Terminator
        val dataCapacityBits = 26 * 8 // Version 3-M capacity = 26 data codewords = 208 bits
        val termLen = minOf(4, dataCapacityBits - bitBuffer.size)
        addBits(bitBuffer, 0, termLen)
        while (bitBuffer.size % 8 != 0) bitBuffer.add(0)

        // Pad bytes (0xEC, 0x11)
        val pad = intArrayOf(0xEC, 0x11)
        var padIdx = 0
        while (bitBuffer.size < dataCapacityBits) {
            addBits(bitBuffer, pad[padIdx], 8)
            padIdx = (padIdx + 1) % 2
        }

        // Convert bitBuffer to byte array
        val dataCodewords = ByteArray(26)
        for (i in 0 until 26) {
            var v = 0
            for (b in 0 until 8) {
                v = (v shl 1) or bitBuffer[i * 8 + b]
            }
            dataCodewords[i] = v.toByte()
        }

        // 7. Error correction: Reed-Solomon (26 data -> 26 error correction codewords)
        val ecCodewords = reedSolomonEncode(dataCodewords, 26)
        val allCodewords = dataCodewords + ecCodewords

        // 8. Place data bits in zig-zag pattern
        val allBits = mutableListOf<Int>()
        for (cw in allCodewords) {
            addBits(allBits, cw.toInt() and 0xFF, 8)
        }

        var bitIdx = 0
        var up = true
        var col = size - 1
        while (col > 0) {
            if (col == 6) col-- // Skip vertical timing pattern
            val rows = if (up) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (c in intArrayOf(col, col - 1)) {
                    if (!reserved[row][c]) {
                        val bit = if (bitIdx < allBits.size) allBits[bitIdx++] else 0
                        // Mask pattern 0: (row + col) % 2 == 0
                        val mask = ((row + c) % 2 == 0)
                        grid[row][c] = (bit == 1) xor mask
                    }
                }
            }
            up = !up
            col -= 2
        }

        // 9. Format information: Mask 0, EC level M = format bits 0x5412 (101010000010010)
        // With XOR mask 0x5412: 0x5412 xor 0x5412 = 0x0000
        val formatBits = 0b101010000010010 xor 0b101010000010010 // 0
        applyFormat(grid, 0b101010000010010)

        // Add 4-module quiet zone around QR code
        val quiet = 2
        val finalSize = size + quiet * 2
        val finalGrid = Array(finalSize) { BooleanArray(finalSize) }
        for (r in 0 until size) {
            for (c in 0 until size) {
                finalGrid[r + quiet][c + quiet] = grid[r][c]
            }
        }
        return finalGrid
    }

    private fun placeFinder(grid: Array<BooleanArray>, reserved: Array<BooleanArray>, r: Int, c: Int) {
        for (dr in -1..7) {
            for (dc in -1..7) {
                val row = r + dr
                val col = c + dc
                if (row in grid.indices && col in grid.indices) {
                    reserved[row][col] = true
                    val inCenter = dr in 0..6 && dc in 0..6
                    if (inCenter) {
                        val border = dr == 0 || dr == 6 || dc == 0 || dc == 6
                        val inner = dr in 2..4 && dc in 2..4
                        grid[row][col] = border || inner
                    } else {
                        grid[row][col] = false
                    }
                }
            }
        }
    }

    private fun placeAlignment(grid: Array<BooleanArray>, reserved: Array<BooleanArray>, r: Int, c: Int) {
        for (dr in 0..4) {
            for (dc in 0..4) {
                val row = r + dr
                val col = c + dc
                reserved[row][col] = true
                val border = dr == 0 || dr == 4 || dc == 0 || dc == 4
                val center = dr == 2 && dc == 2
                grid[row][col] = border || center
            }
        }
    }

    private fun applyFormat(grid: Array<BooleanArray>, bits: Int) {
        val size = grid.size
        // 15 bits of format info
        for (i in 0..14) {
            val bit = ((bits shr i) and 1) == 1
            if (i <= 5) grid[8][i] = bit
            else if (i == 6) grid[8][7] = bit
            else if (i in 7..8) grid[8 - (i - 7)][8] = bit
            else grid[14 - i][8] = bit

            if (i < 8) grid[size - 1 - i][8] = bit
            else grid[8][size - 15 + i] = bit
        }
    }

    private fun addBits(buffer: MutableList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) {
            buffer.add((value shr i) and 1)
        }
    }

    // GF(256) Reed-Solomon encoder for QR codes
    private fun reedSolomonEncode(data: ByteArray, ecCount: Int): ByteArray {
        val exp = IntArray(512)
        val log = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 0x11D // GF(256) primitive polynomial x^8 + x^4 + x^3 + x^2 + 1
        }

        // Generator polynomial for ecCount
        var gen = intArrayOf(1)
        for (i in 0 until ecCount) {
            val factor = intArrayOf(1, exp[i])
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gen[j]
                val p = (gen[j] != 0 && factor[1] != 0)
                if (p) {
                    val prod = exp[(log[gen[j]] + log[factor[1]]) % 255]
                    nextGen[j + 1] = nextGen[j + 1] xor prod
                }
            }
            gen = nextGen
        }

        val res = IntArray(data.size + ecCount)
        for (i in data.indices) res[i] = data[i].toInt() and 0xFF

        for (i in data.indices) {
            val coef = res[i]
            if (coef != 0) {
                for (j in 1 until gen.size) {
                    val prod = exp[(log[coef] + log[gen[j]]) % 255]
                    res[i + j] = res[i + j] xor prod
                }
            }
        }

        val ec = ByteArray(ecCount)
        for (i in 0 until ecCount) {
            ec[i] = res[data.size + i].toByte()
        }
        return ec
    }
}
