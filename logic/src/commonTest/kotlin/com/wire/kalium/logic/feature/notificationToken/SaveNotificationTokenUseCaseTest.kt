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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.notification.NotificationTokenRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.user.ObserveValidAccountsUseCase
import com.wire.kalium.logic.framework.TestUser
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
import kotlin.test.assertEquals

class SaveNotificationTokenUseCaseTest {

    @Test
    fun apnsTokenIsPersistedAndEveryValidAccountIsMarkedForRegistration() = runTest {
        val fixture = Fixture(listOf(TestUser.SELF, TestUser.SELF.copy(id = UserId("second", "wire.test"))))
        for (transport in listOf("APNS", "APNS_SANDBOX")) {
            assertEquals(Result.Success, fixture.useCase("00ab", transport, "com.wire.prism.ios"))
            verify { fixture.tokens.persistNotificationToken("00ab", transport, "com.wire.prism.ios") }
        }
        fixture.pending.forEach { repository ->
            verifySuspend(VerifyMode.exactly(2)) { repository.setUpdateFirebaseTokenFlag(true) }
        }
    }

    @Test
    fun tokenArrivingBeforeAnyAccountIsStillPersisted() = runTest {
        val fixture = Fixture(emptyList())
        assertEquals(Result.Success, fixture.useCase("00ab", "APNS", "com.wire.prism.ios"))
        verify { fixture.tokens.persistNotificationToken("00ab", "APNS", "com.wire.prism.ios") }
        verify(VerifyMode.not) { fixture.sessions.getOrCreate(any(), any<UserSessionScope.() -> PushTokenRepository>()) }
    }

    @Test
    fun storageFailureDoesNotScheduleBackendRegistration() = runTest {
        val fixture = Fixture(listOf(TestUser.SELF))
        every { fixture.tokens.persistNotificationToken(any(), any(), any()) } returns Either.Left(StorageFailure.DataNotFound)
        assertEquals(
            Result.Failure.Generic(StorageFailure.DataNotFound),
            fixture.useCase("00ab", "APNS", "com.wire.prism.ios")
        )
        verifySuspend(VerifyMode.not) { fixture.pending.single().setUpdateFirebaseTokenFlag(any()) }
    }

    @Test
    fun failureToMarkAccountForRegistrationIsReportedToTheCaller() = runTest {
        val fixture = Fixture(listOf(TestUser.SELF))
        everySuspend { fixture.pending.single().setUpdateFirebaseTokenFlag(true) } returns Either.Left(StorageFailure.DataNotFound)
        assertEquals(
            Result.Failure.Generic(StorageFailure.DataNotFound),
            fixture.useCase("00ab", "APNS", "com.wire.prism.ios")
        )
    }

    private class Fixture(accounts: List<SelfUser>) {
        val tokens = mock<NotificationTokenRepository>()
        val sessions = mock<UserSessionScopeProvider>()
        private val observeAccounts = mock<ObserveValidAccountsUseCase>()
        val pending = accounts.map { user ->
            mock<PushTokenRepository>(mode = MockMode.autoUnit).also { repository ->
                every {
                    sessions.getOrCreate(eq(user.id), any<UserSessionScope.() -> PushTokenRepository>())
                } returns repository
                everySuspend { repository.setUpdateFirebaseTokenFlag(true) } returns Either.Right(Unit)
            }
        }
        val useCase = SaveNotificationTokenUseCaseImpl(tokens, observeAccounts, sessions)

        init {
            every { tokens.persistNotificationToken(any(), any(), any()) } returns Either.Right(Unit)
            everySuspend { observeAccounts() } returns flowOf(accounts.map { it to null })
        }
    }
}
