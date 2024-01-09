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

class ValidateEmailUseCaseTest {

    private val validateEmailUseCase: ValidateEmailUseCase = ValidateEmailUseCaseImpl()

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsValid_thenReturnTrue() {
        VALID_EMAILS.forEach { validEmail ->
            assertTrue(message = "$validEmail is invalid ") { validateEmailUseCase(validEmail) }
        }
    }

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsInValid_thenReturnFalse() {
        INVALID_EMAILS.forEach { inValidEmail ->
            assertFalse(message = "$inValidEmail is valid ") { validateEmailUseCase(inValidEmail) }
        }
    }

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsShort_thenReturnFalse() {
        assertFalse { validateEmailUseCase("1@3.") }
    }

    private companion object {
        val VALID_EMAILS =
            listOf(
                "my_email.me@fu-berlin.de",
                "test@domain.com",
                "test.email.with+symbol@domain.com",
                "id-with-dash@domain.com",
                "a@domain.com",
                "example-abc@abc-domain.com",
                "example@s.solutions",
                "exaMple@S.solutions.COM",
                "A@DOMAIN.DE"
            )

        val INVALID_EMAILS = listOf(
            "example.com",
            ".test@domain.com",
            "test..test@domain.com",
            " email@domain.de",
            "test@domain@domain.com"
        )
    }
}
