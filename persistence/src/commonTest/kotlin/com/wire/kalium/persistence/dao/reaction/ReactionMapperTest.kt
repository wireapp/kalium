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

package com.wire.kalium.persistence.dao.reaction

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionMapperTest {

    @Test
    fun givenValidReactionsJson_whenParsingToReactionsEntity_thenCorrectEntityIsReturned() {
        // Given
        val reactionsJson = """[{"emoji":"👍","count":3,"isSelf":true},{"emoji":"❤️","count":5,"isSelf":false}]"""

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertEquals(2, result.totalReactions.size)
        assertEquals(3, result.totalReactions["👍"])
        assertEquals(5, result.totalReactions["❤️"])
        assertEquals(setOf("👍"), result.selfUserReactions)
    }

    @Test
    fun givenEmptyReactionsJson_whenParsingToReactionsEntity_thenEmptyEntityIsReturned() {
        // Given
        val reactionsJson = "[]"

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertTrue(result.totalReactions.isEmpty())
        assertTrue(result.selfUserReactions.isEmpty())
    }

    @Test
    fun givenNullReactionsJson_whenParsingToReactionsEntity_thenEmptyEntityIsReturned() {
        // Given
        val reactionsJson: String? = null

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertEquals(ReactionsEntity.EMPTY, result)
    }

    @Test
    fun givenMultipleSelfReactions_whenParsingToReactionsEntity_thenAllSelfReactionsAreIncluded() {
        // Given
        val reactionsJson = """[{"emoji":"👍","count":2,"isSelf":true},{"emoji":"❤️","count":3,"isSelf":true},{"emoji":"😂","count":1,"isSelf":false}]"""

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertEquals(3, result.totalReactions.size)
        assertEquals(setOf("👍", "❤️"), result.selfUserReactions)
    }

    @Test
    fun givenNoSelfReactions_whenParsingToReactionsEntity_thenSelfReactionsSetIsEmpty() {
        // Given
        val reactionsJson = """[{"emoji":"👍","count":2,"isSelf":false},{"emoji":"❤️","count":3,"isSelf":false}]"""

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertEquals(2, result.totalReactions.size)
        assertTrue(result.selfUserReactions.isEmpty())
    }

    @Test
    fun givenInvalidJson_whenParsingToReactionsEntity_thenEmptyEntityIsReturned() {
        // Given
        val reactionsJson = "invalid json"

        // When
        val result = ReactionMapper.reactionsFromJsonString(reactionsJson)

        // Then
        assertEquals(ReactionsEntity.EMPTY, result)
    }
}
