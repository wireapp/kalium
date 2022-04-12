package com.wire.kalium.logic.feature.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidateUUIDUseCaseTest {

    private val validateUUIDUseCase: ValidateUUIDUseCase = ValidateUUIDUseCaseImpl()

    @Test
    fun givenValidateUUIDUseCaseIsInvoked_whenUUIDIsValid_thenReturnTrue() {
        VALID_UUIDS.forEach { uuid -> assertTrue { validateUUIDUseCase(uuid) } }
    }

    @Test
    fun givenValidateUUIDUseCaseIsInvoked_whenUUIDIsInvalid_thenReturnFalse() {
        INVALID_UUIDS.forEach { uuid -> assertFalse { validateUUIDUseCase(uuid) } }
    }

    private companion object {
        val VALID_UUIDS = listOf(
            "fd994b20-b9af-11ec-ae36-00163e9b33ca", // v1
            "e61648fc-774d-3cf2-b09f-1c4b7468be73", // v3
            "93162444-0d5f-4c4e-9634-e6c20d46c9c4", // v4
            "f64e3024-a3b4-5cdf-9c61-3dd40c27398b" // v5
        )

        val INVALID_UUIDS = listOf(
            "",
            "abc",
            "fd994b20b9af11ecae3600163e9b33ca",
            "e61648fc_774d_3cf2_b09f_1c4b7468be73",
            "f64e3024a3b4-5cdf-9c61-3dd40c27398b"
        )
    }
}
