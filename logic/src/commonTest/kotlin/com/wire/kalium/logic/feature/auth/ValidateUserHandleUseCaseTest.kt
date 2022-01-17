package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateUserHandleUseCaseTest {

    private val validateUserHandleUseCase: ValidateUserHandleUseCase = ValidateUserHandleUseCaseImpl()

    @Test
    fun `given a validUserHandleUseCase is invoked, when valid handel, then return true`() {
        VALID_HANDLES.forEach { validEmail ->
            assertTrue { validateUserHandleUseCase(validEmail) }
        }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when valid handel, then return false`() {
        INVALID_HANDLES.forEach { validEmail ->
            assertFalse { validateUserHandleUseCase(validEmail) }
        }
    }

    @Test
    fun `given a validUserHandleUseCase is invoked, when handel is short, then return false`() {
        assertFalse { validateUserHandleUseCase("a") }
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
