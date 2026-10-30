package com.ridthewann.ubahvo.data

import java.io.BufferedInputStream

/**
 * Reads a bounded prefix despite short network-stream reads, then resets the stream so the
 * actual image copy still starts at byte zero.
 */
internal object ImagePrefixReader {
    fun readPrefix(input: BufferedInputStream, maxBytes: Int): ByteArray {
        require(maxBytes > 0) { "maxBytes must be positive" }
        input.mark(maxBytes)
        val header = ByteArray(maxBytes)
        var total = 0
        while (total < header.size) {
            val count = input.read(header, total, header.size - total)
            if (count < 0) break
            if (count == 0) {
                val next = input.read()
                if (next < 0) break
                header[total++] = next.toByte()
            } else {
                total += count
            }
        }
        input.reset()
        return header.copyOf(total)
    }
}
