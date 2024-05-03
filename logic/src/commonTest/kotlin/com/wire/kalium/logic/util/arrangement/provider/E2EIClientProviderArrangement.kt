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
package com.wire.kalium.logic.util.arrangement.provider

import com.wire.kalium.cryptography.CoreCryptoCentral
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

interface E2EIClientProviderArrangement {
    @Mock
    val mlsClientProvider: MLSClientProvider

    @Mock
    val mlsClient: MLSClient

    @Mock
    val coreCryptoCentral: CoreCryptoCentral

    @Mock
    val e2eiClient: E2EIClient

    @Mock
    val userRepository: UserRepository

    @Mock
    val currentClientIdProvider: CurrentClientIdProvider

    suspend fun withGettingCoreCryptoSuccessful()

    suspend fun withGetNewAcmeEnrollmentSuccessful()

    suspend fun withGetMLSClientSuccessful()

    suspend fun withE2EINewActivationEnrollmentSuccessful()

    suspend fun withE2EINewRotationEnrollmentSuccessful()

    suspend fun withE2EIEnabled(isEnabled: Boolean)

    suspend fun withSelfUser(selfUser: SelfUser?)
}

class E2EIClientProviderArrangementImpl : E2EIClientProviderArrangement {
    override val mlsClientProvider: MLSClientProvider = mock(MLSClientProvider::class)
    override val mlsClient: MLSClient = mock(MLSClient::class)
    override val e2eiClient: E2EIClient = mock(E2EIClient::class)
    override val userRepository: UserRepository = mock(UserRepository::class)
    override val currentClientIdProvider = mock(CurrentClientIdProvider::class)
    override val coreCryptoCentral = mock(CoreCryptoCentral::class)

    override suspend fun withGettingCoreCryptoSuccessful() {
        coEvery {
            mlsClientProvider.getCoreCrypto(any())
        }.returns(Either.Right(coreCryptoCentral))
    }

    override suspend fun withGetNewAcmeEnrollmentSuccessful() {
        coEvery {
            coreCryptoCentral.newAcmeEnrollment(any(), any(), any(), any(), any())
        }.returns(e2eiClient)
    }

    override suspend fun withGetMLSClientSuccessful() {
        coEvery {
            mlsClientProvider.getMLSClient(any())
        }.returns(Either.Right(mlsClient))
    }

    override suspend fun withE2EINewActivationEnrollmentSuccessful() {
        coEvery {
            mlsClient.e2eiNewActivationEnrollment(any(), any(), any(), any())
        }.returns(e2eiClient)
    }
    override suspend fun withE2EINewRotationEnrollmentSuccessful() {
        coEvery {
            mlsClient.e2eiNewRotateEnrollment(any(), any(), any(), any())
        }.returns(e2eiClient)
    }

    override suspend fun withE2EIEnabled(isEnabled: Boolean) {
        coEvery {
            mlsClient.isE2EIEnabled()
        }.returns(isEnabled)
    }

    override suspend fun withSelfUser(selfUser: SelfUser?) {
        coEvery {
            userRepository.getSelfUser()
        }.returns(selfUser)
    }

}
