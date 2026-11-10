/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.crypto.CoreCryptoException
import com.wire.crypto.MlsException
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.ProteusClient
import com.wire.kalium.cryptography.ProteusCoreCryptoContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.featureConfig.FeatureConfigRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
import com.wire.kalium.logic.sync.incremental.EventStreamData
import com.wire.kalium.logic.sync.incremental.IncrementalSyncWorkerImpl
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dbPassphrase.PassphraseStorage
import com.wire.kalium.util.InternalCryptoAccess
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import java.nio.file.Files
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

class MLSClientProviderInitializationTest {

    @Test
    fun givenInitializationFails_whenRequestedAgain_thenRetriesAndCachesOnlySuccess() = runTest {
        withProvider { coreCrypto, mlsClient, provider ->
            val failures = listOf(
                CoreCryptoException.Mls(MlsException.Other("credential mismatch")),
                IllegalStateException("initialization failed")
            )
            failures.forEach { failure ->
                everySuspend { coreCrypto.mlsClient(any(), any(), any(), any(), any(), any()) } throws failure
                provider.getMLSClient(TestClient.CLIENT_ID).shouldFail { assertIs<MLSFailure>(it) }
                assertFalse(provider.isMLSClientInitialised())
            }

            everySuspend {
                coreCrypto.mlsClient(any(), any(), any(), any(), any(), any())
            } returns mlsClient
            provider.getMLSClient(TestClient.CLIENT_ID).shouldSucceed { assertSame(mlsClient, it) }
            provider.getMLSClient(TestClient.CLIENT_ID).shouldSucceed { assertSame(mlsClient, it) }
            assertTrue(provider.isMLSClientInitialised())
            verifySuspend(VerifyMode.exactly(3)) { coreCrypto.mlsClient(any(), any(), any(), any(), any(), any()) }
        }
    }

    @Test
    @OptIn(InternalCryptoAccess::class)
    fun givenInitializationFails_whenReceivingMLSWelcome_thenEventRemainsUnprocessed() = runTest {
        withProvider { coreCrypto, _, provider ->
            val proteusProvider = mock<ProteusClientProvider>()
            val proteusClient = mock<ProteusClient>()
            everySuspend { proteusProvider.getOrError() } returns proteusClient.right()
            val proteusContext = mock<ProteusCoreCryptoContext>()
            everySuspend {
                proteusClient.transaction(any(), any<suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, List<String>>>())
            } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val block = invocation.args[1] as suspend (ProteusCoreCryptoContext) -> Either<CoreFailure, List<String>>
                block(proteusContext)
            }
            val transactions = CryptoTransactionProviderImpl(provider, proteusProvider)
            val gatherer = mock<EventGatherer>()
            val processor = mock<EventProcessor>()
            val events = mock<EventRepository>()
            val envelope = TestEvent.newMLSWelcomeEvent().wrapInEnvelope()
            everySuspend { gatherer.gatherEvents() } returns flowOf(EventStreamData.NewEvents(listOf(envelope)))
            everySuspend { processor.processEvent(any(), any()) } returns envelope.event.id.right()
            everySuspend { processor.flushPendingSideEffects() } returns Unit.right()
            everySuspend { events.setEventsAsProcessed(any()) } returns Unit.right()
            val database = TestUserDatabase(UserIDEntity("mls-init-test", "domain"), StandardTestDispatcher(testScheduler))
            try {
                val worker = IncrementalSyncWorkerImpl(gatherer, processor, transactions, database.builder, events)
                val failures = listOf(
                    CoreCryptoException.Mls(MlsException.Other("credential mismatch")),
                    IllegalStateException("MLS initialization failed")
                )
                failures.forEach { failure ->
                    everySuspend { coreCrypto.mlsClient(any(), any(), any(), any(), any(), any()) } throws failure

                    val syncFailure = assertFailsWith<KaliumSyncException> { worker.processEventsFlow().collect() }
                    assertIs<MLSFailure>(syncFailure.coreFailureCause)
                    assertFalse(provider.isMLSClientInitialised())
                }
                verifySuspend(VerifyMode.not) { processor.processEvent(any(), any()) }
                verifySuspend(VerifyMode.not) { events.setEventsAsProcessed(any()) }
            } finally {
                database.builder.nuke()
                database.delete()
            }
        }
    }

    @Test
    fun givenInitializationCancelled_whenGettingClient_thenCancellationPropagatesAndCacheStaysEmpty() = runTest {
        withProvider { coreCrypto, _, provider ->
            everySuspend {
                coreCrypto.mlsClient(any(), any(), any(), any(), any(), any())
            } throws CancellationException()

            assertFailsWith<CancellationException> { provider.getMLSClient(TestClient.CLIENT_ID) }
            assertFalse(provider.isMLSClientInitialised())
        }
    }

    @Test
    fun givenCertificateInitializationFails_whenRetried_thenCachesOnlySuccessfulClient() = runTest {
        withProvider { coreCrypto, mlsClient, provider ->
            val enrollment = mock<E2EIClient>()
            everySuspend { coreCrypto.mlsClient(any<E2EIClient>(), any(), any(), any(), any(), any(), any()) } throws
                IllegalStateException("certificate initialization failed")

            provider.initMLSClientWithCertificate(enrollment, "certificate", TestClient.CLIENT_ID).shouldFail {
                assertIs<E2EIFailure.Generic>(it)
            }
            assertFalse(provider.isMLSClientInitialised())

            everySuspend {
                coreCrypto.mlsClient(any<E2EIClient>(), any(), any(), any(), any(), any(), any())
            } returns mlsClient
            provider.initMLSClientWithCertificate(enrollment, "certificate", TestClient.CLIENT_ID).shouldSucceed()
            assertTrue(provider.isMLSClientInitialised())
        }
    }

    private suspend fun TestScope.withProvider(
        block: suspend (CoreCryptoCentral, MLSClient, MLSClientProvider) -> Unit
    ) {
        val directory = Files.createTempDirectory("mls-provider-test").toFile()
        try {
            val coreCrypto = mock<CoreCryptoCentral>()
            val mlsClient = mock<MLSClient>()
            val config = mock<UserConfigRepository>()
            val passphraseStorage = mock<PassphraseStorage>(MockMode.autoUnit)
            every { passphraseStorage.getPassphrase(any()) } returns Base64.encode(ByteArray(32))
            everySuspend { config.isMLSEnabled() } returns true.right()
            everySuspend { config.getSupportedCipherSuite() } returns SupportedCipherSuite(
                supported = listOf(CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256),
                default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            ).right()
            val currentClientIdProvider = mock<CurrentClientIdProvider>()
            everySuspend { currentClientIdProvider() } returns TestClient.CLIENT_ID.right()
            val provider = MLSClientProviderImpl(
                rootKeyStorePath = directory.absolutePath,
                userId = UserId("userId", "domain"),
                currentClientIdProvider = currentClientIdProvider,
                passphraseStorage = passphraseStorage,
                userConfigRepository = config,
                featureConfigRepository = mock<FeatureConfigRepository>(),
                mlsTransportProvider = mock<MLSTransportProvider>(MockMode.autoUnit),
                epochObserver = mock<EpochChangesObserver>(MockMode.autoUnit),
                dispatchers = StandardTestDispatcher(testScheduler).testKaliumDispatcher(),
                processingScope = this
            )
            // Seed the existing cache only in JVM tests; production keeps its direct factory call.
            MLSClientProviderImpl::class.java.getDeclaredField("coreCryptoCentral").apply {
                isAccessible = true
                set(provider, coreCrypto)
            }
            block(coreCrypto, mlsClient, provider)
        } finally {
            directory.deleteRecursively()
        }
    }
}
