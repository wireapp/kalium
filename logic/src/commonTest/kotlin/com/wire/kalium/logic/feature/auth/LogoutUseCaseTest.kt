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


package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestCall
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.time
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
                .withNoOngoingCalls()
                .arrange()

            logoutUseCase.invoke(reason)
            arrangement.globalTestScope.advanceUntilIdle()

            coVerify {
                arrangement.sessionRepository.logout(any(), eq(reason))
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.sessionRepository.updateCurrentSession(any())
            }.wasNotInvoked()
            coVerify {
                arrangement.userSessionScopeProvider.delete(any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.userConfigRepository.clearE2EISettings()
            }.wasInvoked(exactly = once)

            if (reason == LogoutReason.SELF_HARD_LOGOUT) {
                coVerify {
                    arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
                }.wasNotInvoked()
            } else {
                coVerify {
                    arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
                }.wasInvoked(exactly = once)
            }
            coVerify {
                arrangement.logoutCallback(any<UserId>(), eq(reason))
            }.wasInvoked()
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
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clearUserDataUseCase.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenRemovedClient_whenLoggingOutWithWipeOnDeviceRemovalEnabled_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.REMOVED_CLIENT
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .withKaliumConfigs { it.copy(wipeOnDeviceRemoval = true) }
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clearUserDataUseCase.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDeletedAccount_whenLoggingOutWithWipeOnDeviceRemovalEnabled_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.DELETED_ACCOUNT
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .withKaliumConfigs { it.copy(wipeOnDeviceRemoval = true) }
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clearUserDataUseCase.invoke()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSessionExpired_whenLoggingOutWithWipeOnInvalidCookieEnabled_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.SESSION_EXPIRED
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .withKaliumConfigs { it.copy(wipeOnCookieInvalid = true) }
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.deregisterTokenUseCase.invoke()
        }.wasNotInvoked()
        coVerify {
            arrangement.logoutRepository.logout()
        }.wasNotInvoked()

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clearUserDataUseCase.invoke()
        }.wasInvoked(exactly = once)
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
                .withNoOngoingCalls()
                .arrange()

            logoutUseCase.invoke(reason)
            arrangement.globalTestScope.advanceUntilIdle()

            coVerify {
                arrangement.deregisterTokenUseCase.invoke()
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.logoutRepository.logout()
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.clearClientDataUseCase.invoke()
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.clearUserDataUseCase.invoke()
            }.wasNotInvoked()
            coVerify {
                arrangement.clientRepository.clearCurrentClientId()
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.clientRepository.clearHasRegisteredMLSClient()
            }.wasInvoked(exactly = once)
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
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.deregisterTokenUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.logoutRepository.logout()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasNotInvoked()
        coVerify {
            arrangement.clearUserDataUseCase.invoke()
        }.wasNotInvoked()
        coVerify {
            arrangement.clientRepository.clearCurrentClientId()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.clientRepository.clearHasRegisteredMLSClient()
        }.wasNotInvoked()
    }

    @Test
    fun whenLogout_thenEndOngoingCalls() = runTest {
        val calls = listOf<Call>(
            TestCall.oneOnOneEstablishedCall().copy(conversationId = ConversationId("id1", "domain")),
            TestCall.oneOnOneEstablishedCall().copy(conversationId = ConversationId("id2", "domain"))
        )
        val reason = LogoutReason.REMOVED_CLIENT
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .withOngoingCalls(calls)
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.deregisterTokenUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.logoutRepository.logout()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.observeEstablishedCallsUseCase.invoke()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.endCall.invoke(any())
        }.wasInvoked(exactly = calls.size.time)
    }

    @Test
    fun givenAMigrationFailedLogout_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        val reason = LogoutReason.MIGRATION_TO_CC_FAILED
        val (arrangement, logoutUseCase) = Arrangement()
            .withLogoutResult(Either.Right(Unit))
            .withSessionLogoutResult(Either.Right(Unit))
            .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
            .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
            .withClearCurrentClientIdResult(Either.Right(Unit))
            .withClearRetainedClientIdResult(Either.Right(Unit))
            .withUserSessionScopeGetResult(null)
            .withFirebaseTokenUpdate()
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        coVerify {
            arrangement.clearClientDataUseCase.invoke()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.logoutRepository.clearClientRelatedLocalMetadata()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.clientRepository.clearRetainedClientId()
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val logoutRepository = mock(LogoutRepository::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        @Mock
        val deregisterTokenUseCase = mock(DeregisterTokenUseCase::class)

        @Mock
        val clearClientDataUseCase = mock(ClearClientDataUseCase::class)

        @Mock
        val clearUserDataUseCase = mock(ClearUserDataUseCase::class)

        @Mock
        val userSessionScopeProvider = mock(UserSessionScopeProvider::class)

        @Mock
        val pushTokenRepository = mock(PushTokenRepository::class)

        @Mock
        val userSessionWorkScheduler = mock(UserSessionWorkScheduler::class)

        @Mock
        val observeEstablishedCallsUseCase = mock(ObserveEstablishedCallsUseCase::class)

        @Mock
        val endCall = mock(EndCallUseCase::class)

        @Mock
        val logoutCallback = mock(LogoutCallback::class)

        var kaliumConfigs = KaliumConfigs()

        val globalTestScope = TestScope()

        private val logoutUseCase
            get() = LogoutUseCaseImpl(
                logoutRepository,
                sessionRepository,
                clientRepository,
                userConfigRepository,
                USER_ID,
                deregisterTokenUseCase,
                clearClientDataUseCase,
                clearUserDataUseCase,
                userSessionScopeProvider,
                pushTokenRepository,
                globalTestScope,
                userSessionWorkScheduler,
                observeEstablishedCallsUseCase,
                endCall,
                logoutCallback,
                kaliumConfigs
            )

        suspend fun withDeregisterTokenResult(result: DeregisterTokenUseCase.Result): Arrangement {
            coEvery {
                deregisterTokenUseCase.invoke()
            }.returns(result)
            return this
        }

        suspend fun withLogoutResult(result: Either<CoreFailure, Unit>): Arrangement {
            coEvery {
                logoutRepository.logout()
            }.returns(result)
            return this
        }

        suspend fun withSessionLogoutResult(result: Either<StorageFailure, Unit>): Arrangement {
            coEvery {
                sessionRepository.logout(any(), any())
            }.returns(result)
            return this
        }

        suspend fun withAllValidSessionsResult(result: Either<StorageFailure, List<AccountInfo.Valid>>): Arrangement {
            coEvery {
                sessionRepository.allValidSessions()
            }.returns(result)
            return this
        }

        suspend fun withClearCurrentClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            coEvery {
                clientRepository.clearCurrentClientId()
            }.returns(result)
            return this
        }

        suspend fun withClearRetainedClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            coEvery {
                clientRepository.clearRetainedClientId()
            }.returns(result)
            return this
        }

        suspend fun withClearHasRegisteredMLSClientResult(result: Either<StorageFailure, Unit>): Arrangement {
            coEvery {
                clientRepository.clearHasRegisteredMLSClient()
            }.returns(result)
            return this
        }

        fun withUserSessionScopeGetResult(result: UserSessionScope?): Arrangement {
            every {
                userSessionScopeProvider.get(any())
            }.returns(result)
            return this
        }

        suspend fun withFirebaseTokenUpdate() = apply {
            coEvery {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            }.returns(Either.Right(Unit))
        }

        fun withKaliumConfigs(changeConfigs: (KaliumConfigs) -> KaliumConfigs) = apply {
            this.kaliumConfigs = changeConfigs(this.kaliumConfigs)
        }

        suspend fun withOngoingCalls(ongoingCalls: List<Call>) = apply {
            coEvery {
                observeEstablishedCallsUseCase.invoke()
            }.returns(flowOf(ongoingCalls))
        }

        suspend fun withNoOngoingCalls() = apply {
            coEvery {
                observeEstablishedCallsUseCase.invoke()
            }.returns(flowOf(emptyList()))
        }

        suspend fun arrange() = this to logoutUseCase.also {
            coEvery {
                endCall.invoke(any())
            }.returns(Unit)

            coEvery {
                userConfigRepository.clearE2EISettings()
            }.returns(Unit)
        }

        companion object {
            val USER_ID = QualifiedID("userId", "domain")
            val VALID_ACCOUNT_INFO = AccountInfo.Valid(USER_ID)
        }
    }
}
