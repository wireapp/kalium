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

import com.wire.kalium.cryptography.PreKeyCrypto
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapProteusRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WrapProteusRequestTest {

    @Test
    fun whenACryptoRequestReturnValue_thenSuccessIsPropagated() {
        val expected = listOf(PreKeyCrypto(1, "key 1"), PreKeyCrypto(2, "key 2"))
        val actual = wrapProteusRequest { expected }

        assertIs<Either.Right<List<PreKeyCrypto>>>(actual)
        assertEquals(expected, actual.value)
    }

    @Test
    fun whenApiRequestReturnNoInternetConnection_thenCorrectErrorIsPropagated() {
        val expected = ProteusException(null, ProteusException.Code.PANIC, 15, RuntimeException())
        val actual = wrapProteusRequest { throw expected }

        assertIs<Either.Left<ProteusFailure>>(actual)
        assertEquals(expected, actual.value.proteusException)
    }
}
