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
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
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
import com.wire.kalium.logic.sync.SyncRequestResult
import com.wire.kalium.logic.util.stubs.newServerConfig
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
        }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfServerConfigUseCase()
            }

            verifySuspend(VerifyMode.not) {
                arrangement.selfClientIdProvider.invoke()
            }
        }

    @Test
    fun givenShouldUpdate_AndClientIsEnabledByAPI_thenTheClientCapabilitiesShouldBeUpdatedAndLocalStateUpdated() =
        runTest {
            val config = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Valid(11), "domain"))
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfClientIdProvider.invoke()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.setShouldUpdateClientConsumableNotificationsCapability(eq(false))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.persistClientHasConsumableNotifications(eq(true))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }
        }

    @Test
    fun givenShouldUpdate_AndClientIsEnabledByAPIAndSyncFails_thenTheClientCapabilitiesShouldBeUpdatedAndLocalStateShouldNotUpdated() =
        runTest {
            val config = newServerConfig(1).copy(metaData = ServerConfig.MetaData(false, CommonApiVersionType.Valid(11), "domain"))
            val (arrangement, useCase) = Arrangement()
                .withSyncDone()
                .withShouldUpdateConsumableNotificationsCapabilityResult(true)
                .withClientHasConsumableNotifications(false)
                .withIsEnabledByAPIResult(SelfServerConfigUseCase.Result.Success(config))
                .withUpdateClientCapabilityResult(Unit.right())
                .withShouldUpdateClientConsumableNotificationsCapabilityResult(false)
                .withPersistClientHasConsumableNotificationsResult(true)
                .withClearLastSlowSyncCompletionInstantResult()
                .arrange { SyncRequestResult.Failure(CoreFailure.Unknown(RuntimeException("Failure"))) }


            useCase.invoke()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfClientIdProvider.invoke()
            }

            verifySuspend(VerifyMode.not) {
                arrangement.clientRepository.setShouldUpdateClientConsumableNotificationsCapability(eq(true))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.clientRepository.persistClientHasConsumableNotifications(eq(true))
            }

            verifySuspend(VerifyMode.not) {
                arrangement.slowSyncRepository.clearLastSlowSyncCompletionInstant()
            }
        }

    private class Arrangement {
        val clientRepository: ClientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)
        val clientRemoteRepository: ClientRemoteRepository = mock<ClientRemoteRepository>(mode = MockMode.autoUnit)
        val selfClientIdProvider = mock<CurrentClientIdProvider>(mode = MockMode.autoUnit)
        val incrementalSyncRepository = mock<IncrementalSyncRepository>(mode = MockMode.autoUnit)
        val selfServerConfigUseCase = mock<SelfServerConfigUseCase>(mode = MockMode.autoUnit)
        val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)

        init {
            runBlocking {
                everySuspend {
                    selfClientIdProvider.invoke()
                } returns Either.Right(CLIENT.id)
            }
        }

        fun withSyncOngoing() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            } returns flowOf(IncrementalSyncStatus.FetchingPendingEvents)
        }

        fun withSyncDone() = apply {
            every {
                incrementalSyncRepository.incrementalSyncState
            } returns flowOf(IncrementalSyncStatus.Live)
        }

        suspend fun withClientHasConsumableNotifications(result: Boolean) = apply {
            everySuspend {
                clientRepository.observeClientHasConsumableNotifications()
            } returns flowOf(result)
        }

        suspend fun withShouldUpdateConsumableNotificationsCapabilityResult(result: Boolean) = apply {
            everySuspend {
                clientRepository.shouldUpdateClientConsumableNotificationsCapability()
            } returns result
        }

        suspend fun withIsEnabledByAPIResult(result: SelfServerConfigUseCase.Result) = apply {
            everySuspend {
                selfServerConfigUseCase.invoke()
            } returns result
        }

        suspend fun withUpdateClientCapabilityResult(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend {
                clientRemoteRepository.updateClientCapabilities(matching {
                    it.capabilities.contains(ClientCapability.ConsumableNotifications)
                            && it.capabilities.contains(ClientCapability.LegalHoldImplicitConsent)
                }, eq(CLIENT.id.value))
            } returns result
        }

        suspend fun withShouldUpdateClientConsumableNotificationsCapabilityResult(should: Boolean) = apply {
            everySuspend { clientRepository.setShouldUpdateClientConsumableNotificationsCapability(should) } returns Unit.right()
        }

        suspend fun withPersistClientHasConsumableNotificationsResult(should: Boolean) = apply {
            everySuspend { clientRepository.persistClientHasConsumableNotifications(should) } returns Unit.right()
        }

        suspend fun withClearLastSlowSyncCompletionInstantResult() = apply {
            everySuspend { slowSyncRepository.clearLastSlowSyncCompletionInstant() } returns Unit
        }

        fun arrange(withSyncRequester: suspend () -> SyncRequestResult = { SyncRequestResult.Success }) =
            this@Arrangement to UpdateSelfClientCapabilityToConsumableNotificationsUseCaseImpl(
                selfClientIdProvider = selfClientIdProvider,
                clientRepository = clientRepository,
                clientRemoteRepository = clientRemoteRepository,
                incrementalSyncRepository = incrementalSyncRepository,
                selfServerConfig = selfServerConfigUseCase,
                slowSyncRepository = slowSyncRepository,
                syncRequester = withSyncRequester,
                logger = kaliumLogger
            )
    }
}
