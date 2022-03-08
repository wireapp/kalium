package com.wire.kalium.logic.core_failure

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapCryptoRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WrapCryptoRequestTest {

    @Test
    fun whenACryptoRequestReturnValue_thenSuccessIsPropagated() {
        val expected = listOf<PreKeyCrypto>(PreKeyCrypto(1, "key 1"), PreKeyCrypto(2, "key 2"))
        val actual = wrapCryptoRequest { expected }

        assertIs<Either.Right<List<PreKeyCrypto>>>(actual)
        assertEquals(expected, actual.value)
    }

    @Test
    fun whenApiRequestReturnNoInternetConnection_thenCorrectErrorIsPropagated() {
        val expected = ProteusException(null, ProteusException.Code.PANIC)
        val actual = wrapCryptoRequest { throw expected }

        assertIs<Either.Left<ProteusFailure>>(actual)
        assertEquals(expected, actual.value.proteusException)
    }
}
