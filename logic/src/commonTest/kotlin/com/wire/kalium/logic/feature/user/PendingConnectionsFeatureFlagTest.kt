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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.user.ContactStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.search.RefreshContactStatusesUseCaseImpl
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PendingConnectionsFeatureFlagTest {
    private val userId = UserId("user", "domain")
    private val search: SearchUserRepository = mock()

    @Test
    fun disabledByDefaultAndRefreshesMakeNoRequests() = runTest {
        assertFalse(KaliumConfigs().pendingConnections)
        assertEquals(emptyMap(), RefreshContactStatusesUseCaseImpl(search)(setOf(userId)))
        verifySuspend(VerifyMode.not) { search.fetchContactStatuses(any()) }
    }

    @Test
    fun enabledRefreshesReturnFreshStatus() = runTest {
        val statuses = mapOf(userId to ContactStatus.NON_CONTACTABLE)
        everySuspend { search.fetchContactStatuses(setOf(userId)) } returns statuses
        assertEquals(statuses, RefreshContactStatusesUseCaseImpl(search, true)(setOf(userId)))
        verifySuspend(VerifyMode.exactly(1)) { search.fetchContactStatuses(setOf(userId)) }
    }
}
