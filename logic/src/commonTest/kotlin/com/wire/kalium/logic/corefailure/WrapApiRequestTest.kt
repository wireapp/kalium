package com.wire.kalium.logic.corefailure

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.model.ErrorResponse
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
