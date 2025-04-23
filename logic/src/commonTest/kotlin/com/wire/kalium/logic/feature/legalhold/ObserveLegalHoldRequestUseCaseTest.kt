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
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
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

            assertTrue(result.first() is ObserveLegalHoldRequestUseCase.Result.NoLegalHoldRequest)
        }

    @Test
    fun givenUserConfigRepositoryOtherFailure_whenObserving_thenPropagateFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withUserConfigRepositoryFailure()
            .arrange()

        val result = useCase()

        assertTrue(result.first() is ObserveLegalHoldRequestUseCase.Result.Failure)
    }

    @Test
    fun givenPreKeyRepositoryFailure_whenObserving_thenPropagateFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withUserConfigRepositorySuccess()
            .withPreKeyRepositoryFailure()
            .arrange()

        val result = useCase()

        assertTrue(result.first() is ObserveLegalHoldRequestUseCase.Result.Failure)
    }

    @Test
    fun givenPreKeyRepositorySuccess_whenObserving_thenPropagateLegalHoldRequestAvailable() =
        runTest {
            val (_, useCase) = Arrangement()
                .withUserConfigRepositorySuccess()
                .withPreKeyRepositorySuccess()
                .arrange()

            val result = useCase()

            assertTrue(result.first() is ObserveLegalHoldRequestUseCase.Result.LegalHoldRequestAvailable)
        }

    private class Arrangement {
        val userConfigRepository = mock(UserConfigRepository::class)
        val preKeyRepository = mock(PreKeyRepository::class)

        fun withUserConfigRepositorySuccess() = apply {
            every {
                userConfigRepository.observeLegalHoldRequest()
            }.returns(flowOf(Either.Right(legalHoldRequest)))
        }

        fun withUserConfigRepositoryDataNotFound() = apply {
            every {
                userConfigRepository.observeLegalHoldRequest()
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withUserConfigRepositoryFailure() = apply {
            every {
                userConfigRepository.observeLegalHoldRequest()
            }.returns(flowOf(Either.Left(StorageFailure.Generic(IllegalStateException()))))
        }

        suspend fun withPreKeyRepositoryFailure() = apply {
            coEvery {
                preKeyRepository.getFingerprintForPreKey(any())
            }.returns(Either.Left(CoreFailure.SyncEventOrClientNotFound))
        }

        suspend fun withPreKeyRepositorySuccess() = apply {
            coEvery {
                preKeyRepository.getFingerprintForPreKey(any())
            }.returns(Either.Right(fingerPrint))
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
