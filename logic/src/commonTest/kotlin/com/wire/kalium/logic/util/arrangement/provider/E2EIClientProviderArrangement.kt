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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.MLSCiphersuite
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock

interface E2EIClientProviderArrangement {

    val mlsClientProvider: MLSClientProvider
    val mlsClient: MLSClient
    val coreCryptoCentral: CoreCryptoCentral
    val e2eiClient: E2EIClient
    val userRepository: UserRepository
    val currentClientIdProvider: CurrentClientIdProvider
    val mlsContext: MlsCoreCryptoContext

    suspend fun withGettingCoreCryptoSuccessful()

    suspend fun withGetNewAcmeEnrollmentSuccessful()

    suspend fun withGetMLSClientSuccessful()

    suspend fun withE2EINewActivationEnrollmentSuccessful()

    suspend fun withE2EINewRotationEnrollmentSuccessful()

    suspend fun withE2EIEnabled(isEnabled: Boolean)

    suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>)

    suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite)
}

class E2EIClientProviderArrangementImpl : E2EIClientProviderArrangement {
    override val mlsClientProvider: MLSClientProvider = mock(MLSClientProvider::class)
    override val e2eiClient: E2EIClient = mock(E2EIClient::class)
    override val userRepository: UserRepository = mock(UserRepository::class)
    override val currentClientIdProvider = mock(CurrentClientIdProvider::class)
    override val coreCryptoCentral = mock(CoreCryptoCentral::class)
    override val mlsContext: MlsCoreCryptoContext = mock(MlsCoreCryptoContext::class)
    override val mlsClient: MLSClient = DummyMLSClient(mlsContext)

    override suspend fun withGettingCoreCryptoSuccessful() {
        coEvery {
            mlsClientProvider.getCoreCrypto(any())
        }.returns(Either.Right(coreCryptoCentral))
    }

    override suspend fun withGetNewAcmeEnrollmentSuccessful() {
        coEvery {
            coreCryptoCentral.newAcmeEnrollment(any(), any(), any(), any(), any(), any())
        }.returns(e2eiClient)
    }

    override suspend fun withGetMLSClientSuccessful() {
        coEvery {
            mlsClientProvider.getMLSClient(any())
        }.returns(Either.Right(mlsClient))
    }

    override suspend fun withE2EINewActivationEnrollmentSuccessful() {
        coEvery {
            mlsContext.e2eiNewActivationEnrollment(any(), any(), any(), any())
        }.returns(e2eiClient)
    }
    override suspend fun withE2EINewRotationEnrollmentSuccessful() {
        coEvery {
            mlsContext.e2eiNewRotateEnrollment(any(), any(), any(), any())
        }.returns(e2eiClient)
    }

    override suspend fun withE2EIEnabled(isEnabled: Boolean) {
        coEvery {
            mlsContext.isE2EIEnabled()
        }.returns(isEnabled)
    }

    override suspend fun withSelfUser(result: Either<StorageFailure, SelfUser>) {
        coEvery {
            userRepository.getSelfUser()
        }.returns(result)
    }

    override suspend fun withGetOrFetchMLSConfig(result: SupportedCipherSuite) {
        coEvery {
            mlsClientProvider.getOrFetchMLSConfig()
        }.returns(result.right())
    }

}

class DummyMLSClient(
    private val context: MlsCoreCryptoContext
) : MLSClient {
    override fun getDefaultCipherSuite(): MLSCiphersuite {
        TODO("Not yet implemented")
    }

    override suspend fun close() {
        TODO("Not yet implemented")
    }

    override suspend fun getPublicKey(): Pair<ByteArray, MLSCiphersuite> {
        TODO("Not yet implemented")
    }

    override suspend fun <R> transaction(name: String, block: suspend (context: MlsCoreCryptoContext) -> R): R {
        return block(context)
    }

}

