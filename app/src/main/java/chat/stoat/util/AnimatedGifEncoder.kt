package chat.stoat.util

import java.io.OutputStream

/**
 * Encodes animated GIF files from a sequence of Bitmap frames.
 * Based on the classic Kevin Weiner / Anthony Dekker / J.M.G. Elliott encoder (public domain).
 * Fully spec-compliant GIF89a implementation with local color tables and Netscape looping.
 */
class AnimatedGifEncoder {
    private var width = 0
    private var height = 0
    private var transparent: Int = -1
    private var transIndex = 0
    private var repeat = 0
    private var delay = 10
    private var sampleFactor = 10
    private var started = false
    private var out: OutputStream? = null
    private var pixels: ByteArray? = null
    private var indexedPixels: ByteArray? = null
    private var colorDepth = 8
    private var colorTab: ByteArray? = null
    private var usedEntry = BooleanArray(256)
    private var palSize = 7
    private var firstFrame = true

    fun setDelay(ms: Int) { delay = ms.coerceAtLeast(20) / 10 }
    fun setRepeat(iter: Int) { repeat = iter }
    fun setTransparent(color: Int) { transparent = color }
    fun setSample(sample: Int) { sampleFactor = sample.coerceIn(1, 30) }

    fun start(os: OutputStream): Boolean {
        return try {
            out = os
            writeString("GIF89a")
            started = true
            firstFrame = true
            true
        } catch (e: Exception) { false }
    }

    fun addFrame(bitmap: android.graphics.Bitmap): Boolean {
        if (!started || bitmap.isRecycled) return false
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        return addFramePixels(w, h, pix)
    }

    fun addFramePixels(w: Int, h: Int, pix: IntArray): Boolean {
        if (!started) return false
        return try {
            if (width == 0) {
                width = w
                height = h
            }
            getImagePixels(w, h, pix)
            analyzePixels()
            if (firstFrame) {
                writeLSD()
                writePalette()
                if (repeat >= 0) writeNetscapeExt()
            }
            writeGraphicCtrlExt()
            writeImageDesc()
            if (!firstFrame) {
                writePalette()
            }
            writePixels()
            firstFrame = false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun finish(): Boolean {
        if (!started) return false
        return try {
            out!!.write(0x3B)
            out!!.flush()
            started = false
            true
        } catch (e: Exception) { false }
    }

    private fun getImagePixels(w: Int, h: Int, pix: IntArray) {
        pixels = ByteArray(pix.size * 3)
        var idx = 0
        for (p in pix) {
            // Store B, G, R to match NeuQuant color order
            pixels!![idx++] = (p and 0xFF).toByte()         // Blue
            pixels!![idx++] = ((p shr 8) and 0xFF).toByte()  // Green
            pixels!![idx++] = ((p shr 16) and 0xFF).toByte() // Red
        }
    }

    private fun analyzePixels() {
        val len = pixels!!.size
        val nPix = len / 3
        indexedPixels = ByteArray(nPix)
        val nq = NeuQuant(pixels!!, len, sampleFactor)
        colorTab = nq.process()

        // Build reverse lookup table for fast exact color matching
        val colorMap = HashMap<Int, Int>(256)
        var k = 0
        for (i in 0 until 256) {
            val r = colorTab!![k++].toInt() and 0xFF
            val g = colorTab!![k++].toInt() and 0xFF
            val b = colorTab!![k++].toInt() and 0xFF
            colorMap[(r shl 16) or (g shl 8) or b] = i
        }

        transIndex = 0
        var idx2 = 0
        var pi = 0
        usedEntry.fill(false)
        for (i in 0 until nPix) {
            val b = pixels!![pi++].toInt() and 0xFF
            val g = pixels!![pi++].toInt() and 0xFF
            val r = pixels!![pi++].toInt() and 0xFF
            val rgb = (r shl 16) or (g shl 8) or b
            val index = colorMap[rgb] ?: nq.map(b, g, r)
            usedEntry[index] = true
            indexedPixels!![idx2++] = index.toByte()
        }
        colorDepth = 8
        palSize = 7
    }

    private fun writeLSD() {
        writeShort(width)
        writeShort(height)
        out!!.write(0x80 or 0x70 or palSize) // Global Color Table Flag = 1, 8 bits/pixel, 256 colors
        out!!.write(0)
        out!!.write(0)
    }

    private fun writePalette() {
        out!!.write(colorTab!!, 0, colorTab!!.size)
        val n = (3 * 256) - colorTab!!.size
        for (i in 0 until n) out!!.write(0)
    }

    private fun writeNetscapeExt() {
        out!!.write(0x21); out!!.write(0xFF); out!!.write(11)
        writeString("NETSCAPE2.0")
        out!!.write(3); out!!.write(1)
        writeShort(repeat)
        out!!.write(0)
    }

    private fun writeGraphicCtrlExt() {
        out!!.write(0x21); out!!.write(0xF9); out!!.write(4)
        val transp = if (transparent >= 0) 1 else 0
        val disp = 2 // Disposal 2: Restore to background color between frames
        out!!.write(0 or (disp shl 2) or transp)
        writeShort(delay)
        out!!.write(if (transparent >= 0) transIndex else 0)
        out!!.write(0)
    }

    private fun writeImageDesc() {
        out!!.write(0x2C)
        writeShort(0); writeShort(0)
        writeShort(width); writeShort(height)
        if (firstFrame) {
            out!!.write(0) // Frame 0 uses Global Color Table
        } else {
            out!!.write(0x80 or palSize) // Frame 1+ uses Local Color Table
        }
    }

    private fun writePixels() {
        val encoder = LZWEncoder(width, height, indexedPixels!!, colorDepth)
        encoder.encode(out!!)
    }

    private fun writeShort(v: Int) { out!!.write(v and 0xFF); out!!.write((v shr 8) and 0xFF) }
    private fun writeString(s: String) { s.forEach { out!!.write(it.code) } }
}

// ── NeuQuant neural-net colour quantiser ──────────────────────────────────
// (c) 1994 Anthony Dekker – public domain
class NeuQuant(private val thepicture: ByteArray, private val lengthcount: Int, private val samplefac: Int) {
    private val netsize = 256; private val maxnetpos = netsize - 1
    private val netbiasshift = 4; private val ncycles = 100
    private val intbiasshift = 16; private val intbias = 1 shl intbiasshift
    private val gammashift = 10; private val gamma = 1 shl gammashift
    private val betashift = 10; private val beta = intbias shr betashift; private val betagamma = intbias shl (gammashift - betashift)
    private val initrad = netsize shr 3; private val radiusbiasshift = 6; private val radiusbias = 1 shl radiusbiasshift
    private val initradius = initrad * radiusbias; private val radiusdec = 30
    private val alphabiasshift = 10; private val initalpha = 1 shl alphabiasshift
    private val radbiasshift = 8; private val radbias = 1 shl radbiasshift; private val alpharadbshift = alphabiasshift + radbiasshift; private val alpharadbias = 1 shl alpharadbshift
    private val network = Array(netsize) { IntArray(4) }
    private val netindex = IntArray(256); private val bias = IntArray(netsize); private val freq = IntArray(netsize); private val radpower = IntArray(initrad)

    init {
        for (i in 0 until netsize) { network[i][0] = (i shl (netbiasshift + 8)) / netsize; network[i][1] = network[i][0]; network[i][2] = network[i][0]; freq[i] = intbias / netsize; bias[i] = 0 }
    }

    fun process(): ByteArray { learn(); unbiasnet(); inxbuild(); return colorMap() }

    private fun colorMap(): ByteArray {
        val map = ByteArray(3 * netsize); var k = 0
        for (i in 0 until netsize) {
            map[k++] = network[i][2].toByte() // Red
            map[k++] = network[i][1].toByte() // Green
            map[k++] = network[i][0].toByte() // Blue
        }
        return map
    }

    private fun inxbuild() {
        var previouscol = 0; var startpos = 0
        for (i in 0 until netsize) {
            val p = network[i]; var smallpos = i; var smallval = p[1]
            for (j in i + 1 until netsize) { val q = network[j]; if (q[1] < smallval) { smallpos = j; smallval = q[1] } }
            val q = network[smallpos]; if (i != smallpos) { network[smallpos] = network[i]; network[i] = q }
            if (smallval != previouscol) { netindex[previouscol] = (startpos + i) shr 1; for (j in previouscol + 1 until smallval) netindex[j] = i; previouscol = smallval; startpos = i }
        }
        netindex[previouscol] = (startpos + maxnetpos) shr 1; for (j in previouscol + 1 until 256) netindex[j] = maxnetpos
    }

    private fun learn() {
        val alphadec = 30 + ((samplefac - 1) / 3)
        val lengthcount2 = if (lengthcount < 3) 3 else lengthcount
        val samplepixels = lengthcount2 / (3 * samplefac)
        var delta = samplepixels / ncycles
        if (delta < 1) delta = 1
        var alpha = initalpha
        var radius = initradius
        var rad = radius shr radiusbiasshift
        if (rad <= 1) rad = 0
        for (i in 0 until rad) radpower[i] = alpha * (((rad * rad - i * i) * radbias) / (rad * rad))

        val step = when {
            lengthcount2 < 499 * 3 -> 3
            lengthcount2 % 499 != 0 -> 499 * 3
            lengthcount2 % 491 != 0 -> 491 * 3
            lengthcount2 % 487 != 0 -> 487 * 3
            else -> 503 * 3
        }

        var pos = 0
        var i2 = 0
        while (i2 < samplepixels) {
            val b = thepicture[pos].toInt() and 0xFF
            val g = thepicture[pos + 1].toInt() and 0xFF
            val r = thepicture[pos + 2].toInt() and 0xFF
            val j = contest(b, g, r)
            alterSingle(alpha, j, b, g, r)
            if (rad != 0) alterNeigh(rad, j, b, g, r)
            pos = (pos + step) % lengthcount2
            pos -= pos % 3
            i2++
            if (i2 % delta == 0) {
                alpha -= alpha / alphadec
                radius -= radius / radiusdec
                rad = radius shr radiusbiasshift
                if (rad <= 1) rad = 0
                for (k in 0 until rad) radpower[k] = alpha * (((rad * rad - k * k) * radbias) / (rad * rad))
            }
        }
    }

    private fun alterSingle(alpha: Int, i: Int, b: Int, g: Int, r: Int) {
        val n = network[i]
        n[0] -= alpha * (n[0] - (b shl netbiasshift)) / initalpha
        n[1] -= alpha * (n[1] - (g shl netbiasshift)) / initalpha
        n[2] -= alpha * (n[2] - (r shl netbiasshift)) / initalpha
    }

    private fun alterNeigh(rad: Int, i: Int, b: Int, g: Int, r: Int) {
        val lo = maxOf(i - rad, -1)
        val hi = minOf(i + rad, netsize)
        var j = i + 1
        var k = i - 1
        var m = 1
        val bs = b shl netbiasshift
        val gs = g shl netbiasshift
        val rs = r shl netbiasshift
        while (j < hi || k > lo) {
            val a = radpower[m++]
            if (j < hi) {
                val p = network[j++]
                p[0] -= a * (p[0] - bs) / alpharadbias
                p[1] -= a * (p[1] - gs) / alpharadbias
                p[2] -= a * (p[2] - rs) / alpharadbias
            }
            if (k > lo) {
                val p = network[k--]
                p[0] -= a * (p[0] - bs) / alpharadbias
                p[1] -= a * (p[1] - gs) / alpharadbias
                p[2] -= a * (p[2] - rs) / alpharadbias
            }
        }
    }

    private fun contest(b: Int, g: Int, r: Int): Int {
        var bestd = Int.MAX_VALUE
        var besti = -1
        val bs = b shl netbiasshift
        val gs = g shl netbiasshift
        val rs = r shl netbiasshift
        for (i in 0 until netsize) {
            val n = network[i]
            val dist = Math.abs(n[0] - bs) + Math.abs(n[1] - gs) + Math.abs(n[2] - rs)
            if (dist < bestd) {
                bestd = dist
                besti = i
            }
            val dist2 = dist - bias[i] / intbias
            if (dist2 < bestd) {
                bestd = dist2
                besti = i
            }
            freq[i] -= freq[i] / 1024
            bias[i] += freq[i] * gamma / beta
        }
        freq[besti] += initalpha
        bias[besti] -= betagamma
        return besti
    }
    private fun unbiasnet() { for (i in 0 until netsize) { network[i][0] = network[i][0] shr netbiasshift; network[i][1] = network[i][1] shr netbiasshift; network[i][2] = network[i][2] shr netbiasshift; network[i][3] = i } }
    fun map(b: Int, g: Int, r: Int): Int {
        var bestd = 1000
        var best = -1
        var i = netindex[g]
        var j = i - 1
        while (i < netsize || j >= 0) {
            if (i < netsize) {
                val p = network[i]
                val dist = Math.abs(p[1] - g)
                if (dist >= bestd) {
                    i = netsize
                } else {
                    i++
                    val dist2 = dist + Math.abs(p[0] - b) + Math.abs(p[2] - r)
                    if (dist2 < bestd) {
                        bestd = dist2
                        best = p[3]
                    }
                }
            }
            if (j >= 0) {
                val p = network[j]
                val dist = Math.abs(p[1] - g)
                if (dist >= bestd) {
                    j = -1
                } else {
                    j--
                    val dist2 = dist + Math.abs(p[0] - b) + Math.abs(p[2] - r)
                    if (dist2 < bestd) {
                        bestd = dist2
                        best = p[3]
                    }
                }
            }
        }
        return best
    }
}

// ── LZW Encoder ────────────────────────────────────────────────────────────
class LZWEncoder(private val imgW: Int, private val imgH: Int, private val pixAry: ByteArray, private val initCodeSize: Int) {
    private val EOF = -1; private val BITS = 12; private val HSIZE = 5003
    private var n_bits = 0; private var maxbits = BITS; private var maxcode = 0; private var maxmaxcode = 1 shl BITS
    private val htab = IntArray(HSIZE); private val codetab = IntArray(HSIZE)
    private var free_ent = 0; private var clear_flg = false
    private var cur_accum = 0; private var cur_bits = 0
    private val masks = intArrayOf(0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F, 0x007F, 0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF, 0x7FFF, 0xFFFF)
    private var clear_code = 0; private var eof_code = 0; private var remaining = 0; private var curPixel = 0
    private var a_count = 0; private val accum = ByteArray(256)

    fun encode(os: OutputStream) {
        os.write(initCodeSize)
        remaining = imgW * imgH; curPixel = 0
        compress(initCodeSize + 1, os)
        os.write(0)
    }

    private fun compress(intlCodeSize: Int, outs: OutputStream) {
        n_bits = intlCodeSize; maxcode = maxCode(n_bits); clear_code = 1 shl (intlCodeSize - 1); eof_code = clear_code + 1; free_ent = clear_code + 2; a_count = 0
        var ent = nextPixel()
        var hshift = 0; var fcode = HSIZE; while (fcode < 65536) { hshift++; fcode *= 2 }; hshift = 8 - hshift
        val hsize_reg = HSIZE; htab.fill(-1)
        output(clear_code, outs)
        outer@ while (true) {
            val c = nextPixel(); if (c == EOF) break
            fcode = (c shl maxbits) + ent
            var i = (c shl hshift) xor ent
            if (htab[i] == fcode) { ent = codetab[i]; continue }
            if (htab[i] >= 0) {
                val disp = if (i == 0) 1 else hsize_reg - i
                do {
                    i -= disp
                    if (i < 0) i += hsize_reg
                    if (htab[i] == fcode) {
                        ent = codetab[i]
                        continue@outer
                    }
                } while (htab[i] >= 0)
            }
            output(ent, outs); ent = c
            if (free_ent < maxmaxcode) { codetab[i] = free_ent++; htab[i] = fcode } else clearTable(outs)
        }
        output(ent, outs)
        output(eof_code, outs)
        // Flush any remaining bits in the accumulator
        if (cur_bits > 0) {
            accum[a_count++] = (cur_accum and 0xFF).toByte()
        }
        flushPacket(outs)
    }

    private fun clearTable(outs: OutputStream) { resetCodeTable(HSIZE); free_ent = clear_code + 2; clear_flg = true; output(clear_code, outs) }
    private fun resetCodeTable(hsize: Int) { htab.fill(-1) }
    private fun maxCode(n_bits: Int) = (1 shl n_bits) - 1
    private fun nextPixel(): Int { if (remaining == 0) return EOF; remaining--; return pixAry[curPixel++].toInt() and 0xFF }
    private fun output(code: Int, outs: OutputStream) {
        cur_accum = cur_accum and masks[cur_bits]; cur_accum = if (cur_bits > 0) cur_accum or (code shl cur_bits) else code; cur_bits += n_bits
        while (cur_bits >= 8) { accum[a_count++] = (cur_accum and 0xFF).toByte(); if (a_count >= 254) flushPacket(outs); cur_accum = cur_accum shr 8; cur_bits -= 8 }
        if (clear_flg) { maxcode = maxCode(n_bits.also { n_bits = initCodeSize + 1 }); clear_flg = false }
        else if (free_ent > maxcode + (if (clear_flg) 1 else 0)) { n_bits++; maxcode = if (n_bits == maxbits) maxmaxcode else maxCode(n_bits) }
    }
    private fun flushPacket(outs: OutputStream) { if (a_count > 0) { outs.write(a_count); outs.write(accum, 0, a_count); a_count = 0 } }
}
