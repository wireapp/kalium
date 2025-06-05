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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.PushTokenBody
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class PushTokenUpdaterTest {

    @Test
    fun givenFBTokenIsUpdated_thenNothingHappen() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(false)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        coVerify {
            arrangement.clientRepository.observeCurrentClientId()
        }.wasNotInvoked()

        verify {
            arrangement.notificationTokenRepository.getNotificationToken()
        }.wasNotInvoked()

        coVerify {
            arrangement.clientRepository.registerToken(any(), any(), any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFBTokenIsNotUpdatedButThereIsNoClientYet_thenNothingHappen() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(null)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify {
            arrangement.notificationTokenRepository.getNotificationToken()
        }.wasNotInvoked()

        coVerify {
            arrangement.clientRepository.registerToken(any(), any(), any(), any())
        }.wasNotInvoked()

        coVerify {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenFBTokenIsNotUpdatedAndRegisteringIsSucceed_thenUpdateFlagChanged() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withNotificationToken(
                Either.Right(
                    NotificationToken(
                        MOCK_TOKEN,
                        MOCK_TRANSPORT,
                        MOCK_APP_ID
                    )
                )
            )
            .withRegisterTokenResult(Either.Right(Unit))
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        coVerify {
            arrangement.clientRepository.registerToken(
                eq(pushTokenRequestBody.senderId),
                eq(pushTokenRequestBody.client),
                eq(pushTokenRequestBody.token),
                eq(pushTokenRequestBody.transport),
            )
        }.wasInvoked(once)

        coVerify {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(false))
        }.wasInvoked(once)
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
        val clientRepository: ClientRepository = mock(ClientRepository::class)
        val notificationTokenRepository: NotificationTokenRepository = mock(NotificationTokenRepository::class)
        val pushTokenRepository: PushTokenRepository = mock(PushTokenRepository::class)

        private val pushTokenUpdater: PushTokenUpdater = PushTokenUpdater(
            clientRepository,
            notificationTokenRepository,
            pushTokenRepository
        )

        suspend fun withCurrentClientId(clientId: ClientId?) = apply {
            coEvery {
                clientRepository.observeCurrentClientId()
            }.returns(flowOf(clientId))
        }

        suspend fun withRegisterTokenResult(result: Either<NetworkFailure, Unit>) = apply {
            coEvery {
                clientRepository.registerToken(any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withUpdateFirebaseTokenFlag(result: Boolean) = apply {
            coEvery {
                pushTokenRepository.observeUpdateFirebaseTokenFlag()
            }.returns(flowOf(result))
        }

        fun withNotificationToken(result: Either<StorageFailure, NotificationToken>) = apply {
            every {
                notificationTokenRepository.getNotificationToken()
            }.returns(result)
        }

        suspend fun arrange() = this to pushTokenUpdater.also {
            coEvery {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            }.returns(Either.Right(Unit))
        }
    }
}
