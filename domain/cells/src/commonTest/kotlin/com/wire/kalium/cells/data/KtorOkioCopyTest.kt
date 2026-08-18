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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KtorOkioCopyTest {

    @Test
    fun givenChannelWithoutContentLength_whenCopyingToSink_thenReportsCumulativeProgress() = runTest {
        val input = ByteArray(TEST_PAYLOAD_SIZE) { (it % Byte.MAX_VALUE).toByte() }
        val sink = Buffer()
        val progressUpdates = mutableListOf<Long>()

        val copied = ByteReadChannel(input).copyToSink(sink) { progressUpdates += it }

        assertEquals(input.size.toLong(), copied)
        assertContentEquals(input, sink.readByteArray())
        assertTrue(progressUpdates.isNotEmpty())
        assertEquals(input.size.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next > previous })
    }

    @Test
    fun givenChannelAlreadyClosedWithFailure_whenCopyingToSink_thenPropagatesFailure() = runTest {
        val failure = IOException("connection lost")
        val input = ByteChannel().apply { close(failure) }

        val exception = assertFailsWith<IOException> {
            input.copyToSink(Buffer())
        }

        assertSame(failure, exception.cause)
    }

    @Test
    fun givenSuccessfulResponse_whenDownloadingViaPresignedUrl_thenStreamsBodyAndProgress() = runTest {
        val payload = ByteArray(TEST_PAYLOAD_SIZE) { it.toByte() }
        val sink = Buffer()
        val progressUpdates = mutableListOf<Pair<Long, Long>>()
        val downloader = fileDownloader(
            HttpClient(
                MockEngine {
                    respond(
                        content = payload,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentLength, payload.size.toString()),
                    )
                }
            )
        )

        val result = downloader.downloadViaPresignedUrl(TEST_URL, sink) { downloaded, total ->
            progressUpdates += downloaded to total
        }

        assertIs<Either.Right<Unit>>(result)
        assertContentEquals(payload, sink.readByteArray())
        assertEquals(payload.size.toLong() to payload.size.toLong(), progressUpdates.last())
        assertTrue(progressUpdates.zipWithNext().all { (previous, next) -> next.first > previous.first })
    }

    @Test
    fun givenErrorResponse_whenDownloadingViaPresignedUrl_thenReturnsNetworkFailure() = runTest {
        val sink = Buffer()
        val downloader = fileDownloader(
            HttpClient(
                MockEngine {
                    respond(content = "", status = HttpStatusCode.Forbidden)
                }
            )
        )

        val result = downloader.downloadViaPresignedUrl(TEST_URL, sink)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(result)
        assertEquals(0L, sink.size)
    }

    private companion object {
        const val TEST_PAYLOAD_SIZE = 20 * 1024
        const val TEST_URL = "https://cells.example.test/download"
    }
}
