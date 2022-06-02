package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateUserHandleUseCaseTest {

    private val validateUserHandleUseCase: ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsValid_thenReturnTrue() {
        VALID_HANDLES.forEach { validUserHandle ->
            assertTrue { validateUserHandleUseCase(validUserHandle).isValid }
        }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsInvalid_thenReturnFalse() {
        INVALID_HANDLES.forEach { inValidUserHandle ->
            assertFalse { validateUserHandleUseCase(inValidUserHandle).isValid }
        }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooShort_thenReturnTooShort() {
        assertTrue { validateUserHandleUseCase("a") is ValidateUserHandleResult.Invalid.TooShort }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooLong_thenReturnTooLong() {
        assertTrue {
            validateUserHandleUseCase(
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "123456789012345678901234567890123456789012345678901234567890"
        )  is ValidateUserHandleResult.Invalid.TooLong }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsInvalid_thenReturnHandleWithoutInvalidChars() {
        val result = validateUserHandleUseCase("@handle1_A")
        assertTrue { result is ValidateUserHandleResult.Invalid.InvalidCharacters && result.handle == "handle1_" }
    }

    @Test
    fun givenAValidUserHandleUseCaseIsInvoked_whenHandleIsTooShortAndHasInvaledChar_thenReturnHandleWithoutInvalidChars() {
        val result = validateUserHandleUseCase("$")
        assertTrue { result is ValidateUserHandleResult.Invalid.InvalidCharacters && result.handle == "" }
    }

    private companion object {
        val VALID_HANDLES = listOf(
            "cm",
            "hadle_",
            "user_99",
            "1_user"
        )

        val INVALID_HANDLES = listOf(
            "c",
            "@hadle",
            "User_99",
            "1_uSer"
        )
    }

}
