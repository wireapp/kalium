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

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.UpdateClientCapabilitiesParam
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UpdateSelfClientCapabilityToLegalHoldConsentUseCaseTest {

    @Test
    fun givenAppSyncing_whenInvokingUseCase_thenDoNotUpdateCapabilities() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSyncOngoing()
            .arrange()

        useCase.invoke()

        coVerify {
            arrangement.userConfigRepository.shouldUpdateClientLegalHoldCapability()
        }.wasNotInvoked()
    }

    @Test
    fun givenUserConfigRepositoryReturnsFalse_whenInvoking_thenTheClientCapabilitiesShouldNotBeUpdated() =
        runTest {
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateClientLegalHoldCapabilityResult(false)
                .arrange()

            useCase.invoke()

            coVerify {
                arrangement.userConfigRepository.shouldUpdateClientLegalHoldCapability()
            }.wasInvoked(once)
            coVerify {
                arrangement.selfClientIdProvider.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenUserConfigRepositoryReturnsTrue_whenInvoking_thenTheClientCapabilitiesShouldBeUpdated() =
        runTest {
            // given
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateClientLegalHoldCapabilityResult(true)
                .withClientId()
                .withUpdateClientCapabilitiesSuccess()
                .withSetShouldUpdateClientLegalHoldCapabilitySuccess()
                .arrange()

            useCase.invoke()

            coVerify {
                arrangement.userConfigRepository.shouldUpdateClientLegalHoldCapability()
            }.wasInvoked(once)
            coVerify {
                arrangement.selfClientIdProvider.invoke()
            }.wasInvoked(once)
            coVerify {
                arrangement.clientRemoteRepository.updateClientCapabilities(any(), any())
            }.wasInvoked(once)
            coVerify {
                arrangement.userConfigRepository.setShouldUpdateClientLegalHoldCapability(eq(false))
            }.wasInvoked(once)
        }

    private class Arrangement {

        @Mock
        val clientRemoteRepository: ClientRemoteRepository = mock(ClientRemoteRepository::class)

        @Mock
        val userConfigRepository: UserConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val selfClientIdProvider: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val incrementalSyncRepository: IncrementalSyncRepository = mock(IncrementalSyncRepository::class)

        val useCase: UpdateSelfClientCapabilityToLegalHoldConsentUseCase by lazy {
            UpdateSelfClientCapabilityToLegalHoldConsentUseCaseImpl(
                clientRemoteRepository = clientRemoteRepository,
                userConfigRepository = userConfigRepository,
                selfClientIdProvider = selfClientIdProvider,
                incrementalSyncRepository = incrementalSyncRepository,
                kaliumLogger = kaliumLogger
            )
        }

        fun arrange() = this to useCase

        fun withSyncOngoing() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(flowOf(IncrementalSyncStatus.FetchingPendingEvents))
        }

        fun withSyncDone() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            }.returns(flowOf(IncrementalSyncStatus.Live))
        }

        suspend fun withShouldUpdateClientLegalHoldCapabilityResult(result: Boolean) = apply {
            coEvery {
                userConfigRepository.shouldUpdateClientLegalHoldCapability()
            }.returns(result)
        }

        suspend fun withClientId() = apply {
            coEvery {
                selfClientIdProvider.invoke()
            }.returns(Either.Right(ClientId("clientId")))
        }

        suspend fun withUpdateClientCapabilitiesSuccess() = apply {
            coEvery {
                clientRemoteRepository.updateClientCapabilities(
                    eq(UpdateClientCapabilitiesParam(listOf(ClientCapability.LegalHoldImplicitConsent))),
                    any()
                )
            }.returns(Either.Right(Unit))
        }

        suspend fun withSetShouldUpdateClientLegalHoldCapabilitySuccess() = apply {
            coEvery {
                userConfigRepository.setShouldUpdateClientLegalHoldCapability(any())
            }.returns(Either.Right(Unit))
        }
    }
}
