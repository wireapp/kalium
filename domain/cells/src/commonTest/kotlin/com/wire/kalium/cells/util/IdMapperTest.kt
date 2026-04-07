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
package com.wire.kalium.cells.util

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdMapperTest {

    @Test
    fun given_ValidQualifiedId_whenInvoked_thenReturnQualifiedIdEntity() {
        // Given
        val qualifiedIdString = "user123@wire.com"

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertEquals(QualifiedIDEntity(value = "user123", domain = "wire.com"), result)
    }

    @Test
    fun given_QualifiedIdWithoutDomain_whenInvoked_thenReturnQualifiedIdWithEmptyDomain() {
        // Given
        val qualifiedIdString = "user123"

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertEquals(QualifiedIDEntity(value = "user123", domain = ""), result)
    }

    @Test
    fun given_NullString_whenInvoked_thenReturnNull() {
        // Given
        val qualifiedIdString: String? = null

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertNull(result)
    }

    @Test
    fun given_EmptyString_whenInvoked_thenReturnNull() {
        // Given
        val qualifiedIdString = ""

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertNull(result)
    }

    @Test
    fun given_WhitespaceOnlyString_whenInvoked_thenReturnNull() {
        // Given
        val qualifiedIdString = "   "

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertNull(result)
    }

    @Test
    fun given_StringWithWhitespace_whenInvoked_thenTrimAndParse() {
        // Given
        val qualifiedIdString = "  user456@example.com  "

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertEquals(QualifiedIDEntity(value = "user456", domain = "example.com"), result)
    }

    @Test
    fun given_StringWithMultipleAtSymbols_whenInvoked_thenParseCorrectly() {
        // Given
        val qualifiedIdString = "user@subdomain@example.com"

        // When
        val result = qualifiedIdString.toQualifiedIdOrNull()

        // Then
        assertEquals(QualifiedIDEntity(value = "user", domain = "subdomain@example.com"), result)
    }
}

