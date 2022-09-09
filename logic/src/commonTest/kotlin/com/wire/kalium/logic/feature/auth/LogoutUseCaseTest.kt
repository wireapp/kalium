@file:OptIn(ConfigurationApi::class)

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.ConfigurationApi
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
    fun givenHardLogout_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.SELF_LOGOUT
        val isHardLogout = true
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.validAuthSession)))
            .withUpdateCurrentSessionResult(Either.Right(Unit))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .arrange()
        logoutUseCase.invoke(reason, isHardLogout)
        verify(arrangement.deregisterTokenUseCase)
            .suspendFunction(arrangement.deregisterTokenUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.logoutRepository)
            .suspendFunction(arrangement.logoutRepository::logout)
            .wasInvoked(exactly = once)
        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::logout)
            .with(any(), eq(reason), eq(isHardLogout))
            .wasInvoked(exactly = once)
        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.clearUserDataUseCase)
            .suspendFunction(arrangement.clearUserDataUseCase::invoke)
            .wasInvoked(exactly = once)
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::clearCurrentClientId)
            .wasInvoked(exactly = once)
        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::updateCurrentSession)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.userSessionScopeProvider)
            .function(arrangement.userSessionScopeProvider::delete)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSoftLogout_whenLoggingOut_thenDoNotExecuteClearingUserData() = runTest {
        val reason = LogoutReason.SELF_LOGOUT
        val isHardLogout = false
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.validAuthSession)))
            .withUpdateCurrentSessionResult(Either.Right(Unit))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .arrange()
        logoutUseCase.invoke(reason, isHardLogout)
        verify(arrangement.clearClientDataUseCase)
            .suspendFunction(arrangement.clearClientDataUseCase::invoke)
            .wasNotInvoked()
        verify(arrangement.clearUserDataUseCase)
            .suspendFunction(arrangement.clearUserDataUseCase::invoke)
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

        private val logoutUseCase: LogoutUseCase = LogoutUseCaseImpl(
            logoutRepository,
            sessionRepository,
            clientRepository,
            userId,
            deregisterTokenUseCase,
            clearClientDataUseCase,
            clearUserDataUseCase,
            userSessionScopeProvider
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
                .function(sessionRepository::logout)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
            return this
        }

        fun withUpdateCurrentSessionResult(result: Either<StorageFailure, Unit>): Arrangement {
            given(sessionRepository)
                .function(sessionRepository::updateCurrentSession)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        fun withAllValidSessionsResult(result: Either<StorageFailure, List<AuthSession>>): Arrangement {
            given(sessionRepository)
                .function(sessionRepository::allValidSessions)
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

        fun withUserSessionScopeGetResult(result: UserSessionScope?): Arrangement {
            given(userSessionScopeProvider)
                .function(userSessionScopeProvider::get)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        fun arrange() = this to logoutUseCase

        companion object {
            val userId = QualifiedID("userId", "domain")
            val validAuthSession = AuthSession(
                AuthSession.Session.Valid(userId, "accessToken", "refreshToken", "token_type"),
                newServerConfig(1).links
            )
        }
    }
}
