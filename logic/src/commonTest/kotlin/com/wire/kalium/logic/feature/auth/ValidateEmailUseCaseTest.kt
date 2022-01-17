package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class ValidateEmailUseCaseTest {

    private val validateEmailUseCase = ValidateEmailUseCaseImpl()

    @Test
    fun `given a validateEmailUseCase is invoked, when valid email, then return true`() {
        VALID_EMAILS.forEach { validEmail ->
            val result = validateEmailUseCase(validEmail)
            assertEquals(true, result)
        }
    }

    @Test
    fun `given a validateEmailUseCase is invoked, when inValid email, then return false`() {
        INVALID_EMAILS.forEach { validEmail ->
            val result = validateEmailUseCase(validEmail)
            assertEquals(false, result)
        }
    }

    @Test
    fun `given a validateEmailUseCase is invoked, when email is short, then return false`() {
            val result = validateEmailUseCase("1@3.")
            assertEquals(false, result)
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
                "example@s.solutions",
            )

        val INVALID_EMAILS = listOf(
            "example.com",
            //"A@b@c@domain.com",
            //"""abc”test”email@domain.com""",
            //"""abc is”not\valid@domain.com""",
            //"""abc\ is\”not\valid@domain.com""",
            ".test@domain.com",
            //"test@domain..com",
            " email@domain.de",
            //"email@domain.de "
        )
    }
}
