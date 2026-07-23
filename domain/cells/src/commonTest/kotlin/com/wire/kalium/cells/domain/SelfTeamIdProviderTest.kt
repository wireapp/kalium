/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain

import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SelfTeamIdProviderTest {

    @Test
    fun givenTeamId_whenInvoked_thenReturnsIt() = runTest {
        val (_, provider) = Arrangement()
            .withSelfTeamId("team_id")
            .arrange()

        assertEquals("team_id", provider())
    }

    @Test
    fun givenInvokedMultipleTimes_whenTeamIdRequested_thenItIsReadFromStorageOnlyOnce() = runTest {
        val (arrangement, provider) = Arrangement()
            .withSelfTeamId("team_id")
            .arrange()

        repeat(3) { provider() }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.usersRepository.getUserTeamId(any())
        }
    }

    private class Arrangement {

        val usersRepository = mock<CellUsersRepository>(mode = MockMode.autoUnit)

        fun withSelfTeamId(teamId: String?) = apply {
            everySuspend { usersRepository.getUserTeamId(any()) }.returns(teamId.right())
        }

        fun arrange(): Pair<Arrangement, SelfTeamIdProvider> =
            this to SelfTeamIdProviderImpl(SELF_USER_ID, usersRepository)
    }

    private companion object {
        val SELF_USER_ID = UserId("self_user_id", "wire.com")
    }
}