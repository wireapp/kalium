package com.wire.kalium.logic.feature.auth

interface ValidateUserHandleUseCase {
    operator fun invoke(handle: String): ValidateUserHandleResult
}
class ValidateUserHandleUseCaseImpl: ValidateUserHandleUseCase  {
    override operator fun invoke(handle: String): ValidateUserHandleResult {
        val handleWithoutInvalidCharacters = handle.replace(Regex(HANDLE_FORBIDDEN_CHARACTERS_REGEX), "")
        val hasValidCharacters = handle == handleWithoutInvalidCharacters
        val tooShort = handleWithoutInvalidCharacters.length < HANDLE_MIN_LENGTH
        val tooLong = handleWithoutInvalidCharacters.length > HANDLE_MAX_LENGTH
        return when {
            tooShort -> ValidateUserHandleResult.Invalid.TooShort(handleWithoutInvalidCharacters)
            tooLong -> ValidateUserHandleResult.Invalid.TooLong(handleWithoutInvalidCharacters)
            !hasValidCharacters -> ValidateUserHandleResult.Invalid.InvalidCharacters(handleWithoutInvalidCharacters)
            else -> ValidateUserHandleResult.Valid
        }
    }

    private companion object {
        private const val HANDLE_FORBIDDEN_CHARACTERS_REGEX = "[^a-z0-9_]"
        private const val HANDLE_MIN_LENGTH = 2
        private const val HANDLE_MAX_LENGTH = 255
    }
}

sealed class ValidateUserHandleResult() {
    object Valid: ValidateUserHandleResult()
    sealed class Invalid(open val handleWithoutInvalidCharacters: String): ValidateUserHandleResult() {
        data class InvalidCharacters(override val handleWithoutInvalidCharacters: String): Invalid(handleWithoutInvalidCharacters)
        data class TooShort(override val handleWithoutInvalidCharacters: String): Invalid(handleWithoutInvalidCharacters)
        data class TooLong(override val handleWithoutInvalidCharacters: String): Invalid(handleWithoutInvalidCharacters)
    }
    val isValid: Boolean get() = this == Valid
}
