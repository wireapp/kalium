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

package com.wire.kalium.logic.data.id

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FederationRegexTest {
    @Test
    fun givenUser_correctFederatedId_matchesRegex() {
        val userId = "damian@wire.com"

        assertTrue(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_doubleDotFederation_matchesRegex() {
        val userId = "damian@wire.com.pl"

        assertTrue(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_tripleDotFederation_matchesRegex() {
        val userId = "damian@wire.com.pl.anything"

        assertTrue(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_containsNumericCharacters_matchesRegex() {
        val userId = "damian1@wire1.com1.pl1.anything1"

        assertTrue(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_containsSpecialCharacters_matchesRegex() {
        val userId = "damian1!#$%^&*()@wire1!#$%^&*().com1!#$%^&*().pl!#$%^&*()1.anything1!#$%^&*()"

        assertTrue(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_containsDoubleAtCharacter_notMatchesRegex() {
        val userId = "dam@ian@wire.com.pl.anything"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_missingAtCharacter_notMatchesRegex() {
        val userId = "damianwire.com.pl.anything"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_missingDotCharacter_notMatchesRegex() {
        val userId = "damian@wire"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_dotBeforeAtCharacter_notMatchesRegex() {
        val userId = "wire.com@damian"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_startsWithAtCharacter_notMatchesRegex() {
        val userId = "@damian.wire.com"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }

    @Test
    fun givenUser_correctFederatedId_endsWithAtCharacter_notMatchesRegex() {
        val userId = "damian.wire.com@"

        assertFalse(userId.matches(FEDERATION_REGEX))
    }
}
