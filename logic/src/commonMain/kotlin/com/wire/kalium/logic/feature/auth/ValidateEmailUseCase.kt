package com.wire.kalium.logic.feature.auth

interface ValidateEmailUseCase {
    operator fun invoke(email: String): Boolean
}


class ValidateEmailUseCaseImpl : ValidateEmailUseCase {
    override operator fun invoke(email: String): Boolean = when {
        isEmailTooShort(email) -> false
        !emailCharactersValid(email) -> false
        else -> true
    }

    private fun emailCharactersValid(email: String) =
        email.matches(EMAIL_REGEX)

    private fun isEmailTooShort(email: String) = email.length < EMAIL_MIN_LENGTH

    private companion object {
        private const val EMAIL_MIN_LENGTH = 5
        private val EMAIL_REGEX = """^[A-Za-z](.*)([@]{1})(.{1,})\.)(.{1,})""".toRegex()
    }
}
