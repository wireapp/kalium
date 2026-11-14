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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class RandomPasswordTest {

    @Test
    fun given_GenerateRandomPasswordUseCase_when_generating_password_then_it_should_meet_the_specified_criteria() {
        val randomPassword = RandomPassword()

        repeat(100) { // Run the test 100 times
            val password = randomPassword.invoke()

            // Test criteria
            assertTrue(password.length >= RandomPassword.DEFAULT_MIN_LENGTH)
            assertTrue(password.length <= RandomPassword.DEFAULT_MAX_LENGTH)
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

    @Test
    fun given_custom_length_bounds_when_generating_password_then_length_and_character_requirements_are_met() {
        val randomPassword = RandomPassword()

        listOf(8 to 8, 8 to 14, 24 to 24, 20 to 25).forEach { (minLength, maxLength) ->
            repeat(100) {
                val password = randomPassword(minLength = minLength, maxLength = maxLength)

                assertTrue(password.length in minLength..maxLength)
                assertTrue(password.any { it in RandomPassword.lowercase })
                assertTrue(password.any { it in RandomPassword.uppercase })
                assertTrue(password.any { it in RandomPassword.digits })
                assertTrue(password.any { it in RandomPassword.specialChars })
            }
        }
    }

    @Test
    fun given_min_and_max_length_eight_when_generating_password_then_length_is_eight() {
        val password = RandomPassword()(minLength = 8, maxLength = 8)

        assertEquals(8, password.length)
    }

    @Test
    fun given_only_min_length_when_generating_password_then_default_max_length_is_used() {
        val password = RandomPassword()(minLength = 20)

        assertEquals(20, password.length)
    }

    @Test
    fun given_only_max_length_when_generating_password_then_default_min_length_is_used() {
        val password = RandomPassword()(maxLength = 15)

        assertEquals(15, password.length)
    }

    @Test
    fun given_min_length_below_eight_when_generating_password_then_it_is_rejected() {
        val randomPassword = RandomPassword()

        listOf(-1, 0, 7).forEach { minLength ->
            assertFailsWith<IllegalArgumentException> {
                randomPassword(minLength = minLength)
            }
        }
    }

    @Test
    fun given_max_length_below_min_length_when_generating_password_then_it_is_rejected() {
        assertFailsWith<IllegalArgumentException> {
            RandomPassword()(minLength = 8, maxLength = 7)
        }
    }
}
