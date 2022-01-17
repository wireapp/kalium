package com.wire.kalium.logic.feature.auth

interface ValidateUserHandleUseCase {
    operator fun invoke(handle: String): Boolean
}
class ValidateUserHandleUseCaseImpl: ValidateUserHandleUseCase  {
    override operator fun invoke(handle: String): Boolean {
        return when {
            isHandleTooShort(handle) -> false
            !validateHandle(handle) -> false
            else -> true
        }
    }

    private fun validateHandle(handle: String) =
        handle.matches(HANDLE_REGEX)

    private fun isHandleTooShort(handle: String) = handle.length < HANDEL_MIN_LENGTH

    private companion object {
        private const val HANDEL_MIN_LENGTH = 2
        private val HANDLE_REGEX = "^[a-z0-9_]*\$".toRegex()
    }
}
