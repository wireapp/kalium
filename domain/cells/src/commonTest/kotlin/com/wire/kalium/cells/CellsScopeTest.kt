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

package com.wire.kalium.cells

import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellsScopeTest {

    @Test
    fun givenActiveCellsScope_whenClosed_thenOwnedCoroutineScopeIsCancelled() {
        val cellsClient = HttpClient(MockEngine { respondOk() })
        val scope = CellsScope(
            cellsClient = cellsClient,
            dao = createCellScopeDao(),
            sessionManager = mock<SessionManager>(),
            accessTokenApi = mock<AccessTokenApi>(),
        )

        assertTrue(scope.isActive)

        scope.close()
        scope.close()

        assertFalse(scope.isActive)
        cellsClient.close()
    }

    private fun createCellScopeDao(): CellsScope.CellScopeDao = CellsScope.CellScopeDao(
        attachmentDraftDao = mock(),
        conversationsDao = mock(),
        attachmentsDao = mock(),
        cellFileDao = mock(),
        userDao = mock(),
        memberDao = mock(),
        publicLinkDao = mock(),
        userConfigDAO = mock(),
    )
}
