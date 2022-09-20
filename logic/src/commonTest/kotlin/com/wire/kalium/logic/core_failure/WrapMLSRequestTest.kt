package com.wire.kalium.logic.core_failure

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.wrapCryptoRequest
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
            assertEquals(exception, it.rootCause)
        }
    }

}
