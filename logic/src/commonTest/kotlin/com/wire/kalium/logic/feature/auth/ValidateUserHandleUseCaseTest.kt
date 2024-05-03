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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateUserHandleUseCaseTest {

    private val validateUserHandleUseCase: ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsValid_thenReturnTrue() {
        VALID_HANDLES.forEach { validUserHandle ->
            assertTrue { validateUserHandleUseCase(validUserHandle).isValid }
        }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsInvalid_thenReturnFalse() {
        INVALID_HANDLES.forEach { inValidUserHandle ->
            assertFalse { validateUserHandleUseCase(inValidUserHandle).isValid }
        }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooShort_thenReturnTooShort() {
        assertTrue { validateUserHandleUseCase("a") is ValidateUserHandleResult.Invalid.TooShort }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooLong_thenReturnTooLong() {
        assertTrue {
            validateUserHandleUseCase(
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "123456789012345678901234567890123456789012345678901234567890"
        ) is ValidateUserHandleResult.Invalid.TooLong }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsInvalid_thenReturnHandleWithoutInvalidChars() {
        val result = validateUserHandleUseCase("@handle1_A")
        assertTrue { result is ValidateUserHandleResult.Invalid.InvalidCharacters && result.handle == "handle1_" }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooShortAndHasInvaledChar_thenReturnHandleWithoutInvalidChars() {
        val result = validateUserHandleUseCase("$")
        assertTrue { result is ValidateUserHandleResult.Invalid.InvalidCharacters && result.handle == "" }
    }

    @Test
    fun givenUserHandleContainsDots_whenValidating_thenReturnProperValues() {
        val handleWithDot = "user.name"
        val result = validateUserHandleUseCase(handleWithDot)
        assertTrue { result.isValid }
    }

    @Test
    fun givenUserHandleContainsUnderline_whenValidating_thenReturnProperValues() {
        val handleWithDot = "user_name"
        val result = validateUserHandleUseCase(handleWithDot)
        assertTrue { result.isValid }
    }

    @Test
    fun givenUserHandleContainsDash_whenValidating_thenReturnProperValues() {
        val handleWithDot = "user-name"
        val result = validateUserHandleUseCase(handleWithDot)
        assertTrue { result.isValid }
    }

    @Test
    fun givenUserHandleContainsInvalidCharacters_whenValidating_thenReturnListOfInvalidCharacters() {
        val handleWithDot = "user.name!with?invalid,characters"
        val result = validateUserHandleUseCase(handleWithDot)
        assertIs<ValidateUserHandleResult.Invalid.InvalidCharacters>(result)
        assertTrue { result.invalidCharactersUsed.toSet() == listOf('!', '?', ',').toSet() }
    }

    private companion object {
        val VALID_HANDLES = listOf(
            "cm",
            "hadle_",
            "user_99",
            "1-user",
            "user.name",
        )

        val INVALID_HANDLES = listOf(
            "c",
            "@hadle",
            "User_99",
            "1-uSer",
            "user,name",
        )
    }

}
