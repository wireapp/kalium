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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.notification.NotificationToken
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.network.api.model.PushTokenBody
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.observeCurrentClientId()
        }

        verify(VerifyMode.not) {
            arrangement.notificationTokenRepository.getNotificationToken()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.registerToken(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(any())
        }
    }

    @Test
    fun givenFBTokenIsNotUpdatedButThereIsNoClientYet_thenNothingHappen() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(null)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify(VerifyMode.not) {
            arrangement.notificationTokenRepository.getNotificationToken()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.registerToken(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(any())
        }
    }

    @Test
    fun givenNativePushDisabledForServer_thenTokenRegistrationIsSkippedAndRetryFlagIsCleared() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withServerNativePushEnabled(false)
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verify(VerifyMode.not) {
            arrangement.notificationTokenRepository.getNotificationToken()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.registerToken(any(), any(), any(), any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(false)
        }
    }

    @Test
    fun givenFBTokenIsNotUpdatedAndRegisteringIsSucceed_thenUpdateFlagChanged() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withServerNativePushEnabled(true)
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.registerToken(
                pushTokenRequestBody.senderId,
                pushTokenRequestBody.client,
                pushTokenRequestBody.token,
                pushTokenRequestBody.transport,
            )
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(false)
        }
    }

    @Test
    fun givenNativePushRegistrationReturnsAppNotFound_thenWebSocketIsEnabledBeforeDisablingPush() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withServerNativePushEnabled(true)
            .withNotificationToken(
                Either.Right(
                    NotificationToken(
                        MOCK_TOKEN,
                        MOCK_TRANSPORT,
                        MOCK_APP_ID
                    )
                )
            )
            .withRegisterTokenResult(Either.Left(serverMiscommunicationFailure(code = 404, label = "app-not-found")))
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        // WebSocket must be enabled BEFORE disabling native push.
        // If the process dies after disabling push but before enabling WebSocket,
        // the user would have no notification channel and no recovery path.
        verifySuspend(VerifyMode.order) {
            arrangement.sessionRepository.updatePersistentWebSocketStatus(MOCK_USER_ID, true)
            arrangement.sessionRepository.setNativePushSupportedByServer(MOCK_USER_ID, false)
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(false)
        }
    }

    @Test
    fun givenNonAppNotFoundError_thenPersistentWebSocketIsNotEnabled() = runTest {
        val (arrangement, pushTokenUpdater) = Arrangement()
            .withUpdateFirebaseTokenFlag(true)
            .withCurrentClientId(ClientId(MOCK_CLIENT_ID))
            .withServerNativePushEnabled(true)
            .withNotificationToken(
                Either.Right(
                    NotificationToken(
                        MOCK_TOKEN,
                        MOCK_TRANSPORT,
                        MOCK_APP_ID
                    )
                )
            )
            .withRegisterTokenResult(Either.Left(serverMiscommunicationFailure(code = 403, label = "forbidden")))
            .arrange()

        pushTokenUpdater.monitorTokenChanges()

        verifySuspend(VerifyMode.not) {
            arrangement.sessionRepository.updatePersistentWebSocketStatus(any(), any())
        }

        verifySuspend(VerifyMode.not) {
            arrangement.sessionRepository.setNativePushSupportedByServer(any(), any())
        }
    }

    companion object {
        private const val MOCK_TOKEN = "7239"
        private const val MOCK_TRANSPORT = "GCM"
        private const val MOCK_APP_ID = "applicationId"
        private const val MOCK_CLIENT_ID = "clientId"
        private val MOCK_USER_ID = UserId("self", "wire.com")

        private val pushTokenRequestBody = PushTokenBody(
            senderId = MOCK_APP_ID,
            client = MOCK_CLIENT_ID,
            token = MOCK_TOKEN,
            transport = MOCK_TRANSPORT
        )
    }

    private class Arrangement {
        val clientRepository: ClientRepository = mock(mode = MockMode.autoUnit)
        val notificationTokenRepository: NotificationTokenRepository = mock(mode = MockMode.autoUnit)
        val pushTokenRepository: PushTokenRepository = mock(mode = MockMode.autoUnit)
        val sessionRepository: SessionRepository = mock(mode = MockMode.autoUnit)

        private val pushTokenUpdater: PushTokenUpdater = PushTokenUpdater(
            clientRepository,
            notificationTokenRepository,
            pushTokenRepository,
            sessionRepository,
            MOCK_USER_ID
        )

        fun withCurrentClientId(clientId: ClientId?) = apply {
            everySuspend {
                clientRepository.observeCurrentClientId()
            } returns flowOf(clientId)
        }

        fun withRegisterTokenResult(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend {
                clientRepository.registerToken(any(), any(), any(), any())
            } returns result
        }

        fun withUpdateFirebaseTokenFlag(result: Boolean) = apply {
            everySuspend {
                pushTokenRepository.observeUpdateFirebaseTokenFlag()
            } returns flowOf(result)
        }

        fun withServerNativePushEnabled(enabled: Boolean) = apply {
            everySuspend {
                sessionRepository.isNativePushSupportedByServer(any())
            } returns Either.Right(enabled)
        }

        fun withNotificationToken(result: Either<StorageFailure, NotificationToken>) = apply {
            every {
                notificationTokenRepository.getNotificationToken()
            } returns result
        }

        fun arrange() = this to pushTokenUpdater.also {
            everySuspend {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            } returns Either.Right(Unit)
            everySuspend {
                sessionRepository.setNativePushSupportedByServer(any(), any())
            } returns Either.Right(Unit)
            everySuspend {
                sessionRepository.updatePersistentWebSocketStatus(any(), any())
            } returns Either.Right(Unit)
        }
    }
}
