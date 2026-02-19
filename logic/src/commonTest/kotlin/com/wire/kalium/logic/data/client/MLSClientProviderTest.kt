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
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CurrentClientIdProviderArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.FeatureConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.FeatureConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import io.ktor.util.reflect.instanceOf
import io.mockative.any
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    @Test
    @IgnoreIOS
    fun givenValidClient_whenExportingDB_thenReturnsExportData() = runTest {
        val (arrangement, mlsClientProvider) = Arrangement(this).arrange {
            withCurrentClientIdSuccess(TestClient.CLIENT_ID)
            withCoreCryptoDatabaseExists()
            withPassphraseStorage()
        }

        mlsClientProvider.exportCryptoDB().shouldSucceed {
            assertEquals(
                "${arrangement.rootKeyStorePath}/${TestClient.CLIENT_ID.value}/keystore/keystore",
                it.dbPath
            )
            assertEquals(TestClient.CLIENT_ID, it.clientId)
            assertEquals(
                Base64.encode(ByteArray(32) { 0xAB.toByte() }),
                Base64.encode(it.passphrase)
            )
        }
    }

    @Test
    fun givenNoClient_whenExportingDB_thenReturnsFailure() = runTest {
        val (_, mlsClientProvider) = Arrangement(this).arrange {
            withCurrentClientIdFailure(StorageFailure.DataNotFound)
        }

        mlsClientProvider.exportCryptoDB().shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    @Test
    fun givenDBDoesNotExist_whenExportingDB_thenReturnsDataNotFound() = runTest {
        val (arrangement, mlsClientProvider) = Arrangement(this).arrange {
            withCurrentClientIdSuccess(TestClient.CLIENT_ID)
            withPassphraseStorage()
            withCoreCryptoDatabaseDoesNotExists()
        }

        mlsClientProvider.exportCryptoDB().shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    private class Arrangement(
        val processingScope: CoroutineScope
    ) : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        FeatureConfigRepositoryArrangement by FeatureConfigRepositoryArrangementImpl(),
        CurrentClientIdProviderArrangement by CurrentClientIdProviderArrangementImpl() {

        val rootKeyStorePath: String = "rootKeyStorePath"
        val userId: UserId = UserId("userId", "domain")
        val passphraseStorage: PassphraseStorage = mock(PassphraseStorage::class)
        val mlsTransportProvider: MLSTransportProvider = mock(MLSTransportProvider::class)
        val epochChangesObserver: EpochChangesObserver = mock(EpochChangesObserver::class)

        init {
            withCoreCryptoDatabaseDoesNotExists()
        }

        fun withCoreCryptoDatabaseDoesNotExists() {
            val clientId = TestClient.CLIENT_ID
            val location = "$rootKeyStorePath/${clientId.value}"
            val rootDir = "$location/keystore"

            FileUtil.deleteDirectory(rootDir)
        }

        /**
         * Create the directory structure that exportCryptoDB expects
         */
        fun withCoreCryptoDatabaseExists() {
            val clientId = TestClient.CLIENT_ID
            val location = "$rootKeyStorePath/${clientId.value}"
            val rootDir = "$location/keystore"
            val dbPath = "$rootDir/keystore"

            FileUtil.mkDirs(dbPath)
        }

        fun withPassphraseStorage() {
            val passphraseBase64 = Base64.encode(ByteArray(32) { 0xAB.toByte() })
            every { passphraseStorage.getPassphrase(any()) }.returns(passphraseBase64)
        }

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
            )
        }
    }
}
