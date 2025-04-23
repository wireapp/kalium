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
package com.wire.kalium.logic.feature.featureConfig

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.configuration.AppLockTeamConfig
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScope
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class ObserveIsAppLockEditableUseCaseTest {

    @Test
    fun givenTwoAccountAllWithoutEnforcedAppLock_whenInvoked_thenEmitTrue() = runTest {
        // given
        val appLockTeamConfig1 = AppLockTeamConfig(isEnforced = false, timeout = 1.minutes, isStatusChanged = null)
        val appLockTeamConfig2 = AppLockTeamConfig(isEnforced = false, timeout = 2.minutes, isStatusChanged = null)
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(listOf(AccountInfo.Valid(TestUser.SELF.id), AccountInfo.Valid(TestUser.OTHER.id))))
            .withObserveAppLockConfig(TestUser.SELF.id, flowOf(Either.Right(appLockTeamConfig1)))
            .withObserveAppLockConfig(TestUser.OTHER.id, flowOf(Either.Right(appLockTeamConfig2)))
            .arrange()
        // when
        val result = useCase().first()
        // then
        assertEquals(true, result)
    }

    @Test
    fun givenTwoAccountsAtLeastOneWithEnforcedAppLock_whenInvoked_thenEmitFalse() = runTest {
        // given
        val appLockTeamConfig1 = AppLockTeamConfig(isEnforced = false, timeout = 1.minutes, isStatusChanged = null)
        val appLockTeamConfig2 = AppLockTeamConfig(isEnforced = true, timeout = 2.minutes, isStatusChanged = null)
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(listOf(AccountInfo.Valid(TestUser.SELF.id), AccountInfo.Valid(TestUser.OTHER.id))))
            .withObserveAppLockConfig(TestUser.SELF.id, flowOf(Either.Right(appLockTeamConfig1)))
            .withObserveAppLockConfig(TestUser.OTHER.id, flowOf(Either.Right(appLockTeamConfig2)))
            .arrange()
        // when
        val result = useCase().first()
        // then
        assertEquals(false, result)
    }

    @Test
    fun givenAccountWithEnforcedAppLock_whenInvokedAndEnforceChangesWhenObserving_thenEmitProperValues() = runTest {
        // given
        val appLockTeamConfig = AppLockTeamConfig(isEnforced = true, timeout = 1.minutes, isStatusChanged = null)
        val appLockTeamConfigFlow = MutableStateFlow(Either.Right(appLockTeamConfig))
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(listOf(AccountInfo.Valid(TestUser.SELF.id))))
            .withObserveAppLockConfig(TestUser.SELF.id, appLockTeamConfigFlow)
            .arrange()
        // when - then
        useCase().test {
            assertEquals(false, awaitItem())
            appLockTeamConfigFlow.value = Either.Right(appLockTeamConfig.copy(isEnforced = false))
            assertEquals(true, awaitItem())
            appLockTeamConfigFlow.value = Either.Right(appLockTeamConfig.copy(isEnforced = true))
            assertEquals(false, awaitItem())
            cancel()
        }
    }

    class Arrangement {

        val userSessionScopeProvider = mock(UserSessionScopeProvider::class)
        val sessionRepository = mock(SessionRepository::class)

        private val useCase by lazy {
            ObserveIsAppLockEditableUseCaseImpl(
                userSessionScopeProvider = userSessionScopeProvider,
                sessionRepository = sessionRepository
            )
        }

        fun arrange() = this to useCase
        suspend fun withAllValidSessionsFlow(result: Flow<List<AccountInfo.Valid>>) = apply {
            coEvery {
                sessionRepository.allValidSessionsFlow()
            }.returns(result.map { Either.Right(it) })
        }

        fun withObserveAppLockConfig(userId: UserId, result: Flow<Either<StorageFailure, AppLockTeamConfig>>) = apply {
            val userConfigRepository = mock(UserConfigRepository::class)
            every {
                userSessionScopeProvider.getOrCreate(eq(userId), any<UserSessionScope.() -> UserConfigRepository>())
            }.returns(userConfigRepository)
            every {
                userConfigRepository.observeAppLockConfig()
            }.returns(result)
        }
    }
}
