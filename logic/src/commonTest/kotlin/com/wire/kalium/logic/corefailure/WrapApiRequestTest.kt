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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.utils.io.errors.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WrapApiRequestTest {

    @Test
    fun whenApiRequestReturnSuccess_thenSuccessIsPropagated() {
        val expected = NetworkResponse.Success("success", mapOf(), 200)
        val actual = wrapApiRequest { expected }

        assertIs<Either.Right<String>>(actual)
        assertEquals(expected.value, actual.value)
    }

    @Test
    fun whenApiRequestReturnAnError_thenCorrectErrorIsPropagated() {
        val expected = NetworkResponse.Error(
            KaliumException.ServerError(
                ErrorResponse(
                    500,
                    "have you tried turning it off and on again?", "server_crash"
                )
            )
        )
        val actual = wrapApiRequest { expected }

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected.kException, actual.value.kaliumException)
    }

    @Test
    fun whenApiRequestReturnAnIOException_thenALackOfConnectionIsPropagated() {
        val exception = KaliumException.GenericError(IOException("Oops doopsie"))

        val actual = wrapApiRequest { NetworkResponse.Error(exception) }

        assertIs<Either.Left<NetworkFailure.NoNetworkConnection>>(actual)
        assertEquals(exception, actual.value.cause)
    }
}
