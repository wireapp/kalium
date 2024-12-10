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
package com.wire.backup.envelope.header

import com.wire.backup.util.testHeader
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BackupHeaderSerializerTest {

    private val serializer = BackupHeaderSerializer.Default

    @Test
    fun givenHeader_whenSerializingAndThenDeserializing_shouldReturnOriginalInput() {
        val originalHeader = testHeader()
        val bytes = serializer.headerToBytes(originalHeader)
        val buffer = Buffer()
        buffer.writeAll(bytes)
        val result = serializer.parseHeader(buffer)
        assertIs<HeaderParseResult.Success>(result)
        assertEquals(originalHeader, result.header)
    }

    @Test
    fun givenProvidedByteArrayIsTooShort_whenParsing_shouldReturnUnknownFormat() {
        val validHeader = testHeader()
        val validHeaderBytes = serializer.headerToBytes(validHeader)
        val shortHeaderBytes = validHeaderBytes.readByteArray(validHeaderBytes.size - 1)
        val buffer = Buffer()
        buffer.write(shortHeaderBytes)
        val result = serializer.parseHeader(buffer)
        assertIs<HeaderParseResult.Failure.UnknownFormat>(result)
    }

    @Test
    fun givenProvidedBytesDoNotStartWithCorrectMagicNumber_whenParsing_shouldReturnUnknownFormat() {
        val validHeader = testHeader()
        val validHeaderBytes = serializer.headerToBytes(validHeader)
        val invalidHeaderBytes = validHeaderBytes.readByteArray(validHeaderBytes.size)
        invalidHeaderBytes[0] = 0x42
        invalidHeaderBytes[1] = 0x43
        invalidHeaderBytes[2] = 0x43
        val buffer = Buffer()
        buffer.write(invalidHeaderBytes)
        val result = serializer.parseHeader(buffer)
        assertIs<HeaderParseResult.Failure.UnknownFormat>(result)
    }

    @Test
    fun givenProvidedBytesAreFromAnOlderUnsupportedVersion_whenParsing_shouldReturnUnsupportedVersion() {
        val unsupportedVersion = serializer.MINIMUM_SUPPORTED_VERSION - 1
        val header = testHeader(version = unsupportedVersion)
        val validHeaderBytes = serializer.headerToBytes(header)
        val result = serializer.parseHeader(validHeaderBytes)
        assertIs<HeaderParseResult.Failure.UnsupportedVersion>(result)
        assertEquals(unsupportedVersion, result.version)
    }

    @Test
    fun givenProvidedBytesAreFromAnNewerUnsupportedVersion_whenParsing_shouldReturnUnsupportedVersion() {
        val unsupportedVersion = serializer.MAXIMUM_SUPPORTED_VERSION + 1
        val header = testHeader(version = unsupportedVersion)
        val validHeaderBytes = serializer.headerToBytes(header)
        val result = serializer.parseHeader(validHeaderBytes)
        assertIs<HeaderParseResult.Failure.UnsupportedVersion>(result)
        assertEquals(unsupportedVersion, result.version)
    }
}
