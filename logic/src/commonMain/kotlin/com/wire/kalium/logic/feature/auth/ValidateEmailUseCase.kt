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
 * Validates an email address
 */
interface ValidateEmailUseCase {
    /**
     * Validates an email address
     * @param email The email address to validate
     * @return true if the email address is valid, false otherwise
     */
    operator fun invoke(email: String): Boolean
}

internal class ValidateEmailUseCaseImpl : ValidateEmailUseCase {
    override operator fun invoke(email: String): Boolean = when {
        isEmailTooShort(email) -> false
        !emailCharactersValid(email) -> false
        else -> true
    }

    private fun emailCharactersValid(email: String) =
        email.lowercase().matches(EMAIL_REGEX)

    private fun isEmailTooShort(email: String) = email.length < EMAIL_MIN_LENGTH

    private companion object {
        private const val EMAIL_MIN_LENGTH = 5
        private val EMAIL_REGEX = // RFC5322-compliant regex that covers 99.99% of input email addresses. http://emailregex.com/
            ("(?:[a-z0-9!#\$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#\$%&'*+/=?^_`{|}~-]+)*|\"" +
                    "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@" +
                    "(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" +
                    "|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}" +
                    "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:" +
                    "(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])"
                    ).toRegex()
    }
}
