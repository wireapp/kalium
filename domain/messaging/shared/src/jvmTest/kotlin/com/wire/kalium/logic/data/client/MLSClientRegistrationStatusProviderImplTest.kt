/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import java.lang.reflect.Proxy
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class MLSClientRegistrationStatusProviderImplTest {

    @Test
    fun givenStoredRegistrationStatus_whenReading_thenValueIsWrappedInRight() = runTest {
        val provider = MLSClientRegistrationStatusProviderImpl(storage { true })

        assertEquals(Either.Right(true), provider())
    }

    @Test
    fun givenStorageFailure_whenReading_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("registration read failed")
        val provider = MLSClientRegistrationStatusProviderImpl(storage { throw expected })

        assertEquals(Either.Left(StorageFailure.Generic(expected)), provider())
    }

    @Test
    fun givenStorageCancellation_whenReading_thenCancellationEscapes() = runTest {
        val expected = CancellationException("registration read cancelled")
        val provider = MLSClientRegistrationStatusProviderImpl(storage { throw expected })

        val actual = assertFailsWith<CancellationException> { provider() }

        assertSame(expected, actual)
    }

    private fun storage(readStatus: () -> Boolean): ClientRegistrationStorage =
        Proxy.newProxyInstance(
            ClientRegistrationStorage::class.java.classLoader,
            arrayOf(ClientRegistrationStorage::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "hasRegisteredMLSClient" -> readStatus()
                "toString" -> "ClientRegistrationStorageTestProxy"
                else -> error("Unexpected ClientRegistrationStorage call: ${method.name}")
            }
        } as ClientRegistrationStorage
}
