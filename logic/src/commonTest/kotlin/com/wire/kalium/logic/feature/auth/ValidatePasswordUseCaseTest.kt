package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidatePasswordUseCaseTest {

    private val validatePasswordUseCase: ValidatePasswordUseCase = ValidatePasswordUseCaseImpl()

    @Test
    fun `given a validatePasswordUseCase is invoked, when password is valid, then return true`() {
        VALID_PASSWORDS.forEach { validPassword ->
            assertTrue(message = "$validPassword is invalid ") { validatePasswordUseCase(validPassword) }
        }
    }

    @Test
    fun `given a validatePasswordUseCase is invoked, when password is invalid, then return false`() {
        INVALID_PASSWORDS.forEach { invalidPassword ->
            assertFalse { validatePasswordUseCase(invalidPassword) }
        }
    }

    @Test
    fun `given a validatePasswordUseCase is invoked, when password is short, then return false`() {
        assertFalse { validatePasswordUseCase("1@3.") }
    }

    private companion object {
        val VALID_PASSWORDS = listOf(
            "Passw0rd!",            // plain old vanilla password
            "Pass w0rd!",           // contains space
            "Päss w0rd!",           // contains umlaut
            "Päss\uD83D\uDC3Cw0rd!" // contains emoji
        )

        val INVALID_PASSWORDS = listOf(
            "aA1!",                 // too short (minimum length here is 8)
            "A1!A1!A1!A1!",         // no lowercase
            "a1!a1!a1!a1!",         // no uppercase
            "aA!aA!aA!aA!",         // no numbers
            "aA1aA1aA1aA1"          // no symbols
        )
    }
}
