/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.util

import co.touchlab.kermit.Logger
import okio.ForwardingSink
import okio.Sink

/**
 * A sink that tracks the progress of writing data to a delegate sink.
 *
 * @param delegate The sink to which data is written.
 * @param totalBytes The total number of bytes that will be written.
 * @param onProgress A callback that is invoked with the number of bytes written and the total number of bytes.
 */
class ProgressTrackingSink(
    delegate: Sink,
    private val totalBytes: Long,
    private val onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
) : ForwardingSink(delegate) {
    private var bytesWritten: Long = 0

    override fun write(source: okio.Buffer, byteCount: Long) {
        super.write(source, byteCount)
        bytesWritten += byteCount
        onProgress(bytesWritten, totalBytes)
    }

    override fun close() {
        delegate.close()
    }

    override fun flush() {
        delegate.flush()
    }
}
