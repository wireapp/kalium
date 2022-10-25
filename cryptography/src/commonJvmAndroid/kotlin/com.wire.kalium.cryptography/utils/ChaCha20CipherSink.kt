package com.wire.kalium.cryptography.utils

import okio.Buffer
import okio.BufferedSink
import okio.Sink
import java.io.IOException
import javax.crypto.Cipher

class ChaCha20CipherSink(
    private val sink: BufferedSink,
    val cipher: Cipher
) : Sink {
    private var closed = false

    @Throws(IOException::class)
    override fun write(source: Buffer, byteCount: Long) {
        checkOffsetAndCount(source.size, 0, byteCount)
        check(!closed) { "closed" }

        var remaining = byteCount
        while (remaining > 0) {
            val size = update(source, remaining)
            remaining -= size
        }
    }

    private fun update(source: Buffer, remaining: Long): Int {
        sink.write(cipher.update(source.readByteArray(remaining)))
        return remaining.toInt()
    }

    override fun flush() = sink.flush()

    override fun timeout() = sink.timeout()

    @Throws(IOException::class)
    override fun close() {
        if (closed) return
        closed = true

        var thrown = doFinal()

        try {
            sink.close()
        } catch (e: Throwable) {
            if (thrown == null) thrown = e
        }

        if (thrown != null) throw thrown
    }

    private fun doFinal(): Throwable? {
        try {
            sink.write(cipher.doFinal())
        } catch (t: Throwable) {
            return t
        }
        return null
    }
}

internal fun checkOffsetAndCount(size: Long, offset: Long, byteCount: Long) {
    if (offset or byteCount < 0 || offset > size || size - offset < byteCount) {
        throw ArrayIndexOutOfBoundsException("size=$size offset=$offset byteCount=$byteCount")
    }
}
