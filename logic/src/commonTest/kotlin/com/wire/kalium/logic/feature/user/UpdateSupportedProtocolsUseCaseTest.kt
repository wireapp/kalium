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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.COMPLETED_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.DISABLED_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.ONGOING_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateSupportedProtocolsUseCaseTest {

    @Test
    fun givenSlowSyncIsCompleted_whenInvokingUseCase_thenContinueByFetchingSelfUser() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = emptySet(),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = emptyList())
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::getSelfUser)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSupportedProtocolsHasNotChanged_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful(supportedProtocols = setOf(SupportedProtocol.PROTEUS))
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = emptyList())
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenProteusAsSupportedProtocol_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = emptyList())
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupportedButMigrationHasNotEnded_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.MLS),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = emptyList())
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupported_whenInvokingUseCase_thenProteusIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.MLS),
                migrationConfiguration = COMPLETED_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = emptyList())
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.MLS),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = listOf(
                TestClient.CLIENT.copy(isMLSCapable = true)
            ))
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllClientsAreNotCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.MLS),
                migrationConfiguration = ONGOING_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = listOf(
                TestClient.CLIENT.copy(isMLSCapable = true),
                TestClient.CLIENT.copy(isMLSCapable = false)
            ))
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndMigrationHasEnded_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.MLS),
                migrationConfiguration = COMPLETED_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = listOf(
                TestClient.CLIENT.copy(isMLSCapable = true),
                TestClient.CLIENT.copy(isMLSCapable = false)
            ))
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMigrationIsMissingAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS),
                migrationConfiguration = null
            )
            .withGetSelfClientsSuccessful(clients = listOf(
                TestClient.CLIENT.copy(isMLSCapable = true)
            ))
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsNotSupportedAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSlowSyncStatusComplete()
            .withGetSelfUserSuccessful()
            .withGetFeatureConfigurationSuccessful(
                supportedProtocols = setOf(SupportedProtocol.PROTEUS),
                migrationConfiguration = DISABLED_MIGRATION_CONFIGURATION
            )
            .withGetSelfClientsSuccessful(clients = listOf(
                TestClient.CLIENT.copy(isMLSCapable = true)
            ))
            .withUpdateSupportedProtocolsSuccessful()
            .arrange()

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val clientRepository = mock(ClientRepository::class)
        @Mock
        val userRepository = mock(UserRepository::class)
        @Mock
        val featureConfigRepository = mock(FeatureConfigRepository::class)
        @Mock
        val slowSyncRepository = mock(SlowSyncRepository::class)

        fun withSlowSyncStatusComplete() = apply {
            val stateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(stateFlow)
        }

        fun withGetSelfUserSuccessful(supportedProtocols: Set<SupportedProtocol>? = null) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(TestUser.SELF.copy(
                    supportedProtocols = supportedProtocols
                ))
        }

        fun withUpdateSupportedProtocolsSuccessful() = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSupportedProtocols)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetFeatureConfigurationSuccessful(
            supportedProtocols: Set<SupportedProtocol>,
            migrationConfiguration: MLSMigrationModel?) = apply {
            given(featureConfigRepository)
                .suspendFunction(featureConfigRepository::getFeatureConfigs)
                .whenInvoked()
                .thenReturn(Either.Right(FeatureConfigTest.newModel(
                    mlsModel = MLSModel(
                        allowedUsers = emptyList(),
                        supportedProtocols = supportedProtocols,
                        status = Status.ENABLED
                    ),
                    mlsMigrationModel = migrationConfiguration
                )))
        }

        fun withGetSelfClientsSuccessful(clients: List<Client>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::selfListOfClients)
                .whenInvoked()
                .thenReturn(Either.Right(clients))
        }

        fun arrange() = this to UpdateSupportedProtocolsUseCaseImpl(
            clientRepository,
            userRepository,
            featureConfigRepository,
            slowSyncRepository
        )

        companion object {
            val ONGOING_MIGRATION_CONFIGURATION = MLSMigrationModel(
                Instant.DISTANT_PAST,
                Instant.DISTANT_FUTURE,
                0,
                0,
                Status.ENABLED
            )
            val COMPLETED_MIGRATION_CONFIGURATION = ONGOING_MIGRATION_CONFIGURATION
                .copy(endTime = Instant.DISTANT_PAST)
            val DISABLED_MIGRATION_CONFIGURATION = ONGOING_MIGRATION_CONFIGURATION
                .copy(status = Status.DISABLED)
        }
    }
}
