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

import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.EI2EIClientProviderImpl
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.E2EIClientProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class E2EIClientProviderTest {
    @Test
    fun givenMLSClientWithoutE2EI_whenGettingE2EIClient_callsNewRotateEnrollment() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement()
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewActivationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF)
                withE2EIEnabled(false)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::getSelfUser)
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewActivationEnrollment)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewRotateEnrollment)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenMLSClientWithE2EI_whenGettingE2EIClient_callsNewActivationEnrollment() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement()
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewRotationEnrollmentSuccessful()
                withSelfUser(TestUser.SELF)
                withE2EIEnabled(true)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::getSelfUser)
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClientProvider)
            .suspendFunction(arrangement.mlsClientProvider::getMLSClient)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewRotateEnrollment)
            .with(any(), any(), any())
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewActivationEnrollment)
            .with(any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenSelfUserNotFound_whenGettingE2EIClient_ReturnsError() = runTest {
        val (arrangement, e2eiClientProvider) = Arrangement()
            .arrange {
                withGetMLSClientSuccessful()
                withE2EINewRotationEnrollmentSuccessful()
                withSelfUser(null)
                withE2EIEnabled(true)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID).shouldFail()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::getSelfUser)
            .wasInvoked(exactly = once)

        verify(arrangement.mlsClientProvider)
            .suspendFunction(arrangement.mlsClientProvider::getMLSClient)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewRotateEnrollment)
            .with(any(), any(), any())
            .wasNotInvoked()

        verify(arrangement.mlsClient)
            .suspendFunction(arrangement.mlsClient::e2eiNewActivationEnrollment)
            .with(any(), any(), any())
            .wasNotInvoked()
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
        val (arrangement, e2eiClientProvider) = Arrangement()
            .arrange {
                withGettingCoreCryptoSuccessful()
                withGetNewAcmeEnrollmentSuccessful()
                withSelfUser(TestUser.SELF)
                withGetOrFetchMLSConfig(supportedCipherSuite)
            }

        e2eiClientProvider.getE2EIClient(TestClient.CLIENT_ID,isNewClient = true).shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::getSelfUser)
            .wasInvoked(exactly = once)

        verify(arrangement.coreCryptoCentral)
            .suspendFunction(arrangement.coreCryptoCentral::newAcmeEnrollment)
            .with(any(), any(), any(), any(), any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement :
        E2EIClientProviderArrangement by E2EIClientProviderArrangementImpl() {
        private lateinit var e2eiClientProvider: E2EIClientProvider

        fun arrange(block: Arrangement.() -> Unit): Pair<Arrangement, E2EIClientProvider> {
            apply(block)
            e2eiClientProvider = EI2EIClientProviderImpl(
                currentClientIdProvider,
                mlsClientProvider,
                userRepository
            )

            return this to e2eiClientProvider
        }
    }
}
