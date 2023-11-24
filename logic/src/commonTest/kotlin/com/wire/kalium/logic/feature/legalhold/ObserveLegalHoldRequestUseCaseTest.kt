/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.legalhold.LastPreKey
import com.wire.kalium.logic.data.legalhold.LegalHoldRequest
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.functional.Either
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ObserveLegalHoldRequestUseCaseTest {

    @Test
    fun givenUserConfigRepositoryDataNotFoundFailure_whenObserving_thenPropagateNoLegalHoldRequest() =
        runTest {
            val (_, useCase) = Arrangement()
                .withUserConfigRepositoryDataNotFound()
                .arrange()

            val result = useCase()

            assertTrue(result.first() is ObserveLegalHoldRequestUseCaseResult.NoObserveLegalHoldRequest)
        }

    @Test
    fun givenUserConfigRepositoryOtherFailure_whenObserving_thenPropagateFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        val result = useCase()

        assertTrue(result.first() is ObserveLegalHoldRequestUseCaseResult.Failure)
    }

    @Test
    fun givenPreKeyRepositoryFailure_whenObserving_thenPropagateFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withUserConfigRepositorySuccess()
            .withPreKeyRepositoryFailure()
            .arrange()

        val result = useCase()

        assertTrue(result.first() is ObserveLegalHoldRequestUseCaseResult.Failure)
    }

    @Test
    fun givenPreKeyRepositorySuccess_whenObserving_thenPropagateLegalHoldRequestAvailable() =
        runTest {
            val (_, useCase) = Arrangement()
                .withUserConfigRepositorySuccess()
                .withPreKeyRepositorySuccess()
                .arrange()

            val result = useCase()

            assertTrue(result.first() is ObserveLegalHoldRequestUseCaseResult.ObserveLegalHoldRequestAvailable)
        }

    private class Arrangement {

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val preKeyRepository = mock(PreKeyRepository::class)

        fun withUserConfigRepositorySuccess() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeLegalHoldRequest)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(legalHoldRequest)))
        }

        fun withUserConfigRepositoryDataNotFound() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeLegalHoldRequest)
                .whenInvoked()
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withUserConfigRepositoryFailure() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::observeLegalHoldRequest)
                .whenInvoked()
                .thenReturn(flowOf(Either.Left(StorageFailure.Generic(IllegalStateException()))))
        }

        fun withPreKeyRepositoryFailure() = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::getFingerprintForPreKey)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        fun withPreKeyRepositorySuccess() = apply {
            given(preKeyRepository)
                .suspendFunction(preKeyRepository::getFingerprintForPreKey)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(fingerPrint))
        }

        fun arrange() = this to ObserveLegalHoldRequestUseCaseImpl(
            userConfigRepository = userConfigRepository,
            preKeyRepository = preKeyRepository
        )
    }

    companion object {
        val legalHoldRequest = LegalHoldRequest(
            clientId = ClientId("clientId"),
            lastPreKey = LastPreKey(
                id = 1,
                key = "key"
            )
        )
        val fingerPrint = "fingerPrint".toByteArray()
    }
}