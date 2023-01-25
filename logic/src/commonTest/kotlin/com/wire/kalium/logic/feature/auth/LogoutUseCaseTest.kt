/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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


package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    @Test
    fun givenAnyReason_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        for (reason in LogoutReason.values()) {
            val (arrangement, logoutUseCase) = Arrangement()
                .withLogoutResult(Either.Right(Unit))
                .withSessionLogoutResult(Either.Right(Unit))
                .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
                .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
                .withClearCurrentClientIdResult(Either.Right(Unit))
                .withClearRetainedClientIdResult(Either.Right(Unit))
                .withClearHasRegisteredMLSClientResult(Either.Right(Unit))
                .withUserSessionScopeGetResult(null)
                .withFirebaseTokenUpdate()
                .arrange()

            logoutUseCase.invoke(reason)

            verify(arrangement.deregisterTokenUseCase)
                .suspendFunction(arrangement.deregisterTokenUseCase::invoke)
                .wasInvoked(exactly = once)
            verify(arrangement.logoutRepository)
                .suspendFunction(arrangement.logoutRepository::logout)
                .wasInvoked(exactly = once)
            verify(arrangement.sessionRepository)
                .suspendFunction(arrangement.sessionRepository::logout)
                .with(any(), eq(reason))
                .wasInvoked(exactly = once)
            verify(arrangement.sessionRepository)
                .suspendFunction(arrangement.sessionRepository::updateCurrentSession)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.userSessionScopeProvider)
                .function(arrangement.userSessionScopeProvider::delete)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.pushTokenRepository)
                .suspendFunction(arrangement.pushTokenRepository::setUpdateFirebaseTokenFlag)
                .with(eq(true))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenHardLogout_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.SELF_HARD_LOGOUT
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .arrange()

        logoutUseCase.invoke(reason)

        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.clearUserDataUseCase)
            .suspendFunction(arrangement.clearUserDataUseCase::invoke)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRemovedClientOrDeletedAccount_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        for (reason in listOf(LogoutReason.REMOVED_CLIENT, LogoutReason.DELETED_ACCOUNT)) {
            val (arrangement, logoutUseCase) = Arrangement()
                .withLogoutResult(Either.Right(Unit))
                .withSessionLogoutResult(Either.Right(Unit))
                .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
                .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
                .withClearCurrentClientIdResult(Either.Right(Unit))
                .withClearRetainedClientIdResult(Either.Right(Unit))
                .withClearHasRegisteredMLSClientResult(Either.Right(Unit))
                .withUserSessionScopeGetResult(null)
                .withFirebaseTokenUpdate()
                .arrange()

            logoutUseCase.invoke(reason)

            verify(arrangement.clearClientDataUseCase)
                .suspendFunction(arrangement.clearClientDataUseCase::invoke)
                .wasInvoked(exactly = once)
            verify(arrangement.clearUserDataUseCase)
                .suspendFunction(arrangement.clearUserDataUseCase::invoke)
                .wasNotInvoked()
            verify(arrangement.clientRepository)
                .suspendFunction(arrangement.clientRepository::clearCurrentClientId)
                .wasInvoked(exactly = once)
            verify(arrangement.clientRepository)
                .suspendFunction(arrangement.clientRepository::clearHasRegisteredMLSClient)
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSoftLogout_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.SELF_SOFT_LOGOUT
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .arrange()

        logoutUseCase.invoke(reason)

        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasNotInvoked()
        verify(arrangement.clearUserDataUseCase)
            .suspendFunction(arrangement.clearUserDataUseCase::invoke)
            .wasNotInvoked()
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::clearCurrentClientId)
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::clearHasRegisteredMLSClient)
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val logoutRepository = mock(classOf<LogoutRepository>())

        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val deregisterTokenUseCase = mock(classOf<DeregisterTokenUseCase>())

        @Mock
        val clearClientDataUseCase = configure(mock(ClearClientDataUseCase::class)) { stubsUnitByDefault = true }

        @Mock
        val clearUserDataUseCase = configure(mock(ClearUserDataUseCase::class)) { stubsUnitByDefault = true }

        @Mock
        val userSessionScopeProvider = configure(mock(classOf<UserSessionScopeProvider>())) { stubsUnitByDefault = true }

        @Mock
        val pushTokenRepository = mock(classOf<PushTokenRepository>())

        private val logoutUseCase: LogoutUseCase = LogoutUseCaseImpl(
            logoutRepository,
            sessionRepository,
            clientRepository,
            USER_ID,
            deregisterTokenUseCase,
            clearClientDataUseCase,
            clearUserDataUseCase,
            userSessionScopeProvider,
            pushTokenRepository
        )

        fun withDeregisterTokenResult(result: DeregisterTokenUseCase.Result): Arrangement {
            given(deregisterTokenUseCase)
                .suspendFunction(deregisterTokenUseCase::invoke)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withLogoutResult(result: Either<CoreFailure, Unit>): Arrangement {
            given(logoutRepository)
                .suspendFunction(logoutRepository::logout)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withSessionLogoutResult(result: Either<StorageFailure, Unit>): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::logout)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
            return this
        }

        fun withAllValidSessionsResult(result: Either<StorageFailure, List<AccountInfo.Valid>>): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::allValidSessions)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withClearCurrentClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::clearCurrentClientId)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withClearRetainedClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::clearRetainedClientId)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withClearHasRegisteredMLSClientResult(result: Either<StorageFailure, Unit>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::clearHasRegisteredMLSClient)
                .whenInvoked()
                .thenReturn(result)
            return this
        }

        fun withUserSessionScopeGetResult(result: UserSessionScope?): Arrangement {
            given(userSessionScopeProvider)
                .function(userSessionScopeProvider::get)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        suspend fun withFirebaseTokenUpdate() = apply {
            given(pushTokenRepository)
                .suspendFunction(pushTokenRepository::setUpdateFirebaseTokenFlag)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to logoutUseCase

        companion object {
            val USER_ID = QualifiedID("userId", "domain")
            val VALID_ACCOUNT_INFO = AccountInfo.Valid(USER_ID)
        }
    }
}
