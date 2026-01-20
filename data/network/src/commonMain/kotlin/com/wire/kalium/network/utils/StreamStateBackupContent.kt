/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.utils

import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import okio.Buffer
import okio.Source
import okio.use

/**
 * Streaming content handler for uploading cryptographic state backups.
 * Streams binary data from an Okio Source without loading the entire file into memory.
 */
internal class StreamStateBackupContent(
    private val backupDataSource: () -> Source,
    backupSize: Long
) : OutgoingContent.WriteChannelContent() {

    override val contentLength: Long = backupSize
    override val contentType: ContentType = ContentType.Application.OctetStream

    override suspend fun writeTo(channel: ByteWriteChannel) {
        backupDataSource().use { source ->
            val buffer = Buffer()
            while (source.read(buffer, BUFFER_SIZE) != -1L) {
                val byteArray = buffer.readByteArray()
                channel.writeFully(byteArray)
            }
        }
        channel.flush()
        channel.flushAndClose()
    }

    private companion object {
        const val BUFFER_SIZE = 8192L
    }
}
