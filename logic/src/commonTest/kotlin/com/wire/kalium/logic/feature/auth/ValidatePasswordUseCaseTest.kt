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

package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatePasswordUseCaseTest {

    private val validatePasswordUseCase: ValidatePasswordUseCase = ValidatePasswordUseCaseImpl()

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsValid_thenReturnTrue() {
        VALID_PASSWORDS.forEach { validPassword ->
            assertTrue(message = "$validPassword is invalid ") { validatePasswordUseCase(validPassword).isValid }
        }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsInvalid_thenReturnFalse() {
        INVALID_PASSWORDS.forEach { invalidPassword ->
            assertFalse { validatePasswordUseCase(invalidPassword).isValid }
        }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsShort_thenReturnFalse() {
        assertTrue { validatePasswordUseCase("aA1@3.").let { it is ValidatePasswordResult.Invalid && it.tooShort } }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsMissingUppercase_thenReturnFalse() {
        assertTrue { validatePasswordUseCase("a1@3.").let { it is ValidatePasswordResult.Invalid && it.missingUppercaseCharacter } }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsMissingLowercase_thenReturnFalse() {
        assertTrue { validatePasswordUseCase("A1@3.").let { it is ValidatePasswordResult.Invalid && it.missingLowercaseCharacter } }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsMissingSpecialCharacter_thenReturnFalse() {
        assertTrue { validatePasswordUseCase("aA13").let { it is ValidatePasswordResult.Invalid && it.missingSpecialCharacter } }
    }

    @Test
    fun givenAValidatePasswordUseCaseIsInvoked_whenPasswordIsMissingDigit_thenReturnFalse() {
        assertTrue { validatePasswordUseCase("aA@.").let { it is ValidatePasswordResult.Invalid && it.missingDigit } }
    }

    private companion object {
        val VALID_PASSWORDS = listOf(
            "Passw0rd!",            // plain old vanilla password
            "Pass w0rd!",           // contains space
            "Päss w0rd!",           // contains umlaut
            "Päss\uD83D\uDC3Cw0rd!" // contains emoji
        )

        val INVALID_PASSWORDS = listOf(
            "aA1!",                 // too short (minimum length here is 8)
            "A1!A1!A1!A1!",         // no lowercase
            "a1!a1!a1!a1!",         // no uppercase
            "aA!aA!aA!aA!",         // no numbers
            "aA1aA1aA1aA1"          // no symbols
        )
    }
}
