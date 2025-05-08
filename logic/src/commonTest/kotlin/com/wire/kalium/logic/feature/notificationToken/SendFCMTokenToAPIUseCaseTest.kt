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
package com.wire.kalium.logic.feature.notificationToken

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import io.mockative.any
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SendFCMTokenToAPIUseCaseTest {
    @Test
    fun whenInvokedAndSuccessfulResultInTokenRegistered() = runTest {

        val useCase =
            Arrangement()
                .withClientId()
                .withNotificationToken()
                .withClientRepositoryRegisterToken()
                .arrange()

        val result = useCase.invoke()
        assertEquals(Either.Right(Unit), result)
    }

    @Test
    fun whenInvokedAndFailureOnClientId() = runTest {

        val useCase = Arrangement()
            .withClientIdFailure()
            .withNotificationToken()
            .arrange()

        val failReason = useCase.invoke().fold(
            { it.status },
            { fail("Expected failure, but got success") }
        )
        assertEquals(SendFCMTokenError.Reason.CANT_GET_CLIENT_ID, failReason)

    }

    @Test
    fun whenInvokedAndFailureOnNotificationToken() = runTest {

        val useCase = Arrangement()
            .withClientId()
            .withNotificationTokenFailure()
            .arrange()

        val failReason = useCase.invoke().fold(
            { it.status },
            { fail("Expected failure, but got success") }
        )
        assertEquals(SendFCMTokenError.Reason.CANT_GET_NOTIFICATION_TOKEN, failReason)
    }

    @Test
    fun whenInvokedAndFailureOnClientRepositoryRegisterToken() = runTest {

        val useCase = Arrangement()
            .withClientId()
            .withNotificationToken()
            .withClientRepositoryRegisterTokenFailure()
            .arrange()

        val failReason = useCase.invoke().fold(
            { it.status },
            { fail("Expected failure, but got success") }
        )
        assertEquals(SendFCMTokenError.Reason.CANT_REGISTER_TOKEN, failReason)
    }


    private class Arrangement {

        private val currentClientIdProvider: CurrentClientIdProvider = mock(CurrentClientIdProvider::class)
        private val clientRepository: ClientRepository = mock(ClientRepository::class)
        private val notificationTokenRepository: NotificationTokenRepository = mock(NotificationTokenRepository::class)


        fun arrange(): SendFCMTokenToAPIUseCaseImpl {
            return SendFCMTokenToAPIUseCaseImpl(
                currentClientIdProvider, clientRepository, notificationTokenRepository
            )
        }

        suspend fun withClientId() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(ClientId("clientId")))
        }

        suspend fun withClientIdFailure() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Left(CoreFailure.MissingClientRegistration))
        }

        fun withNotificationToken() = apply {
            every {
                notificationTokenRepository.getNotificationToken()
            }.returns(Either.Right(NotificationToken("applicationId", "token", "transport")))
        }

        fun withNotificationTokenFailure() = apply {
            every {
                notificationTokenRepository.getNotificationToken()
            }.returns(Either.Left(StorageFailure.DataNotFound))
        }

        suspend fun withClientRepositoryRegisterToken() = apply {
            coEvery {
                clientRepository.registerToken(any(), any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withClientRepositoryRegisterTokenFailure() = apply {
            coEvery {
                clientRepository.registerToken(any(), any(), any(), any())
            }.returns(Either.Left(NetworkFailure.FeatureNotSupported))
        }

    }

}
