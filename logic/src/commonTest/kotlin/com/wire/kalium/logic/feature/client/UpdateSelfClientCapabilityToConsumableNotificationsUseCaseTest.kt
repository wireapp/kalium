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
package com.wire.kalium.logic.feature.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientCapability
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.feature.user.SelfServerConfigUseCase
import com.wire.kalium.logic.framework.TestClient.CLIENT
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UpdateSelfClientCapabilityToConsumableNotificationsUseCaseTest {

    @Test
    fun givenAppSyncing_whenInvokingUseCase_thenDoNotUpdateCapabilities() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSyncOngoing()
            .arrange()

        useCase.invoke()

        coVerify {
            arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
        }.wasNotInvoked()
    }

    @Test
    fun givenShouldUpdate_AndClientIsNOTEnabledByAPI_thenTheClientCapabilitiesShouldNOTBeUpdated() =
        runTest {
            val config = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Valid(7), "domain"))
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateConsumableNotificationsCapabilityResult(true)
                .withClientHasConsumableNotifications(false)
                .withIsEnabledByAPIResult(SelfServerConfigUseCase.Result.Success(config))
                .arrange()

            useCase.invoke()

            coVerify {
                arrangement.selfServerConfigUseCase()
            }.wasInvoked(once)

            coVerify {
                arrangement.selfClientIdProvider.invoke()
            }.wasNotInvoked()
        }

    @Test
    fun givenShouldUpdate_AndClientIsEnabledByAPI_thenTheClientCapabilitiesShouldBeUpdatedAndLocalStateUpdated() =
        runTest {
            val config = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Valid(9), "domain"))
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateConsumableNotificationsCapabilityResult(true)
                .withClientHasConsumableNotifications(false)
                .withIsEnabledByAPIResult(SelfServerConfigUseCase.Result.Success(config))
                .withUpdateClientCapabilityResult(Unit.right())
                .withShouldUpdateClientConsumableNotificationsCapabilityResult(false)
                .withPersistClientHasConsumableNotificationsResult(true)
                .withClearLastSlowSyncCompletionInstantResult()
                .arrange()

            useCase.invoke()

            coVerify {
                arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            }.wasInvoked(once)

            coVerify {
                arrangement.selfClientIdProvider.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.clientRepository.setShouldUpdateClientConsumableNotificationsCapability(eq(false))
            }.wasInvoked(once)

            coVerify {
                arrangement.clientRepository.persistClientHasConsumableNotifications(eq(true))
            }.wasInvoked(once)

            coVerify {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }.wasInvoked(once)
        }

    @Test
    fun givenShouldUpdate_AndClientIsEnabledByAPIAndSyncFails_thenTheClientCapabilitiesShouldBeUpdatedAndLocalStateShouldNotUpdated() =
        runTest {
            val config = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Valid(8), "domain"))
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateConsumableNotificationsCapabilityResult(true)
                .withClientHasConsumableNotifications(false)
                .withIsEnabledByAPIResult(SelfServerConfigUseCase.Result.Success(config))
                .withUpdateClientCapabilityResult(Unit.right())
                .withShouldUpdateClientConsumableNotificationsCapabilityResult(false)
                .withPersistClientHasConsumableNotificationsResult(true)
                .withClearLastSlowSyncCompletionInstantResult()
                .arrange(withSyncRequester = { CoreFailure.Unknown(RuntimeException("Failure")).left() })

            useCase.invoke()

            coVerify {
                arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            }.wasInvoked(once)

            coVerify {
                arrangement.selfClientIdProvider.invoke()
            }.wasInvoked(once)

            coVerify {
                arrangement.clientRepository.setShouldUpdateClientConsumableNotificationsCapability(eq(true))
            }.wasNotInvoked()

            coVerify {
                arrangement.clientRepository.persistClientHasConsumableNotifications(eq(true))
            }.wasNotInvoked()

            coVerify {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }.wasNotInvoked()
        }

    private class Arrangement {
        val clientRepository: ClientRepository = mock(ClientRepository::class)
        val clientRemoteRepository: ClientRemoteRepository = mock(ClientRemoteRepository::class)
        val selfClientIdProvider = mock(CurrentClientIdProvider::class)
        val incrementalSyncRepository = mock(IncrementalSyncRepository::class)
        val selfServerConfigUseCase = mock(SelfServerConfigUseCase::class)
        val slowSyncRepository = mock(SlowSyncRepository::class)

        init {
            runBlocking {
                coEvery {
                    selfClientIdProvider.invoke()
                }.returns(Either.Right(CLIENT.id))
            }
        }

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

        suspend fun withClientHasConsumableNotifications(result: Boolean) = apply {
            coEvery {
                clientRepository.observeClientHasConsumableNotifications()
            }.returns(flowOf(result))
        }

        suspend fun withShouldUpdateConsumableNotificationsCapabilityResult(result: Boolean) = apply {
            coEvery {
                clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            }.returns(result)
        }

        suspend fun withIsEnabledByAPIResult(result: SelfServerConfigUseCase.Result) = apply {
            coEvery {
                selfServerConfigUseCase.invoke()
            }.returns(result)
        }

        suspend fun withUpdateClientCapabilityResult(result: Either<NetworkFailure, Unit>) = apply {
            coEvery {
                clientRemoteRepository.updateClientCapabilities(matches {
                    it.capabilities.contains(ClientCapability.ConsumableNotifications)
                            && it.capabilities.contains(ClientCapability.LegalHoldImplicitConsent)
                }, eq(CLIENT.id.value))
            }.returns(result)
        }

        suspend fun withShouldUpdateClientConsumableNotificationsCapabilityResult(should: Boolean) = apply {
            coEvery { clientRepository.setShouldUpdateClientConsumableNotificationsCapability(should) }
                .returns(Unit.right())
        }

        suspend fun withPersistClientHasConsumableNotificationsResult(should: Boolean) = apply {
            coEvery { clientRepository.persistClientHasConsumableNotifications(should) }
                .returns(Unit.right())
        }

        suspend fun withClearLastSlowSyncCompletionInstantResult() = apply {
            coEvery { slowSyncRepository.clearLastSlowSyncCompletionInstant() }
                .returns(Unit)
        }

        fun arrange(withSyncRequester: suspend () -> Either<CoreFailure, Unit> = { Unit.right() }) =
            this@Arrangement to UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl(
                selfClientIdProvider = selfClientIdProvider,
                clientRepository = clientRepository,
                clientRemoteRepository = clientRemoteRepository,
                incrementalSyncRepository = incrementalSyncRepository,
                selfServerConfig = selfServerConfigUseCase,
                slowSyncRepository = slowSyncRepository,
                syncRequester = withSyncRequester
            )
    }
}
