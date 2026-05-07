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
import com.wire.kalium.common.functional.Either
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
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogoutUseCaseTest {

    @Test
    fun givenAnyReason_whenLoggingOut_thenExecuteAllRequiredActions() = runTest {
        for (reason in LogoutReason.entries) {
            val (arrangement, logoutUseCase) = Arrangement()
                .withLogoutResult(Either.Right(Unit))
                .withSessionLogoutResult(Either.Right(Unit))
                .withAllValidSessionsResult(Either.Right(listOf(Arrangement.VALID_ACCOUNT_INFO)))
                .withDeregisterTokenResult(DeregisterTokenUseCase.Result.Success)
                .withClearCurrentClientIdResult(Either.Right(Unit))
                .withClearRetainedClientIdResult(Either.Right(Unit))
                .withClearHasRegisteredMLSClientResult(Either.Right(Unit))
                .withClearHasConsumableNotifications(Either.Right(Unit))
                .withUserSessionScopeGetResult(null)
                .withFirebaseTokenUpdate()
                .withNoOngoingCalls()
                .arrange()

            logoutUseCase.invoke(reason)
            arrangement.globalTestScope.advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionRepository.logout(any(), eq(reason))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.sessionRepository.updateCurrentSession(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userSessionScopeProvider.delete(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userConfigRepository.clearE2EISettings()
            }

            if (reason == LogoutReason.SELF_HARD_LOGOUT) {
                verifySuspend(VerifyMode.not) {
                    arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
                }
            } else {
                verifySuspend(VerifyMode.exactly(1)) {
                    arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
                }
            }
            verifySuspend {
                arrangement.logoutCallback(any<UserId>(), eq(reason))
            }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearUserDataUseCase.invoke()
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearUserDataUseCase.invoke()
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearUserDataUseCase.invoke()
        }
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

        verifySuspend(VerifyMode.not) {
            arrangement.deregisterTokenUseCase.invoke()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.logoutRepository.logout()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearUserDataUseCase.invoke()
        }
    }

    @Test
    fun givenSessionExpired_whenLoggingOutWithNomadProfiles_thenExecuteAllRequiredActions() = runTest {
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
            .withKaliumConfigs { it.copy(wipeOnCookieInvalid = false) }
            .withIsNomadEnabled(true)
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.deregisterTokenUseCase.invoke()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.logoutRepository.logout()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearUserDataUseCase.invoke()
        }
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.deregisterTokenUseCase.invoke()
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.logoutRepository.logout()
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clearClientDataUseCase.invoke()
            }

            verifySuspend(VerifyMode.not) {
                arrangement.clearUserDataUseCase.invoke()
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.clearCurrentClientId()
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.clientRepository.clearHasRegisteredMLSClient()
            }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deregisterTokenUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutRepository.logout()
        }

        verifySuspend(VerifyMode.not) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.clearUserDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.clearCurrentClientId()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.clientRepository.clearHasRegisteredMLSClient()
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.deregisterTokenUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutRepository.logout()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.observeEstablishedCallsUseCase.invoke()
        }

        verifySuspend(VerifyMode.exactly(calls.size)) {
            arrangement.endCall.invoke(any())
        }
    }

    @Test
    fun givenHardLogout_whenLoggingOut_thenE2EISettingsClearedBeforeWipe() = runTest {
        var e2eiCalledBeforeWipe = false
        var e2eiWasCalled = false

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

        everySuspend { arrangement.userConfigRepository.clearE2EISettings() } calls {
            e2eiWasCalled = true
        }
        everySuspend { arrangement.clearUserDataUseCase.invoke() } calls {
            e2eiCalledBeforeWipe = e2eiWasCalled
        }

        logoutUseCase.invoke(LogoutReason.SELF_HARD_LOGOUT)
        arrangement.globalTestScope.advanceUntilIdle()

        assertTrue(e2eiCalledBeforeWipe, "clearE2EISettings must be called before clearUserDataUseCase (wipe)")
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
            .withClearHasConsumableNotifications(Either.Right(Unit))
            .withFirebaseTokenUpdate()
            .withNoOngoingCalls()
            .arrange()

        logoutUseCase.invoke(reason)
        arrangement.globalTestScope.advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clearClientDataUseCase.invoke()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.logoutRepository.clearClientRelatedLocalMetadata()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.clearRetainedClientId()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pushTokenRepository.setUpdateFirebaseTokenFlag(eq(true))
        }
    }

    private data class Arrangement(
        val logoutRepository: LogoutRepository = mock<LogoutRepository>(mode = MockMode.autoUnit),
        val sessionRepository: SessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit),
        val clientRepository: ClientRepository = mock<ClientRepository>(mode = MockMode.autoUnit),
        val userConfigRepository: UserConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit),
        val deregisterTokenUseCase: DeregisterTokenUseCase = mock<DeregisterTokenUseCase>(mode = MockMode.autoUnit),
        val clearClientDataUseCase: ClearClientDataUseCase = mock<ClearClientDataUseCase>(mode = MockMode.autoUnit),
        val clearUserDataUseCase: ClearUserDataUseCase = mock<ClearUserDataUseCase>(mode = MockMode.autoUnit),
        val userSessionScopeProvider: UserSessionScopeProvider = mock<UserSessionScopeProvider>(mode = MockMode.autoUnit),
        val pushTokenRepository: PushTokenRepository = mock<PushTokenRepository>(mode = MockMode.autoUnit),
        val userSessionWorkScheduler: UserSessionWorkScheduler = mock<UserSessionWorkScheduler>(mode = MockMode.autoUnit),
        val observeEstablishedCallsUseCase: ObserveEstablishedCallsUseCase = mock<ObserveEstablishedCallsUseCase>(mode = MockMode.autoUnit),
        val endCall: EndCallUseCase = mock<EndCallUseCase>(mode = MockMode.autoUnit),
        val logoutCallback: LogoutCallback = mock<LogoutCallback>(mode = MockMode.autoUnit),
        val isNomadEnabled: () -> Boolean = { false },
        var kaliumConfigs: KaliumConfigs = KaliumConfigs(),
        val globalTestScope: TestScope = TestScope()
    ) {
        suspend fun withDeregisterTokenResult(result: DeregisterTokenUseCase.Result): Arrangement {
            everySuspend {
                deregisterTokenUseCase.invoke()
            } returns (result)
            return this
        }

        suspend fun withLogoutResult(result: Either<CoreFailure, Unit>): Arrangement {
            everySuspend {
                logoutRepository.logout()
            } returns (result)
            return this
        }

        suspend fun withSessionLogoutResult(result: Either<StorageFailure, Unit>): Arrangement {
            everySuspend {
                sessionRepository.logout(any(), any())
            } returns (result)
            return this
        }

        suspend fun withAllValidSessionsResult(result: Either<StorageFailure, List<AccountInfo.Valid>>): Arrangement {
            everySuspend {
                sessionRepository.allValidSessions()
            } returns (result)
            return this
        }

        suspend fun withClearCurrentClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            everySuspend {
                clientRepository.clearCurrentClientId()
            } returns (result)
            return this
        }

        suspend fun withClearRetainedClientIdResult(result: Either<StorageFailure, Unit>): Arrangement {
            everySuspend {
                clientRepository.clearRetainedClientId()
            } returns (result)
            return this
        }

        suspend fun withClearHasRegisteredMLSClientResult(result: Either<StorageFailure, Unit>): Arrangement {
            everySuspend {
                clientRepository.clearHasRegisteredMLSClient()
            } returns (result)
            return this
        }

        suspend fun withClearHasConsumableNotifications(result: Either<StorageFailure, Unit>): Arrangement {
            everySuspend {
                clientRepository.clearClientHasConsumableNotifications()
            } returns (result)
            return this
        }

        fun withUserSessionScopeGetResult(result: UserSessionScope?): Arrangement {
            every {
                userSessionScopeProvider.get(any())
            } returns (result)
            return this
        }

        suspend fun withFirebaseTokenUpdate() = apply {
            everySuspend {
                pushTokenRepository.setUpdateFirebaseTokenFlag(any())
            } returns (Either.Right(Unit))
        }

        fun withKaliumConfigs(changeConfigs: (KaliumConfigs) -> KaliumConfigs) = apply {
            this.kaliumConfigs = changeConfigs(this.kaliumConfigs)
        }

        suspend fun withOngoingCalls(ongoingCalls: List<Call>) = apply {
            everySuspend {
                observeEstablishedCallsUseCase.invoke()
            } returns (flowOf(ongoingCalls))
        }

        suspend fun withNoOngoingCalls() = apply {
            everySuspend {
                observeEstablishedCallsUseCase.invoke()
            } returns (flowOf(emptyList()))
        }

        fun withIsNomadEnabled(isEnabled: Boolean): Arrangement {
            return copy(isNomadEnabled = { isEnabled })
        }

        suspend fun arrange() = this to LogoutUseCaseImpl(
            logoutRepository = logoutRepository,
            sessionRepository = sessionRepository,
            clientRepository = clientRepository,
            userConfigRepository = userConfigRepository,
            userId = USER_ID,
            deregisterTokenUseCase = deregisterTokenUseCase,
            clearClientDataUseCase = clearClientDataUseCase,
            clearUserDataUseCase = clearUserDataUseCase,
            userSessionScopeProvider = userSessionScopeProvider,
            pushTokenRepository = pushTokenRepository,
            globalCoroutineScope = globalTestScope,
            userSessionWorkScheduler = userSessionWorkScheduler,
            getEstablishedCallsUseCase = observeEstablishedCallsUseCase,
            endCallUseCase = endCall,
            logoutCallback = logoutCallback,
            kaliumConfigs = kaliumConfigs,
            isNomadEnabled = isNomadEnabled
        ).also {
            everySuspend {
                endCall.invoke(any())
            } returns (Unit)

            everySuspend {
                userConfigRepository.clearE2EISettings()
            } returns (Unit)

            everySuspend {
                userSessionWorkScheduler.cancelScheduledSendingOfPendingMessages()
            } returns (Unit)

            everySuspend {
                logoutRepository.onLogout(any())
            } returns (Unit)

            everySuspend {
                logoutCallback(any(), any())
            } returns (Unit)

            everySuspend {
                userSessionScopeProvider.delete(any())
            } returns (Unit)
        }

        companion object {
            val USER_ID = QualifiedID("userId", "domain")
            val VALID_ACCOUNT_INFO = AccountInfo.Valid(USER_ID)
        }
    }
}
