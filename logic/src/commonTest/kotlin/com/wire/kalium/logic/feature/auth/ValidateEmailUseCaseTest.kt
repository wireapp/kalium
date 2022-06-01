package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateEmailUseCaseTest {

    private val validateEmailUseCase: ValidateEmailUseCase = ValidateEmailUseCaseImpl()

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsValid_thenReturnTrue() {
        VALID_EMAILS.forEach { validEmail ->
            assertTrue(message = "$validEmail is invalid ") { validateEmailUseCase(validEmail) }
        }
    }

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsInValid_thenReturnFalse() {
        INVALID_EMAILS.forEach { inValidEmail ->
            assertFalse(message = "$inValidEmail is valid ") { validateEmailUseCase(inValidEmail) }
        }
    }

    @Test
    fun givenAValidateEmailUseCaseIsInvoked_whenEmailIsShort_thenReturnFalse() {
        assertFalse { validateEmailUseCase("1@3.") }
    }

    private companion object {
        val VALID_EMAILS =
            listOf(
                "my_email.me@fu-berlin.de",
                "test@domain.com",
                "test.email.with+symbol@domain.com",
                "id-with-dash@domain.com",
                "a@domain.com",
                "example-abc@abc-domain.com",
                "example@s.solutions"
            )

        val INVALID_EMAILS = listOf(
            "example.com",
            ".test@domain.com",
            "test..test@domain.com",
            " email@domain.de",
            "test@domain@domain.com"
        )
    }
}
