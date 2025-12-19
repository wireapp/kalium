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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

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

    private class Arrangement(
        private val testDispatcher: KaliumDispatcher
    ) : E2EIClientProviderArrangement by E2EIClientProviderArrangementMokkeryImpl() {
        private lateinit var e2eiClientProvider: E2EIClientProvider

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, E2EIClientProvider> {
            block()
            e2eiClientProvider = EI2EIClientProviderImpl(
                currentClientIdProvider,
                mlsClientProvider,
                userRepository,
                testDispatcher
            )

            return this to e2eiClientProvider
        }

        override suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite) {
            everySuspend { mlsClientProvider.getOrFetchMLSConfig() } returns result.right()
        }
    }
}
