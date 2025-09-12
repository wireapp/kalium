/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.auth.sso

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ValidateSSOCodeUseCaseTest {

    private val validateSSOCodeUseCase: ValidateSSOCodeUseCase = ValidateSSOCodeUseCaseImpl()

    @Test
    fun givenValidSSOCodes_whenValidating_thenReturnTrue() {
        VALID_SSO_CODES.forEach { code ->
            val result = validateSSOCodeUseCase(code)
            assertIs<ValidateSSOCodeResult.Valid>(
                result,
                "Expected SSO Code $code to be VALID. But UseCase returned ${result::class.simpleName}"
            )
            assertEquals(code.removePrefix(ValidateSSOCodeUseCase.SSO_CODE_WIRE_PREFIX), result.uuid)
        }
    }

    @Test
    fun givenInvalidSSOCodes_whenValidating_thenReturnFalse() {
        INVALID_SSO_CODES.forEach { code ->
            val result = validateSSOCodeUseCase(code)
            assertIs<ValidateSSOCodeResult.Invalid>(
                result,
                "Expected SSO Code $code to be INVALID. But UseCase returned ${result::class.simpleName}"
            )
        }
    }

    private companion object {
        val VALID_SSO_CODES = listOf(
            "wire-fd994b20-b9af-11ec-ae36-00163e9b33ca", // v1
            "wire-e61648fc-774d-3cf2-b09f-1c4b7468be73", // v3
            "wire-93162444-0d5f-4c4e-9634-e6c20d46c9c4", // v4
            "wire-f64e3024-a3b4-5cdf-9c61-3dd40c27398b", // v5
        )

        val INVALID_SSO_CODES = listOf(
            "",
            "wire-abc",
            "wire_e61648fc_774d_3cf2_b09f_1c4b7468be73",
            "f64e302-4a3b4-5cdf-9c61-3dd40c27398b",
        )
    }
}
