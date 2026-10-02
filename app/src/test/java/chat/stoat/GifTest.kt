package chat.stoat

import chat.stoat.util.AnimatedGifEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class GifTest {
    @Test
    fun testAnimatedGifEncoderFullPipeline() {
        val w = 32
        val h = 32
        val out = ByteArrayOutputStream()

        val encoder = AnimatedGifEncoder()
        assertTrue("Encoder should start", encoder.start(out))
        encoder.setRepeat(0)
        encoder.setDelay(100)
        encoder.setSample(10)

        // Frame 1: All RED
        val redFrame = IntArray(w * h) { 0xFFFF0000.toInt() }
        assertTrue("Add frame 1 should succeed", encoder.addFramePixels(w, h, redFrame))

        // Frame 2: All GREEN
        val greenFrame = IntArray(w * h) { 0xFF00FF00.toInt() }
        assertTrue("Add frame 2 should succeed", encoder.addFramePixels(w, h, greenFrame))

        // Frame 3: All BLUE
        val blueFrame = IntArray(w * h) { 0xFF0000FF.toInt() }
        assertTrue("Add frame 3 should succeed", encoder.addFramePixels(w, h, blueFrame))

        assertTrue("Encoder finish should succeed", encoder.finish())

        val gifBytes = out.toByteArray()
        println("Generated animated GIF total bytes: ${gifBytes.size}")
        assertTrue("GIF should have content", gifBytes.size > 100)

        // Decode using ImageIO GIF reader
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = ImageIO.createImageInputStream(ByteArrayInputStream(gifBytes))
        val count = reader.getNumImages(true)
        println("Total frames decoded by standard GIF reader: $count")
        assertEquals(3, count)

        val f1 = reader.read(0)
        val f2 = reader.read(1)
        val f3 = reader.read(2)

        println("Frame 1 color: 0x${Integer.toHexString(f1.getRGB(16, 16))}")
        println("Frame 2 color: 0x${Integer.toHexString(f2.getRGB(16, 16))}")
        println("Frame 3 color: 0x${Integer.toHexString(f3.getRGB(16, 16))}")

        val c1 = f1.getRGB(16, 16)
        val c2 = f2.getRGB(16, 16)
        val c3 = f3.getRGB(16, 16)

        // Check Red frame
        val r1 = (c1 shr 16) and 0xFF
        val g1 = (c1 shr 8) and 0xFF
        val b1 = c1 and 0xFF
        assertTrue("Frame 1 should be predominantly RED (r=$r1, g=$g1, b=$b1)", r1 > 200 && g1 < 50 && b1 < 50)

        // Check Green frame
        val r2 = (c2 shr 16) and 0xFF
        val g2 = (c2 shr 8) and 0xFF
        val b2 = c2 and 0xFF
        assertTrue("Frame 2 should be predominantly GREEN (r=$r2, g=$g2, b=$b2)", g2 > 200 && r2 < 50 && b2 < 50)

        // Check Blue frame
        val r3 = (c3 shr 16) and 0xFF
        val g3 = (c3 shr 8) and 0xFF
        val b3 = c3 and 0xFF
        assertTrue("Frame 3 should be predominantly BLUE (r=$r3, g=$g3, b=$b3)", b3 > 200 && r3 < 50 && g3 < 50)
    }
}
