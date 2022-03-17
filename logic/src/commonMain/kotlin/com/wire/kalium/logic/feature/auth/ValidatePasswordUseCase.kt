package com.wire.kalium.logic.feature.auth

interface ValidatePasswordUseCase {
    operator fun invoke(password: String): Boolean
}


class ValidatePasswordUseCaseImpl : ValidatePasswordUseCase {
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
        private val PASSWORD_REGEX = ("^" +
                "(?=.*[a-z])" +                  // at least one lowercase ASCII letter
                "(?=.*[A-Z])" +                  // at least one uppercase ASCII letter
                "(?=.*[0-9])" +                  // at least a digit
                "(?=.*[^a-zA-Z0-9])" +           // at least a "special character"
                ".{$PASSWORD_MIN_LENGTH,}" +     //min PASSWORD_MIN_LENGTH characters
                "$"
                ).toRegex()
    }
}
