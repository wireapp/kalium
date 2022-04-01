package com.wire.kalium.logic.feature.auth

interface ValidateUserHandleUseCase {
    operator fun invoke(handle: String): ValidateUserHandleResult
}

class ValidateUserHandleUseCaseImpl : ValidateUserHandleUseCase {
    override operator fun invoke(handle: String): ValidateUserHandleResult {
        val handleWithoutInvalidCharacters = handle.replace(Regex(HANDLE_FORBIDDEN_CHARACTERS_REGEX), "")
        val hasValidCharacters = handle == handleWithoutInvalidCharacters
        val tooShort = handleWithoutInvalidCharacters.length < HANDLE_MIN_LENGTH
        val tooLong = handleWithoutInvalidCharacters.length > HANDLE_MAX_LENGTH
        return when {
            !hasValidCharacters -> ValidateUserHandleResult.Invalid.InvalidCharacters(handleWithoutInvalidCharacters)
            tooShort -> ValidateUserHandleResult.Invalid.TooShort(handleWithoutInvalidCharacters)
            tooLong -> ValidateUserHandleResult.Invalid.TooLong(handleWithoutInvalidCharacters)
            else -> ValidateUserHandleResult.Valid(handle)
        }
    }

    private companion object {
        private const val HANDLE_FORBIDDEN_CHARACTERS_REGEX = "[^a-z0-9_]"
        private const val HANDLE_MIN_LENGTH = 2
        private const val HANDLE_MAX_LENGTH = 255
    }
}

sealed class ValidateUserHandleResult(val handle: String) {
    class Valid(handle: String) : ValidateUserHandleResult(handle)
    sealed class Invalid(handleWithoutInvalidCharacters: String) : ValidateUserHandleResult(handleWithoutInvalidCharacters) {
        class InvalidCharacters(handleWithoutInvalidCharacters: String) : Invalid(handleWithoutInvalidCharacters)
        class TooShort(handleWithoutInvalidCharacters: String) : Invalid(handleWithoutInvalidCharacters)
        class TooLong(handleWithoutInvalidCharacters: String) : Invalid(handleWithoutInvalidCharacters)
    }
    val isValid: Boolean get() = this is Valid
}
