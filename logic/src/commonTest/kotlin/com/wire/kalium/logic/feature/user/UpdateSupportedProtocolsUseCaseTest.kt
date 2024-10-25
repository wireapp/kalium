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

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.featureConfig.MLSMigrationModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
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
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test

class UpdateSupportedProtocolsUseCaseTest {

    @Test
    fun givenMlsFeatureDisabledAndMlsKeyPresent_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withIsMLSSupported(false)
                withKeyForCipherSuite()
            }

        useCase.invoke().shouldSucceed()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(any())
        }.wasNotInvoked()
    }


    @Test
    fun givenMlsFeatureEnabledAndMlsKeyNotPresent_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withIsMLSSupported(true)
                withoutKeyForCipherSuite()
            }

        useCase.invoke().shouldSucceed()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenSupportedProtocolsHasNotChanged_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful(supportedProtocols = setOf(SupportedProtocol.PROTEUS))
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenProteusAsSupportedProtocol_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.PROTEUS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.PROTEUS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupportedButMigrationHasNotEnded_whenInvokingUseCase_thenProteusIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.PROTEUS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenProteusIsNotSupported_whenInvokingUseCase_thenProteusIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(COMPLETED_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { !it.contains(SupportedProtocol.PROTEUS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllActiveClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
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

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAnInactiveClientIsNotMlsCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
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

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndAllActiveClientsAreNotCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful()
                withGetSupportedProtocolsSuccessful(setOf(SupportedProtocol.MLS))
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(
                    clients = listOf(
                        TestClient.CLIENT.copy(isMLSCapable = true, lastActive = Clock.System.now()),
                        TestClient.CLIENT.copy(isMLSCapable = false, lastActive = Clock.System.now())
                    )
                )
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { !it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsSupportedAndMigrationHasEnded_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
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

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMigrationIsMissingAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
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

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsIsNotSupportedAndAllClientsAreCapable_whenInvokingUseCase_thenMlsIsNotIncluded() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
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

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(matches { !it.contains(SupportedProtocol.MLS) })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSupportedProtocolsAreNotConfigured_whenInvokingUseCase_thenSupportedProtocolsAreNotUpdated() = runTest {
        val (arrangement, useCase) = Arrangement()
            .arrange {
                withCurrentClientIdSuccess(ClientId("1"))
                withIsMLSSupported(true)
                withKeyForCipherSuite()
                withGetSelfUserSuccessful(supportedProtocols = setOf(SupportedProtocol.PROTEUS))
                withGetSupportedProtocolsFailing(StorageFailure.DataNotFound)
                withGetMigrationConfigurationSuccessful(ONGOING_MIGRATION_CONFIGURATION)
                withGetSelfClientsSuccessful(clients = emptyList())
                withUpdateSupportedProtocolsSuccessful()
            }

        useCase.invoke().shouldSucceed()

        coVerify {
            arrangement.userRepository.updateSupportedProtocols(any())
        }.wasNotInvoked()
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

        @Mock
        val mlsPublicKeysRepository = mock(MLSPublicKeysRepository::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val mlsClient = mock(MLSClient::class)

        private var kaliumLogger = KaliumLogger.disabled()

        fun withIsMLSSupported(supported: Boolean) = apply {
            every {
                featureSupport.isMLSSupported
            }.returns(supported)
        }

        suspend fun withKeyForCipherSuite() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
            every {
                mlsClient.getDefaultCipherSuite()
            }.returns(CIPHER_SUITE.tag.toUShort())
            coEvery {
                mlsPublicKeysRepository.getKeyForCipherSuite(any())
            }.returns(Either.Right(CRYPTO_MLS_PUBLIC_KEY))
        }

        suspend fun withoutKeyForCipherSuite() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
            every {
                mlsClient.getDefaultCipherSuite()
            }.returns(CIPHER_SUITE.tag.toUShort())
            coEvery {
                mlsPublicKeysRepository.getKeyForCipherSuite(any())
            }.returns(Either.Left(MLSFailure.Generic(IllegalStateException("No key found for cipher suite"))))
        }

        suspend fun withGetSelfUserSuccessful(supportedProtocols: Set<SupportedProtocol>? = null) = apply {
            coEvery { userRepository.getSelfUser() }
                .returns(TestUser.SELF.copy(supportedProtocols = supportedProtocols))
        }

        suspend fun withUpdateSupportedProtocolsSuccessful() = apply {
            coEvery {
                userRepository.updateSupportedProtocols(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetMigrationConfigurationSuccessful(migrationConfiguration: MLSMigrationModel) = apply {
            coEvery {
                userConfigRepository.getMigrationConfiguration()
            }.returns(Either.Right(migrationConfiguration))
        }

        suspend fun withGetMigrationConfigurationFailing(failure: StorageFailure) = apply {
            coEvery {
                userConfigRepository.getMigrationConfiguration()
            }.returns(Either.Left(failure))
        }

        suspend fun withGetSupportedProtocolsSuccessful(supportedProtocols: Set<SupportedProtocol>) = apply {
            coEvery {
                userConfigRepository.getSupportedProtocols()
            }.returns(Either.Right(supportedProtocols))
        }

        suspend fun withGetSupportedProtocolsFailing(failure: StorageFailure) = apply {
            coEvery {
                userConfigRepository.getSupportedProtocols()
            }.returns(Either.Left(failure))
        }

        suspend fun withGetSelfClientsSuccessful(clients: List<Client>) = apply {
            coEvery {
                clientRepository.selfListOfClients()
            }.returns(Either.Right(clients))
        }

        suspend fun arrange(block: suspend (Arrangement.() -> Unit)) = run {
            block()
            this to UpdateSelfUserSupportedProtocolsUseCaseImpl(
                clientRepository,
                userRepository,
                userConfigRepository,
                featureSupport,
                currentClientIdProvider,
                mlsClientProvider,
                mlsPublicKeysRepository,
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
            val CIPHER_SUITE = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            val MLS_PUBLIC_KEY = MLSPublicKeys(
                removal = mapOf(
                    "ed25519" to "gRNvFYReriXbzsGu7zXiPtS8kaTvhU1gUJEV9rdFHVw="
                )
            )
            val CRYPTO_MLS_PUBLIC_KEY: ByteArray = MLS_PUBLIC_KEY.removal?.get("ed25519")!!.decodeBase64Bytes()
        }
    }
}
