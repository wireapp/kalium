/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.api.util

import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.HttpResponseData
import com.wire.kalium.network.utils.UnauthorizedResponseInterceptor
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UnauthorizedResponseInterceptorTest {

    private val subject = UnauthorizedResponseInterceptor

    @Test
    fun givenBadRequest_whenIntercepting_thenShouldReturnNull() = runTest {
        val result = subject.intercept(HttpResponseData(emptyMap(), HttpStatusCode.BadRequest, "", KtxSerializer.json))
        assertNull(result)
    }

    @Test
    fun givenUnauthorized_whenIntercepting_thenShouldReturnUnauthorizedError() = runTest {
        val result = subject.intercept(HttpResponseData(emptyMap(), HttpStatusCode.Unauthorized, "", KtxSerializer.json))
        assertNotNull(result)
        assertIs<KaliumException.Unauthorized>(result.kException)
    }

}
