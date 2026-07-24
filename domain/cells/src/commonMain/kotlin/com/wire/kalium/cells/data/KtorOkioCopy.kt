/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.cells.data

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.BufferedSink
import okio.Sink
import okio.buffer
import okio.use

internal suspend fun ByteReadChannel.copyToSink(
    sink: Sink,
    contentLength: Long? = null,
    onProgressUpdate: (Long, Long) -> Unit = { _, _ -> },
): Long = copyToSinkInternal(sink) { total ->
    contentLength?.let { onProgressUpdate(total, it) }
}

internal suspend fun ByteReadChannel.copyToSink(
    sink: Sink,
    onProgressUpdate: (Long) -> Unit,
): Long = copyToSinkInternal(sink, onProgressUpdate)

private suspend fun ByteReadChannel.copyToSinkInternal(
    sink: Sink,
    onProgressUpdate: (Long) -> Unit,
): Long {
    val buffer = ByteArray(COPY_BUFFER_SIZE)
    val total = sink.buffer().use { bufferedSink ->
        val copied = copyAvailableTo(bufferedSink, buffer, onProgressUpdate)
        bufferedSink.flush()
        copied
    }
    return total
}

private suspend fun ByteReadChannel.copyAvailableTo(
    sink: BufferedSink,
    buffer: ByteArray,
    onProgressUpdate: (Long) -> Unit,
): Long {
    var total = 0L
    var read = readAvailable(buffer)
    while (read != END_OF_CHANNEL) {
        if (read > 0) {
            sink.write(buffer, 0, read)
            total += read
            onProgressUpdate(total)
        }
        read = readAvailable(buffer)
    }
    return total
}

private const val COPY_BUFFER_SIZE = 8 * 1024
private const val END_OF_CHANNEL = -1
