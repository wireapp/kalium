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

package com.wire.kalium.logic.corefailure

import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.wrapMLSRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WrapMLSRequestTest {

    @Test
    fun givenSuccess_whenWrappingMLSRequest_thenSuccessIsPropagated() {
        val expected = "success"
        val actual = wrapMLSRequest { expected }

        assertIs<Either.Right<String>>(actual)
        assertEquals(expected, actual.value)
    }

    @Test
    fun givenExceptionIsThrown_whenWrappingMLSRequest_thenShouldReturnMLSFailureWithCorrectCause() {
        val exception = IllegalArgumentException("Test exception")

        val result = wrapMLSRequest {
            throw exception
        }

        result.shouldFail {
            assertIs<MLSFailure.Generic>(it)
            assertEquals(exception, it.rootCause)
        }
    }

    // Disabled until core-crypto multiplatform has been updated
//     @Test
//     fun givenWrongEpochExceptionIsThrown_whenWrappingMLSRequest_thenShouldMapToWrongEpochFailure() {
//         val exception = CryptoException.WrongEpoch("SomeMessage")
//
//         val result = wrapMLSRequest {
//             throw exception
//         }
//
//         result.shouldFail {
//             assertIs<MLSFailure.WrongEpoch>(it)
//         }
//     }

}
