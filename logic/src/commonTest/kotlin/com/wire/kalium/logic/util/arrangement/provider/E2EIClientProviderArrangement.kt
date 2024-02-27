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
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
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

    fun withGettingCoreCryptoSuccessful()

    fun withGetNewAcmeEnrollmentSuccessful()

    fun withGetMLSClientSuccessful()

    fun withE2EINewActivationEnrollmentSuccessful()

    fun withE2EINewRotationEnrollmentSuccessful()

    fun withE2EIEnabled(isEnabled: Boolean)

    fun withSelfUser(selfUser: SelfUser?)
}

class E2EIClientProviderArrangementImpl : E2EIClientProviderArrangement {
    override val mlsClientProvider: MLSClientProvider = mock(classOf<MLSClientProvider>())
    override val mlsClient: MLSClient = mock(classOf<MLSClient>())
    override val e2eiClient: E2EIClient = mock(classOf<E2EIClient>())
    override val userRepository: UserRepository = mock(classOf<UserRepository>())
    override val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())
    override val coreCryptoCentral = mock(classOf<CoreCryptoCentral>())

    override fun withGettingCoreCryptoSuccessful() {
        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getCoreCrypto)
            .whenInvokedWith(anything())
            .then { Either.Right(coreCryptoCentral) }
    }

    override fun withGetNewAcmeEnrollmentSuccessful() {
        given(coreCryptoCentral)
            .suspendFunction(coreCryptoCentral::newAcmeEnrollment)
            .whenInvokedWith(anything())
            .thenReturn(e2eiClient)
    }


    override fun withGetMLSClientSuccessful() {
        given(mlsClientProvider)
            .suspendFunction(mlsClientProvider::getMLSClient)
            .whenInvokedWith(anything())
            .then { Either.Right(mlsClient) }
    }

    override fun withE2EINewActivationEnrollmentSuccessful() {
        given(mlsClient)
            .suspendFunction(mlsClient::e2eiNewActivationEnrollment)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(e2eiClient)
    }
    override fun withE2EINewRotationEnrollmentSuccessful() {
        given(mlsClient)
            .suspendFunction(mlsClient::e2eiNewRotateEnrollment)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(e2eiClient)
    }

    override fun withE2EIEnabled(isEnabled: Boolean) {
        given(mlsClient)
            .suspendFunction(mlsClient::isE2EIEnabled)
            .whenInvoked()
            .thenReturn(isEnabled)
    }

    override fun withSelfUser(selfUser: SelfUser?) {
        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(selfUser)
    }

}
