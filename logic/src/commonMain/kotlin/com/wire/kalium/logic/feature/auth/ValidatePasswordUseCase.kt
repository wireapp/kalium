/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

/**
 * Validates a password
 */
interface ValidatePasswordUseCase {
    /**
     * Validates a password
     * @param password The password to validate
     * @return true if the password is valid, false otherwise
     */
    operator fun invoke(password: String): Boolean
}

internal class ValidatePasswordUseCaseImpl : ValidatePasswordUseCase {
    override operator fun invoke(password: String): Boolean = when {
        isPasswordTooShort(password) -> false
        !passwordCharactersValid(password) -> false
        else -> true
    }

    private fun passwordCharactersValid(password: String) =
        password.matches(PASSWORD_REGEX)

    private fun isPasswordTooShort(password: String) = password.length < PASSWORD_MIN_LENGTH

    private companion object {
        private const val PASSWORD_MIN_LENGTH = 8

        @Suppress("NoMultipleSpaces")
        private val PASSWORD_REGEX = ("^" +
                "(?=.*[a-z])" + // at least one lowercase ASCII letter
                "(?=.*[A-Z])" + // at least one uppercase ASCII letter
                "(?=.*[0-9])" + // at least a digit
                "(?=.*[^a-zA-Z0-9])" + // at least a "special character"
                ".{$PASSWORD_MIN_LENGTH,}" + // min PASSWORD_MIN_LENGTH characters
                "$"
                ).toRegex()
    }
}
