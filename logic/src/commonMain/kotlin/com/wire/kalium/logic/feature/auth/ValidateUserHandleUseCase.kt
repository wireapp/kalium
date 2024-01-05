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
 * Validates a user handle
 */
interface ValidateUserHandleUseCase {
    /**
     * Validates a user handle
     * @param handle The user handle to validate
     * @return [ValidateUserHandleResult] if user's handle is [ValidateUserHandleResult.Valid] or [ValidateUserHandleResult.Invalid]
     */
    operator fun invoke(handle: String): ValidateUserHandleResult
}

internal class ValidateUserHandleUseCaseImpl : ValidateUserHandleUseCase {
    override operator fun invoke(handle: String): ValidateUserHandleResult {
        val forbiddenCharactersRegex = Regex(HANDLE_FORBIDDEN_CHARACTERS_REGEX)
        val handleWithoutInvalidCharacters = handle.replace(forbiddenCharactersRegex, "")
        val hasOnlyValidCharacters = handle == handleWithoutInvalidCharacters
        val tooShort = handleWithoutInvalidCharacters.length < HANDLE_MIN_LENGTH
        val tooLong = handleWithoutInvalidCharacters.length > HANDLE_MAX_LENGTH
        return when {
            !hasOnlyValidCharacters -> {
                val allCharactersUsed = handle.toCharArray().distinct()
                val validCharactersUsed = handleWithoutInvalidCharacters.toCharArray().distinct()
                val invalidCharactersUsed = allCharactersUsed.minus(validCharactersUsed.toSet())
                ValidateUserHandleResult.Invalid.InvalidCharacters(handleWithoutInvalidCharacters, invalidCharactersUsed)
            }

            tooShort -> ValidateUserHandleResult.Invalid.TooShort(handleWithoutInvalidCharacters)
            tooLong -> ValidateUserHandleResult.Invalid.TooLong(handleWithoutInvalidCharacters)
            else -> ValidateUserHandleResult.Valid(handle)
        }
    }

    private companion object {
        private const val HANDLE_FORBIDDEN_CHARACTERS_REGEX = "[^a-z0-9._-]"
        private const val HANDLE_MIN_LENGTH = 2
        private const val HANDLE_MAX_LENGTH = 255
    }
}

sealed class ValidateUserHandleResult(val handle: String) {
    class Valid(handle: String) : ValidateUserHandleResult(handle)
    sealed class Invalid(handleWithoutInvalidCharacters: String) : ValidateUserHandleResult(handleWithoutInvalidCharacters) {
        class InvalidCharacters(
            handleWithoutInvalidCharacters: String,
            val invalidCharactersUsed: List<Char>
        ) : Invalid(handleWithoutInvalidCharacters)

        class TooShort(handleWithoutInvalidCharacters: String) : Invalid(handleWithoutInvalidCharacters)
        class TooLong(handleWithoutInvalidCharacters: String) : Invalid(handleWithoutInvalidCharacters)
    }

    val isValid: Boolean get() = this is Valid
}
