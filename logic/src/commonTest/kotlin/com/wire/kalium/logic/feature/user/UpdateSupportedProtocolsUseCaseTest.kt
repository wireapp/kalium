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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.COMPLETED_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.DISABLED_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsUseCaseTest.Arrangement.Companion.ONGOING_MIGRATION_CONFIGURATION
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test

class UpdateSupportedProtocolsUseCaseTest {

    @Test
    fun givenMLSIsNotSupported_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withIsMLSSupported(false)
            }

        useCase.invoke().shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenSupportedProtocolsHasNotChanged_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful(supportedProtocols = setOf(SupportedProtocol.PROTEUS))
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(anything())
            .wasNotInvoked()
    }

    @Test
    fun givenProteusAsSupportedProtocol_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupportedButMigrationHasNotEnded_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupported_whenInvokingUseCase_thenProteusIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(COMPLETED_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.PROTEUS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllActiveClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true, lastActive = Clock.System.now())
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAnInactiveClientIsNotMlsCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true, lastActive = Clock.System.now()),
                        TestClient.CLIENT.copy(isMLSCapable = false, lastActive = Instant.DISTANT_PAST)
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllActiveClientsAreNotCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = listOf(
                    TestClient.CLIENT.copy(isMLSCapable = true, lastActive = Clock.System.now()),
                    TestClient.CLIENT.copy(isMLSCapable = false, lastActive = Clock.System.now())
                ))
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndMigrationHasEnded_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(COMPLETED_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true),
                        TestClient.CLIENT.copy(isMLSCapable = false)
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMigrationIsMissingAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS, SupportedProtocol.MLS))
                withGetMigrationConfigurationFailing(StorageFailure.DataNotFound)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true)
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsNotSupportedAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS))
                withGetMigrationConfigurationSuccessful(DISABLED_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true)
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(matching { !it.contains(SupportedProtocol.MLS) })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSupportedProtocolsAreNotConfigured_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withGetSelfUserSuccessful(supportedProtocols = setOf(SupportedProtocol.PROTEUS))
                withGetSupportedProtocolsFailing(StorageFailure.DataNotFound)
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke().shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSupportedProtocols)
            .with(anything())
            .wasNotInvoked()
    }

    private class Arrangement : CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl() {
        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val featureSupport = mock(FeatureSupport::class)

        private var kaliumLogger = KaliumLogger.disabled()

        fun withIsMLSSupported(supported: Boolean) = apply {
            given(featureSupport)
                .invocation { featureSupport.isMLSSupported }
                .thenReturn(supported)
        }

        fun withGetSelfUserSuccessful(supportedProtocols: Set<SupportedProtocol>? = null) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(
                    TestUser.SELF.copy(
                        supportedProtocols = supportedProtocols
                    )
                )
        }

        fun withUpdateSupportedProtocolsSuccessful() = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSupportedProtocols)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetMigrationConfigurationSuccessful(migrationConfiguration: MLSMigrationModel) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getMigrationConfiguration)
                .whenInvoked()
                .thenReturn(Either.Right(migrationConfiguration))
        }

        fun withGetMigrationConfigurationFailing(failure: StorageFailure) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getMigrationConfiguration)
                .whenInvoked()
                .thenReturn(Either.Left(failure))
        }

        fun withGetSupportedProtocolsSuccessful(supportedProtocols: Set<SupportedProtocol>) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getSupportedProtocols)
                .whenInvoked()
                .thenReturn(Either.Right(supportedProtocols))
        }

        fun withGetSupportedProtocolsFailing(failure: StorageFailure) = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getSupportedProtocols)
                .whenInvoked()
                .thenReturn(Either.Left(failure))
        }

        fun withGetSelfClientsSuccessful(clients: List<Client>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::selfListOfClients)
                .whenInvoked()
                .thenReturn(Either.Right(clients))
        }

        fun arrange(block: (Arrangement.() -> Unit)) = apply(block).let {
            this to UpdateSelfUserSupportedProtocolsUseCaseImpl(
                clientRepository,
                userRepository,
                userConfigRepository,
                featureSupport,
                currentClientIdProvider,
                kaliumLogger
            )
        }

        companion object {
            val ONGOING_MIGRATION_CONFIGURATION = MLSMigrationModel(
                Instant.DISTANT_PAST,
                Instant.DISTANT_FUTURE,
                Status.ENABLED
            )
            val COMPLETED_MIGRATION_CONFIGURATION = ONGOING_MIGRATION_CONFIGURATION
                .copy(endTime = Instant.DISTANT_PAST)
            val DISABLED_MIGRATION_CONFIGURATION = ONGOING_MIGRATION_CONFIGURATION
                .copy(status = Status.DISABLED)
        }
    }
}
