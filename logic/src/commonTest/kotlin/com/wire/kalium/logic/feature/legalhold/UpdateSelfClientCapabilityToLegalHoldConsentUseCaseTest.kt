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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
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

        verify(arrangement.userConfigRepository)
            .suspendFunction(arrangement.userConfigRepository::shouldUpdateClientLegalHoldCapability)
            .wasNotInvoked()
    }

    @Test
    fun givenUserConfigRepositoryReturnsFalse_whenInvoking_thenTheClientCapabilitiesShouldNotBeUpdated() =
        runTest {
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateClientLegalHoldCapabilityResult(false)
                .arrange()

            useCase.invoke()

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::shouldUpdateClientLegalHoldCapability)
                .wasInvoked(once)
            verify(arrangement.selfClientIdProvider)
                .suspendFunction(arrangement.selfClientIdProvider::invoke)
                .wasNotInvoked()
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

            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::shouldUpdateClientLegalHoldCapability)
                .wasInvoked(once)
            verify(arrangement.selfClientIdProvider)
                .suspendFunction(arrangement.selfClientIdProvider::invoke)
                .wasInvoked(once)
            verify(arrangement.clientRemoteRepository)
                .suspendFunction(arrangement.clientRemoteRepository::updateClientCapabilities)
                .with(anything(), anything())
                .wasInvoked(once)
            verify(arrangement.userConfigRepository)
                .suspendFunction(arrangement.userConfigRepository::setShouldUpdateClientLegalHoldCapability)
                .with(eq(false))
                .wasInvoked(once)
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
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .thenReturn(flowOf(IncrementalSyncStatus.FetchingPendingEvents))
        }

        fun withSyncDone() = apply {
            given(incrementalSyncRepository)
                .getter(incrementalSyncRepository::incrementalSyncState)
                .whenInvoked()
                .thenReturn(flowOf(IncrementalSyncStatus.Live))
        }
        fun withShouldUpdateClientLegalHoldCapabilityResult(result: Boolean) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::shouldUpdateClientLegalHoldCapability)
                .whenInvoked()
                .thenReturn(result)
        }

        fun withClientId() = apply {
            given(selfClientIdProvider)
                .suspendFunction(selfClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(ClientId("clientId")))
        }

        fun withUpdateClientCapabilitiesSuccess() = apply {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::updateClientCapabilities)
                .whenInvokedWith(
                    eq(UpdateClientCapabilitiesParam(listOf(ClientCapability.LegalHoldImplicitConsent))),
                    any()
                )
                .thenReturn(Either.Right(Unit))
        }

        fun withSetShouldUpdateClientLegalHoldCapabilitySuccess() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::setShouldUpdateClientLegalHoldCapability)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }
    }
}
