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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import okio.IOException
import okio.Sink

internal actual fun fileDownloader(
    httpClient: HttpClient
): FileDownloader = FileDownloaderApple(httpClient)

public class FileDownloaderApple(
    private val httpClient: HttpClient
) : FileDownloader {

    override suspend fun downloadViaPresignedUrl(
        presignedUrl: String,
        outFileSink: Sink,
        onProgressUpdate: (Long, Long) -> Unit,
    ): Either<NetworkFailure, Unit> {
        val response = httpClient.get(presignedUrl)

        if (!response.status.isSuccess()) {
            val exception = IOException("Download failed: ${response.status}")
            return Either.Left(NetworkFailure.ServerMiscommunication(exception))
        }

        response.bodyAsChannel().copyToSink(
            sink = outFileSink,
            contentLength = response.contentLength(),
            onProgressUpdate = onProgressUpdate,
        )

        return Either.Right(Unit)
    }
}
