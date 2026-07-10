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

import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.io.IOException

internal suspend fun ByteWriteChannel.writeUploadBytes(bytes: ByteArray) {
    try {
        writeFully(bytes)
    } catch (cause: IOException) {
        throw RetryableTransportException(cause.message.orEmpty())
    }
}

internal suspend fun ByteWriteChannel.flushUpload() {
    try {
        flush()
    } catch (cause: IOException) {
        throw RetryableTransportException(cause.message.orEmpty())
    }
}

internal class RetryableTransportException(message: String) : Exception(message)
