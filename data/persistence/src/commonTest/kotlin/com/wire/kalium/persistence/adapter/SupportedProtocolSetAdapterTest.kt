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
package com.wire.kalium.persistence.adapter

import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupportedProtocolSetAdapterTest {

    private val adapter = SupportedProtocolSetAdapter

    @Test
    fun givenEmptySet_whenEncodingAndDecoding_thenShouldReturnEmptySet() {
        val encoded = adapter.encode(emptySet())
        val decoded = adapter.decode(encoded)

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun givenEmptyString_whenDecodingAndEncoding_thenShouldReturnEmptyString() {
        val decoded = adapter.decode("")
        val encoded = adapter.encode(decoded)

        assertTrue(encoded.isEmpty())
    }

    @Test
    fun givenProteus_whenEncodingAndDecoding_thenShouldReturnProteus() {
        val encoded = adapter.encode(setOf(SupportedProtocolEntity.PROTEUS))
        val decoded = adapter.decode(encoded)

        assertEquals(1, decoded.size)
        assertContains(decoded, SupportedProtocolEntity.PROTEUS)
    }

    @Test
    fun givenMLSAndProteus_whenEncodingAndDecoding_thenShouldReturnMLSAndProteus() {
        val encoded = adapter.encode(setOf(SupportedProtocolEntity.MLS, SupportedProtocolEntity.PROTEUS))
        val decoded = adapter.decode(encoded)

        assertEquals(2, decoded.size)
        assertContains(decoded, SupportedProtocolEntity.MLS)
        assertContains(decoded, SupportedProtocolEntity.PROTEUS)
    }
}
