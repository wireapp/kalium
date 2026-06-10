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

package com.wire.kalium.logic.feature.server

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.stubs.newServerConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsCrossBackendLoginBlockedUseCaseTest {

    @Test
    fun givenFlagDisabled_whenInvoked_thenReturnsFalse() = runTest {
        val useCase = arrange(blockFlag = false)

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(newServerConfig(2).links))

        assertFalse(result)
    }

    @Test
    fun givenNoCurrentSession_whenInvoked_thenReturnsFalse() = runTest {
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Failure.SessionNotFound,
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(newServerConfig(2).links))

        assertFalse(result)
    }

    @Test
    fun givenInvalidSession_whenInvoked_thenReturnsFalse() = runTest {
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(
                AccountInfo.Invalid(USER_ID, com.wire.kalium.logic.data.logout.LogoutReason.SELF_HARD_LOGOUT)
            ),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(newServerConfig(2).links))

        assertFalse(result)
    }

    @Test
    fun givenServerConfigLookupFails_whenInvoked_thenReturnsFalse() = runTest {
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(AccountInfo.Valid(USER_ID)),
            serverConfigResult = ServerConfigForAccountUseCase.Result.Failure(StorageFailure.DataNotFound),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(newServerConfig(2).links))

        assertFalse(result)
    }

    @Test
    fun givenMatchingLinks_whenInvoked_thenReturnsFalse() = runTest {
        val current = newServerConfig(1)
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(AccountInfo.Valid(USER_ID)),
            serverConfigResult = ServerConfigForAccountUseCase.Result.Success(current),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(current.links))

        assertFalse(result)
    }

    @Test
    fun givenDifferentLinks_whenInvoked_thenReturnsTrue() = runTest {
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(AccountInfo.Valid(USER_ID)),
            serverConfigResult = ServerConfigForAccountUseCase.Result.Success(newServerConfig(1)),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.Links(newServerConfig(2).links))

        assertTrue(result)
    }

    @Test
    fun givenMatchingSsoConfigId_whenInvoked_thenReturnsFalse() = runTest {
        val current = newServerConfig(1)
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(AccountInfo.Valid(USER_ID)),
            serverConfigResult = ServerConfigForAccountUseCase.Result.Success(current),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.SsoConfigId(current.id))

        assertFalse(result)
    }

    @Test
    fun givenDifferentSsoConfigId_whenInvoked_thenReturnsTrue() = runTest {
        val useCase = arrange(
            blockFlag = true,
            currentSession = CurrentSessionResult.Success(AccountInfo.Valid(USER_ID)),
            serverConfigResult = ServerConfigForAccountUseCase.Result.Success(newServerConfig(1)),
        )

        val result = useCase(IsCrossBackendLoginBlockedUseCase.Target.SsoConfigId("some-other-config-id"))

        assertTrue(result)
    }

    private fun arrange(
        blockFlag: Boolean,
        currentSession: CurrentSessionResult = CurrentSessionResult.Failure.SessionNotFound,
        serverConfigResult: ServerConfigForAccountUseCase.Result =
            ServerConfigForAccountUseCase.Result.Failure(StorageFailure.DataNotFound),
    ) = IsCrossBackendLoginBlockedUseCase(
        kaliumConfigs = KaliumConfigs(blockCrossBackendLogin = blockFlag),
        currentSession = { currentSession },
        serverConfigForAccount = { serverConfigResult },
    )

    companion object {
        private val USER_ID = UserId("user-id", "domain")
    }
}
