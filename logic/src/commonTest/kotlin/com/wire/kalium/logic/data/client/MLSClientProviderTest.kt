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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.featureConfig.FeatureConfigTest
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.featureConfig.MLSModel
import com.wire.kalium.logic.data.featureConfig.Status
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.FileUtil
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.util.reflect.instanceOf
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class MLSClientProviderTest {

    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @Test
    fun givenMlsConfigIsnotStoredLocally_whenGetMlsClient_thenMlsFetchMlsConfigFromRemote() = runTest(testDispatchers.io) {
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

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.isMLSEnabled() }

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.getSupportedCipherSuite() }

        verifySuspend(VerifyMode.exactly(1)) { arrangement.featureConfigRepository.getFeatureConfigs() }
    }

    @Test
    fun givenMlsConfigIsStoredLocally_whenGetMlsClient_thenMlsFetchMlsConfigFromLocal() = runTest(testDispatchers.io) {
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

        verifySuspend(VerifyMode.exactly(1)) { arrangement.userConfigRepository.isMLSEnabled() }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigRepository.getSupportedCipherSuite()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.featureConfigRepository.getFeatureConfigs()
        }
    }

    @Test
    fun givenMLSDisabledWhenGetOrFetchMLSConfigIsCalledThenDoNotCallGetSupportedCipherSuiteOrGetFeatureConfigs() =
        runTest(testDispatchers.io) {
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

            verifySuspend(VerifyMode.not) { arrangement.userConfigRepository.getSupportedCipherSuite() }

            verifySuspend(VerifyMode.not) { arrangement.featureConfigRepository.getFeatureConfigs() }
        }

    @Test
    fun givenNoClient_whenExportingDB_thenReturnsFailure() = runTest(testDispatchers.io) {
        val (_, mlsClientProvider) = Arrangement(this).arrange {
            withCurrentClientIdFailure(StorageFailure.DataNotFound)
        }

        mlsClientProvider.exportCryptoDB("/tmp/keystore").shouldFail {
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    @Test
    fun givenCoreClientNotInitialized_whenExportingDB_thenReturnsDataNotFound() = runTest(testDispatchers.io) {
        val (_, mlsClientProvider) = Arrangement(this).arrange {
            withCurrentClientIdSuccess(TestClient.CLIENT_ID)
            withPassphraseStorage()
        }

        mlsClientProvider.exportCryptoDB("/tmp/keystore").shouldFail {
            // CoreCryptoCentral was never initialized, so should return DataNotFound
            assertIs<StorageFailure.DataNotFound>(it)
        }
    }

    private inner class Arrangement(
        val processingScope: CoroutineScope
    ) {

        val rootKeyStorePath: String = "rootKeyStorePath"
        val userId: UserId = UserId("userId", "domain")
        val userConfigRepository = mock<UserConfigRepository>()
        val featureConfigRepository = mock<FeatureConfigRepository>()
        val currentClientIdProvider = mock<CurrentClientIdProvider>()
        val passphraseStorage = mock<PassphraseStorage>(mode = MockMode.autoUnit)
        val mlsTransportProvider = mock<MLSTransportProvider>(mode = MockMode.autoUnit)
        val epochChangesObserver = mock<EpochChangesObserver>(mode = MockMode.autoUnit)

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

        suspend fun withGetSupportedCipherSuitesReturning(result: com.wire.kalium.common.functional.Either<StorageFailure, SupportedCipherSuite>) = apply {
            everySuspend { userConfigRepository.getSupportedCipherSuite() }.returns(result)
        }

        suspend fun withGetFeatureConfigsReturning(result: com.wire.kalium.common.functional.Either<NetworkFailure, com.wire.kalium.logic.data.featureConfig.FeatureConfigModel>) = apply {
            everySuspend { featureConfigRepository.getFeatureConfigs() }.returns(result)
        }

        suspend fun withGetMLSEnabledReturning(result: com.wire.kalium.common.functional.Either<StorageFailure, Boolean>) = apply {
            everySuspend { userConfigRepository.isMLSEnabled() }.returns(result)
        }

        suspend fun withCurrentClientIdFailure(error: CoreFailure) = apply {
            everySuspend { currentClientIdProvider.invoke() }.returns(error.left())
        }

        suspend fun withCurrentClientIdSuccess(clientId: ClientId) = apply {
            everySuspend { currentClientIdProvider.invoke() }.returns(clientId.right())
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
                dispatchers = testDispatchers,
                processingScope = processingScope,
            )
        }
    }
}
