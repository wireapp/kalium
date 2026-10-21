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

package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.utils.decryptFileWithAES256
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import okio.Buffer
import okio.EOFException
import okio.Sink
import okio.ForwardingSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileDecryptionTest {
    @Test
    fun truncatedIvIsRejectedBeforeNativeDecryptionAndClosesResources() {
        val key = generateRandomAES256Key()
        for (size in 0 until 16) {
            var sourceClosed = false
            var sinkClosed = false
            val source = object : ForwardingSource(Buffer().write(ByteArray(size))) {
                override fun close() {
                    sourceClosed = true
                    super.close()
                }
            }
            val output = Buffer()
            val sink = object : Sink by output {
                override fun close() {
                    sinkClosed = true
                    output.close()
                }
            }
            assertFailsWith<EOFException>("Truncated IV size: $size") {
                decryptFileWithAES256(source, sink, key)
            }
            assertTrue(sourceClosed)
            assertTrue(sinkClosed)
        }
    }

    @Test
    fun fragmentedSourcePreservesTheFullIvAndCiphertext() {
        val key = generateRandomAES256Key()
        val input = ByteArray(129) { it.toByte() }
        val encrypted = Buffer()
        encryptFileWithAES256(Buffer().write(input), key, encrypted)
        val source = object : ForwardingSource(encrypted) {
            override fun read(sink: Buffer, byteCount: Long): Long = super.read(sink, minOf(1L, byteCount))
        }
        val decrypted = Buffer()

        assertEquals(input.size.toLong(), decryptFileWithAES256(source, decrypted, key))
        assertContentEquals(input, decrypted.readByteArray())
    }
}
