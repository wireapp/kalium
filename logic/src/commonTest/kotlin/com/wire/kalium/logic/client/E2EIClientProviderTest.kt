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
package com.wire.kalium.logic.client

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class E2EIClientProviderTest {

    private lateinit var testDispatcher: TestDispatcher

    @BeforeTest
    fun setup() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun breakDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun givenMLSClientWithoutE2EI_whenGettingE2EIClient_callsNewRotateEnrollment() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewActivationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF.right())
                withE2EIEnabled(false)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verifySuspend {
            arrangement.userRepository.getSelfUser()
        }

        verifySuspend {
            arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any())
        }
    }

    @Test
    fun givenMLSClientWithE2EI_whenGettingE2EIClient_callsNewActivationEnrollment() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewRotationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF.right())
                withE2EIEnabled(true)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verifySuspend {
            arrangement.userRepository.getSelfUser()
        }

        verifySuspend {
            arrangement.mlsClientProvider.getMLSClient(any())
        }

        verifySuspend {
            arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any())
        }
    }

    @Test
    fun givenSelfUserNotFound_whenGettingE2EIClient_ReturnsError() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewRotationEnrollmentSuccessful()
                withSelfUser(StorageFailure.DataNotFound.left())
                withE2EIEnabled(true)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldFail()

        verifySuspend {
            arrangement.userRepository.getSelfUser()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsClientProvider.getMLSClient(any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any())
        }
    }

    @Test
    fun givenIsNewClientTrue_whenGettingE2EIClient_newAcmeEnrollmentCalled() = runTest {
        val supportedCipherSuite = SupportedCipherSuite(
            supported = listOf(
                CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256,
                CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
            ),
            default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
        )
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGettingCoreCryptoSuccessful()
                withGetNewAcmeEnrollmentSuccessful()
                withSelfUser(TestUser.SELF.right())
                withGetOrFetchMLSConfig(supportedCipherSuite)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID, isNewClient = true).shouldSucceed()

        verifySuspend {
            arrangement.userRepository.getSelfUser()
        }

        verifySuspend {
            arrangement.coreCryptoCentral.newAcmeEnrollment(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenDebugExpirationNotSet_whenGettingE2EIClient_usesDefault90Days() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewActivationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF.right())
                withE2EIEnabled(false)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verifySuspend {
            arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), eq(90.days))
        }
    }

    @Test
    fun givenDebugExpirationSet_whenGettingE2EIClient_usesOverrideExpiration() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement(testDispatcher.testKaliumDispatcher())
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewActivationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF.right())
                withE2EIEnabled(false)
            }

        e2eiClientProvider.setDebugCertificateExpirationOverride(360)
        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verifySuspend {
            arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), eq(360.seconds))
        }
    }

    @Test
    fun givenE2EICheckThrows_whenGettingEnrollment_thenReturnsFailureAndCanRetry() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGetMLSClientSuccessful()
            withSelfUser(TestUser.SELF.right())
            withE2EINewRotationEnrollmentSuccessful()
        }
        everySuspend { arrangement.mlsContext.isE2EIEnabled() } throws IllegalStateException("check failed")
        provider.getE2EIClient(TestClient.CLIENT_ID).shouldFail { assertIs<E2EIFailure.Generic>(it) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsClientProvider.getCoreCrypto(any()) }

        arrangement.withE2EIEnabled(true)
        provider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed { assertSame(arrangement.e2eiClient, it) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.notifier.onCryptoStateChanged(any()) }
    }

    @Test
    fun givenRotationThrows_whenGettingEnrollment_thenReturnsFailureWithoutCaching() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGetMLSClientSuccessful()
            withSelfUser(TestUser.SELF.right())
            withE2EIEnabled(true)
        }
        everySuspend { arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any()) } throws
            IllegalStateException("rotation failed")
        repeat(2) { provider.getE2EIClient(TestClient.CLIENT_ID).shouldFail { assertIs<E2EIFailure.Generic>(it) } }
        verifySuspend(VerifyMode.exactly(2)) { arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsClientProvider.getCoreCrypto(any()) }
    }

    @Test
    fun givenActivationThrows_whenGettingEnrollment_thenReturnsFailureWithoutCaching() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGetMLSClientSuccessful()
            withSelfUser(TestUser.SELF.right())
            withE2EIEnabled(false)
        }
        everySuspend { arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any()) } throws
            IllegalStateException("activation failed")
        repeat(2) { provider.getE2EIClient(TestClient.CLIENT_ID).shouldFail { assertIs<E2EIFailure.Generic>(it) } }
        verifySuspend(VerifyMode.exactly(2)) { arrangement.mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
    }

    @Test
    fun givenNewEnrollmentThrows_whenGettingEnrollment_thenReturnsFailureAndCanRetry() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGettingCoreCryptoSuccessful()
            withSelfUser(TestUser.SELF.right())
            withGetOrFetchMLSConfig(
                SupportedCipherSuite(
                    supported = listOf(CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256),
                    default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                )
            )
        }
        everySuspend { arrangement.coreCryptoCentral.newAcmeEnrollment(any(), any(), any(), any(), any(), any()) } throws
            IllegalStateException("new enrollment failed")
        provider.getE2EIClient(TestClient.CLIENT_ID, isNewClient = true).shouldFail { assertIs<E2EIFailure.Generic>(it) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }

        arrangement.withGetNewAcmeEnrollmentSuccessful()
        provider.getE2EIClient(TestClient.CLIENT_ID, isNewClient = true).shouldSucceed {
            assertSame(arrangement.e2eiClient, it)
        }
    }

    @Test
    fun givenMLSInitializationFails_whenGettingEnrollment_thenReturnsFailureWithoutFallingBackToNewClient() = runTest {
        val failure = MLSFailure.Generic(IllegalStateException("initialization failed"))
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withSelfUser(TestUser.SELF.right())
        }
        everySuspend { arrangement.mlsClientProvider.getMLSClient(any()) } returns failure.left()
        provider.getE2EIClient(TestClient.CLIENT_ID).shouldFail {
            assertEquals(E2EIFailure.GettingE2EIClient(failure), it)
        }
        verifySuspend(VerifyMode.not) { arrangement.mlsClientProvider.getCoreCrypto(any()) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
    }

    @Test
    fun givenMLSInitializationThrows_whenGettingEnrollment_thenReturnsFailureAndCanRetry() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withSelfUser(TestUser.SELF.right())
            withE2EIEnabled(true)
            withE2EINewRotationEnrollmentSuccessful()
        }
        everySuspend { arrangement.mlsClientProvider.getMLSClient(any()) } throws
            IllegalStateException("MLS initialization failed")

        provider.getE2EIClient(TestClient.CLIENT_ID).shouldFail { assertIs<E2EIFailure.Generic>(it) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsClientProvider.getCoreCrypto(any()) }

        arrangement.withGetMLSClientSuccessful()
        provider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed { assertSame(arrangement.e2eiClient, it) }
        verifySuspend(VerifyMode.exactly(2)) { arrangement.mlsClientProvider.getMLSClient(any()) }
    }

    @Test
    fun givenMLSInitializationCancelled_whenGettingEnrollment_thenCancellationPropagates() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withSelfUser(TestUser.SELF.right())
        }
        everySuspend { arrangement.mlsClientProvider.getMLSClient(any()) } throws CancellationException()

        assertFailsWith<CancellationException> { provider.getE2EIClient(TestClient.CLIENT_ID) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
        verifySuspend(VerifyMode.not) { arrangement.mlsClientProvider.getCoreCrypto(any()) }
    }

    @Test
    fun givenEnrollmentCancelled_whenGettingEnrollment_thenCancellationPropagates() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGetMLSClientSuccessful()
            withSelfUser(TestUser.SELF.right())
            withE2EIEnabled(true)
        }
        everySuspend {
            arrangement.mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any())
        } throws CancellationException()
        assertFailsWith<CancellationException> { provider.getE2EIClient(TestClient.CLIENT_ID) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
    }

    @Test
    fun givenNewEnrollmentCancelled_whenGettingEnrollment_thenCancellationPropagates() = runTest {
        val (arrangement, provider) = Arrangement(testDispatcher.testKaliumDispatcher()).arrange {
            withGettingCoreCryptoSuccessful()
            withSelfUser(TestUser.SELF.right())
            withGetOrFetchMLSConfig(
                SupportedCipherSuite(
                    supported = listOf(CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256),
                    default = CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256
                )
            )
        }
        everySuspend {
            arrangement.coreCryptoCentral.newAcmeEnrollment(any(), any(), any(), any(), any(), any())
        } throws CancellationException()
        assertFailsWith<CancellationException> { provider.getE2EIClient(TestClient.CLIENT_ID, isNewClient = true) }
        verifySuspend(VerifyMode.not) { arrangement.notifier.onCryptoStateChanged(any()) }
    }

    private class Arrangement(
        private val testDispatcher: KaliumDispatcher
    ) : E2EIClientProviderArrangement by E2EIClientProviderArrangementImpl() {
        private lateinit var e2eiClientProvider: E2EIClientProvider
        val notifier = mock<CryptoStateChangeHookNotifier>(MockMode.autoUnit)

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, E2EIClientProvider> {
            block()
            e2eiClientProvider = EI2EIClientProviderImpl(
                currentClientIdProvider,
                mlsClientProvider,
                userRepository,
                TestUser.SELF.id,
                notifier,
                testDispatcher
            )

            return this to e2eiClientProvider
        }

        override suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite) {
            everySuspend { mlsClientProvider.getOrFetchMLSConfig() } returns result.right()
        }
    }
}
