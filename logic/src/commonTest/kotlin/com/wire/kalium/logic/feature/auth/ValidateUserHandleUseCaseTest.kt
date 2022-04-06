package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateUserHandleUseCaseTest {

    private val validateUserHandleUseCase: ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is valid, then return true`() {
        VALID_HANDLES.forEach { validUserHandle ->
            assertTrue { validateUserHandleUseCase(validUserHandle).isValid }
        }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is invalid, then return false`() {
        INVALID_HANDLES.forEach { inValidUserHandle ->
            assertFalse { validateUserHandleUseCase(inValidUserHandle).isValid }
        }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is too short, then return TooShort`() {
        assertTrue { validateUserHandleUseCase("a") is ValidateUserHandleResult.Invalid.TooShort }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is too long, then return TooLong`() {
        assertTrue {
            validateUserHandleUseCase(
            "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890" +
                    "123456789012345678901234567890123456789012345678901234567890"
        )  is ValidateUserHandleResult.Invalid.TooLong }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is invalid, then return handle without invalid chars`() {
        val result = validateUserHandleUseCase("@handle1_A")
        assertTrue { result is ValidateUserHandleResult.Invalid.InvalidCharacters && result.handle == "handle1_" }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handle is too short and has invaled char, then return handle without invalid chars`() {
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
