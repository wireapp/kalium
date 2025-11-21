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
package com.wire.kalium.logic.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class RandomPasswordTest {

    @Test
    fun given_GenerateRandomPasswordUseCase_when_generating_password_then_it_should_meet_the_specified_criteria() {
        val randomPassword = RandomPassword()

        repeat(100) { // Run the test 100 times
            val password = randomPassword.invoke()

            // Test criteria
            assertTrue(password.length >= RandomPassword.MIN_LENGTH)
            assertTrue(password.length <= RandomPassword.MAX_LENGTH)
            assertTrue(password.any { it in RandomPassword.lowercase })
            assertTrue(password.any { it in RandomPassword.uppercase })
            assertTrue(password.any { it in RandomPassword.digits })
            assertTrue(password.any { it in RandomPassword.specialChars })
        }
    }

    @Test
    fun given_character_sets_when_accessing_lowercase_then_expected_value_returned() {
        assertEquals("abcdefghijklmnopqrstuvwxyz".toList(), RandomPassword.lowercase.sorted())
    }

    @Test
    fun given_character_sets_when_accessing_uppercase_then_expected_value_returned() {
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWXYZ".toList(), RandomPassword.uppercase.sorted())
    }

    @Test
    fun given_character_sets_when_accessing_digits_then_expected_value_returned() {
        assertEquals("0123456789".toList(), RandomPassword.digits.sorted())
    }

    @Test
    fun given_character_sets_when_accessing_specialChars_then_expected_value_returned() {
        assertEquals("!@#$%^&*()_+[]{}|;:,.<>?-".toList().sorted(), RandomPassword.specialChars.sorted())
    }

    @Test
    fun given_character_sets_when_accessing_allCharacters_then_expected_value_returned() {
        val expectedAllCharacters =
            RandomPassword.lowercase +
                    RandomPassword.uppercase +
                    RandomPassword.digits +
                    RandomPassword.specialChars

        assertEquals(expectedAllCharacters.toList().sorted(), RandomPassword.allCharacters.sorted())
    }
}
