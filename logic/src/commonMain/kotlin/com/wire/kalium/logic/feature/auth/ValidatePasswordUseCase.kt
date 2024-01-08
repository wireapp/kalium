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

/**
 * Validates a password
 */
interface ValidatePasswordUseCase {
    /**
     * Validates a password
     * @param password The password to validate
     * @return true if the password is valid, false otherwise
     */
    operator fun invoke(password: String): ValidatePasswordResult
}

internal class ValidatePasswordUseCaseImpl : ValidatePasswordUseCase {
    override operator fun invoke(password: String): ValidatePasswordResult =
        if (password.matches(PASSWORD_REGEX)) ValidatePasswordResult.Valid
        else ValidatePasswordResult.Invalid(
            missingLowercaseCharacter = !password.matches(PASSWORD_LOWERCASE_REGEX),
            missingUppercaseCharacter = !password.matches(PASSWORD_UPPERCASE_REGEX),
            missingDigit = !password.matches(PASSWORD_DIGIT_REGEX),
            missingSpecialCharacter = !password.matches(PASSWORD_SPECIAL_CHAR_REGEX),
            tooShort = !password.matches(PASSWORD_LENGTH_REGEX),
        )

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
        private val PASSWORD_LOWERCASE_REGEX = "^.*[a-z].*$".toRegex() // at least one lowercase ASCII letter
        private val PASSWORD_UPPERCASE_REGEX = "^.*[A-Z].*$".toRegex() // at least one uppercase ASCII letter
        private val PASSWORD_DIGIT_REGEX = "^.*[0-9].*$".toRegex() // at least a digit
        private val PASSWORD_SPECIAL_CHAR_REGEX = "^.*[^a-zA-Z0-9].*$".toRegex() // at least a "special character"
        private val PASSWORD_LENGTH_REGEX = "^.{$PASSWORD_MIN_LENGTH,}$".toRegex() // min PASSWORD_MIN_LENGTH characters
    }
}

sealed class ValidatePasswordResult {
    data object Valid : ValidatePasswordResult()
    data class Invalid(
        val missingLowercaseCharacter: Boolean = true,
        val missingUppercaseCharacter: Boolean = true,
        val missingDigit: Boolean = true,
        val missingSpecialCharacter: Boolean = true,
        val tooShort: Boolean = true,
    ) : ValidatePasswordResult()

    val isValid: Boolean
        get() = this is Valid
}
