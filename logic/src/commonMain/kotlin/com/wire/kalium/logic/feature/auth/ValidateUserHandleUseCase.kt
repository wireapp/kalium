package com.wire.kalium.logic.feature.auth

interface ValidateUserHandleUseCase {
    operator fun invoke(handle: String): ValidateUserHandleResult
}
class ValidateUserHandleUseCaseImpl: ValidateUserHandleUseCase  {
    override operator fun invoke(handle: String): ValidateUserHandleResult {
        val handleWithoutInvalidChars = handle.replace(Regex(HANDLE_FORBIDDEN_CHARACTERS_REGEX), "")
        val hasValidChars = handle == handleWithoutInvalidChars
        val hasValidLength = handleWithoutInvalidChars.length in HANDLE_MIN_LENGTH..HANDLE_MAX_LENGTH
        return ValidateUserHandleResult(hasValidChars && hasValidLength, handleWithoutInvalidChars)
    }

    private companion object {
        private const val HANDLE_FORBIDDEN_CHARACTERS_REGEX = "[^a-z0-9_]"
        private const val HANDLE_MIN_LENGTH = 2
        private const val HANDLE_MAX_LENGTH = 255
    }
}

data class ValidateUserHandleResult(val isValid: Boolean, val handleWithoutInvalidChars: String)
