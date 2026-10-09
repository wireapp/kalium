/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.network

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.session.token.AccessTokenRefresher
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.api.model.SessionDTO
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.util.AccountResourceAccess
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.answering.returns
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AccountResourceSessionManagerTest {
    @Test
    fun tokenRefreshQueuedDuringHandoffUsesTokensWrittenByTheOtherOwner() = runTest {
        val access = AccountResourceAccess()
        val session = SessionDTO(QualifiedID("user", "wire.test"), "Bearer", "new-access", "new-refresh", "cookie")
        val manager = mock<SessionManager>()
        val api = mock<AccessTokenApi>()
        everySuspend { manager.session() } returns session
        val guarded = manager.guardedBy(access)
        access.pause {}
        val refreshed = async { guarded.updateToken(api, "old-refresh") }
        runCurrent()
        assertFalse(refreshed.isCompleted)
        access.resume {}
        // Strict mock has no updateToken stub: using the obsolete token would fail this test.
        assertEquals(session, refreshed.await())
    }

    @Test
    fun clientBindingRefreshReloadsTheTokenAfterHandoff() = runTest {
        val access = AccountResourceAccess()
        val client = ClientId("client")
        val tokens = AccountTokens(UserId("user", "wire.test"), "access", "refreshed", "Bearer", "cookie")
        val refresher = mock<AccessTokenRefresher>()
        everySuspend { refresher.refreshTokenAndPersistSession("new-refresh", client) } returns Either.Right(tokens)
        var stored = "old-refresh"
        val guarded = refresher.guardedBy(access) { stored }
        access.pause {}
        val refreshed = async { guarded.refreshTokenAndPersistSession("old-refresh", client) }
        runCurrent()
        assertFalse(refreshed.isCompleted)
        stored = "new-refresh"
        access.resume {}
        assertEquals(Either.Right(tokens), refreshed.await())
    }
}
