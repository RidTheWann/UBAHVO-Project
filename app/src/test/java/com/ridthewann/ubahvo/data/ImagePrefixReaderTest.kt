package com.ridthewann.ubahvo.data

import java.io.BufferedInputStream
import java.io.InputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePrefixReaderTest {
    @Test
    fun accumulatesShortReadsAndResetsStreamToBeginning() {
        val bytes = ByteArray(64) { (it + 1).toByte() }
        val input = BufferedInputStream(ChunkedInputStream(bytes, maxChunk = 2), 8)

        assertArrayEquals(bytes.copyOf(12), ImagePrefixReader.readPrefix(input, 12))
        assertEquals(bytes[0].toInt() and 0xff, input.read())
    }

    @Test
    fun returnsAvailableBytesWhenStreamEndsBeforePrefixLimit() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val input = BufferedInputStream(ChunkedInputStream(bytes, maxChunk = 1), 4)

        assertArrayEquals(bytes, ImagePrefixReader.readPrefix(input, 512))
        val replayed = ByteArray(bytes.size) { input.read().toByte() }
        assertArrayEquals(bytes, replayed)
        assertEquals(-1, input.read())
    }

    private class ChunkedInputStream(
        private val content: ByteArray,
        private val maxChunk: Int
    ) : InputStream() {
        private var offset = 0

        override fun read(): Int {
            if (offset >= content.size) return -1
            return content[offset++].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (offset >= content.size) return -1
            val count = minOf(len, maxChunk, content.size - offset)
            content.copyInto(buffer, off, offset, offset + count)
            offset += count
            return count
        }
    }
}
