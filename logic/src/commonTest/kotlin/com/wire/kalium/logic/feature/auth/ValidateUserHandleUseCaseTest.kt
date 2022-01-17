package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateUserHandleUseCaseTest {

    private val validateEmailUseCase = ValidateEmailUseCaseImpl()

    @Test
    fun `given a valid user handle is invoked, when valid handel, then return true`() {
        VALID_HANDLES.forEach { validEmail ->
            val result = validateEmailUseCase(validEmail)
            assertEquals(true, result)
        }
    }

    @Test
    fun `given a invalid user handle is invoked, when valid handel, then return false`() {
        INVALID_HANDLES.forEach { validEmail ->
            val result = validateEmailUseCase(validEmail)
            assertEquals(false, result)
        }
    }

    @Test
    fun `given a short user handle is invoked, when valid handel, then return false`() {
            val result = validateEmailUseCase("a")
            assertEquals(false, result)
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
