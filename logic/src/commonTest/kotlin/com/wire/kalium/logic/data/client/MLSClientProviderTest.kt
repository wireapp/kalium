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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.util.arrangement.repository.FeatureConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.FeatureConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import io.ktor.util.reflect.instanceOf
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MLSClientProviderTest {

    @Test
    fun givenMlsConfigIsnotStoredLocally_whenGetMlsClient_thenMlsFetchMlsConfigFromRemote() = runTest {
        val expected = MLSModel(
            defaultProtocol = SupportedProtocol.MLS,
            supportedProtocols = setOf(SupportedProtocol.MLS, SupportedProtocol.PROTEUS),
            supportedCipherSuite = SupportedCipherSuite(
                supported = listOf(
                    CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
                    CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                ),
                default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            ),
            status = Status.ENABLED
        )

        val (arrangement, mlsClientProvider) = Arrangement(this).arrange {
            withGetSupportedCipherSuitesReturning(StorageFailure.DataNotFound.left())
            withGetFeatureConfigsReturning(FeatureConfigTest.newModel(mlsModel = expected).right())
            withGetMLSEnabledReturning(true.right())
        }

        mlsClientProvider.getOrFetchMLSConfig().shouldSucceed {
            assertEquals(expected.supportedCipherSuite, it)
        }

        coVerify { arrangement.userConfigRepository.isMLSEnabled() }
            .wasInvoked(exactly = once)

        coVerify { arrangement.userConfigRepository.getSupportedCipherSuite() }
            .wasInvoked(exactly = once)

        coVerify { arrangement.featureConfigRepository.getFeatureConfigs() }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMlsConfigIsStoredLocally_whenGetMlsClient_thenMlsFetchMlsConfigFromLocal() = runTest {
        val expected = SupportedCipherSuite(
            supported = listOf(
                CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
                CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
            ),
            default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        )

        val (arrangement, mlsClientProvider) = Arrangement(this).arrange {
            withGetSupportedCipherSuitesReturning(expected.right())
            withGetMLSEnabledReturning(true.right())
            withGetFeatureConfigsReturning(FeatureConfigTest.newModel().right())
        }

        mlsClientProvider.getOrFetchMLSConfig().shouldSucceed {
            assertEquals(expected, it)
        }

        coVerify { arrangement.userConfigRepository.isMLSEnabled() }
            .wasInvoked(exactly = once)

        coVerify {
            arrangement.userConfigRepository.getSupportedCipherSuite()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.featureConfigRepository.getFeatureConfigs()
        }.wasNotInvoked()
    }

    @Test
    fun givenMLSDisabledWhenGetOrFetchMLSConfigIsCalledThenDoNotCallGetSupportedCipherSuiteOrGetFeatureConfigs() = runTest {
        // given
        val (arrangement, mlsClientProvider) = Arrangement(this).arrange {
            withGetMLSEnabledReturning(false.right())
            withGetSupportedCipherSuitesReturning(
                SupportedCipherSuite(
                    supported = listOf(
                        CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
                        CipherSuite.MLS_256_DHKEMP384_AES256GCM_SHA384_P384
                    ),
                    default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                ).right()
            )
        }

        // when
        val result = mlsClientProvider.getOrFetchMLSConfig()

        // then
        result.shouldFail {
            it.instanceOf(CoreFailure.Unknown::class)
        }

        coVerify { arrangement.userConfigRepository.getSupportedCipherSuite() }
            .wasNotInvoked()

        coVerify { arrangement.featureConfigRepository.getFeatureConfigs() }
            .wasNotInvoked()
    }

    private class Arrangement(
        val processingScope: CoroutineScope
    ) : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        FeatureConfigRepositoryArrangement by FeatureConfigRepositoryArrangementImpl() {

        val rootKeyStorePath: String = "rootKeyStorePath"
        val userId: UserId = UserId("userId", "domain")
        val currentClientIdProvider: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)
        val passphraseStorage: PassphraseStorage = mock(PassphraseStorage::class)
        val mlsTransportProvider: MLSTransportProvider = mock(MLSTransportProvider::class)
        val epochChangesObserver: EpochChangesObserver = mock(EpochChangesObserver::class)
        val coreCryptoMigrationRecoveryHandler = mock(CoreCryptoMigrationRecoveryHandler::class)

        fun arrange(block: suspend Arrangement.() -> Unit) = apply { runBlocking { block() } }.let {
            this to MLSClientProviderImpl(
                rootKeyStorePath = rootKeyStorePath,
                userId = userId,
                currentClientIdProvider = currentClientIdProvider,
                passphraseStorage = passphraseStorage,
                userConfigRepository = userConfigRepository,
                featureConfigRepository = featureConfigRepository,
                mlsTransportProvider = mlsTransportProvider,
                epochObserver = epochChangesObserver,
                processingScope = processingScope,
                coreCryptoMigrationRecoveryHandler = coreCryptoMigrationRecoveryHandler,
            )
        }
    }
}
