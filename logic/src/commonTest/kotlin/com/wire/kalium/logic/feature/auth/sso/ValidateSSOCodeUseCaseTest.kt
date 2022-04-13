package com.wire.kalium.logic.feature.auth.sso

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateSSOCodeUseCaseTest {

    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase = ValidateSSOCodeUseCaseImpl()

    @Test
    fun givenValidateSSOCodeUseCaseIsInvoked_whenUUIDIsValid_thenReturnTrue() {
        VALID_SSO_CODES.forEach { code ->
            assertTrue {
                validateSSOCodeUseCase(code)
                    .let { it is ValidateSSOCodeResult.Valid && it.uuid == code.removePrefix(ValidateSSOCodeUseCase.SSO_CODE_WIRE_PREFIX) }
            }
        }
    }

    @Test
    fun givenValidateSSOCodeUseCaseIsInvoked_whenUUIDIsInvalid_thenReturnFalse() {
        INVALID_SSO_CODES.forEach { code -> assertTrue { validateSSOCodeUseCase(code) is ValidateSSOCodeResult.Invalid } }
    }

    private companion object {
        val VALID_SSO_CODES = listOf(
            "wire-fd994b20-b9af-11ec-ae36-00163e9b33ca", // v1
            "wire-e61648fc-774d-3cf2-b09f-1c4b7468be73", // v3
            "wire-93162444-0d5f-4c4e-9634-e6c20d46c9c4", // v4
            "wire-f64e3024-a3b4-5cdf-9c61-3dd40c27398b" // v5
        )

        val INVALID_SSO_CODES = listOf(
            "",
            "wire-abc",
            "wire-fd994b20b9af11ecae3600163e9b33ca",
            "wire_e61648fc_774d_3cf2_b09f_1c4b7468be73",
            "f64e302-4a3b4-5cdf-9c61-3dd40c27398b"
        )
    }
}
