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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.base.model.PushTokenBody
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PushTokenUpdaterTest {

    @Test
    fun givenFBTokenIsUpdated_thenNothingHappen() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(false)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::observeCurrentClientId)
            .wasNotInvoked()

        verify(arrangement.notificationTokenRepository)
            .function(arrangement.notificationTokenRepository::getNotificationToken)
            .wasNotInvoked()

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::registerToken)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.pushTokenRepository)
            .suspendFunction(arrangement.pushTokenRepository::setUpdateFirebaseTokenFlag)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenFBTokenIsNotUpdatedButThereIsNoClientYet_thenNothingHappen() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(null)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify(arrangement.notificationTokenRepository)
            .function(arrangement.notificationTokenRepository::getNotificationToken)
            .wasNotInvoked()

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::registerToken)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.pushTokenRepository)
            .suspendFunction(arrangement.pushTokenRepository::setUpdateFirebaseTokenFlag)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenFBTokenIsNotUpdatedAndRegisteringIsSucceed_thenUpdateFlagChanged() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withNotificationToken(Either.Right(NotificationToken(MOCK_TOKEN, MOCK_TRANSPORT, MOCK_APP_ID)))
            .withRegisterTokenResult(Either.Right(Unit))
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::registerToken)
            .with(eq(pushTokenRequestBody))
            .wasInvoked(once)

        verify(arrangement.pushTokenRepository)
            .suspendFunction(arrangement.pushTokenRepository::setUpdateFirebaseTokenFlag)
            .with(eq(false))
            .wasInvoked(once)
    }

    companion object {
        private const val MOCK_TOKEN = "7239"
        private const val MOCK_TRANSPORT = "GCM"
        private const val MOCK_APP_ID = "applicationId"
        private const val MOCK_CLIENT_ID = "clientId"

        private val pushTokenRequestBody = PushTokenBody(
            senderId = MOCK_APP_ID,
            client = MOCK_CLIENT_ID,
            token = MOCK_TOKEN,
            transport = MOCK_TRANSPORT
        )
    }

    private class Arrangement {

        @Mock
        val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

        @Mock
        val notificationTokenRepository: NotificationTokenRepository = mock(classOf<NotificationTokenRepository>())

        @Mock
        val pushTokenRepository: PushTokenRepository = mock(classOf<PushTokenRepository>())

        private val pushTokenUpdater: PushTokenUpdater = PushTokenUpdater(
            clientRepository,
            notificationTokenRepository,
            pushTokenRepository
        )

        init {
            given(pushTokenRepository)
                .suspendFunction(pushTokenRepository::setUpdateFirebaseTokenFlag)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withCurrentClientId(clientId: ClientId?) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::observeCurrentClientId)
                .whenInvoked()
                .thenReturn(flowOf(clientId))
        }

        fun withRegisterTokenResult(result: Either<NetworkFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::registerToken)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpdateFirebaseTokenFlag(result: Boolean) = apply {
            given(pushTokenRepository)
                .suspendFunction(pushTokenRepository::observeUpdateFirebaseTokenFlag)
                .whenInvoked()
                .thenReturn(flowOf(result))
        }

        fun withNotificationToken(result: Either<StorageFailure, NotificationToken>) = apply {
            given(notificationTokenRepository)
                .function(notificationTokenRepository::getNotificationToken)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to pushTokenUpdater
    }
}
