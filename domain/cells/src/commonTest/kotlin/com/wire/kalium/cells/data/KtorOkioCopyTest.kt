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
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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

    private companion object {
        const val TEST_PAYLOAD_SIZE = 20 * 1024
    }
}
