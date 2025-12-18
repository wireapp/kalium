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
package com.wire.kalium.cells.data

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import io.ktor.client.HttpClient
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.copyTo
import okio.Sink
import okio.buffer

internal actual fun fileDownloader(
    httpClient: HttpClient
): FileDownloader = FileDownloaderJvm(httpClient)

public class FileDownloaderJvm(
    private val httpClient: HttpClient
) : FileDownloader {

    override suspend fun downloadViaPresignedUrl(
        presignedUrl: String,
        outFileSink: Sink,
        onProgressUpdate: (Long, Long) -> Unit,
    ): Either<NetworkFailure, Unit> {

        val response = httpClient.get(presignedUrl) {
            onDownload { bytesSentTotal, contentLength ->
                contentLength?.let {
                    onProgressUpdate(bytesSentTotal, it)
                }
            }
        }

        if (!response.status.isSuccess()) {
            val exception = okio.IOException("Download failed: ${response.status}")
            return Either.Left(NetworkFailure.ServerMiscommunication(exception))
        }

        outFileSink.buffer().use { bufferedSink ->
            response.bodyAsChannel().copyTo(bufferedSink)
            bufferedSink.flush()
        }

        return Either.Right(Unit)
    }
}
