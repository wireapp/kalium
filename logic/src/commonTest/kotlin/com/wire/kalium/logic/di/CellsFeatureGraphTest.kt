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

package com.wire.kalium.logic.di

import com.wire.kalium.cells.CellsScope
import com.wire.kalium.logic.feature.conversation.ConversationDependencies
import com.wire.kalium.logic.feature.message.MessageDependencies
import com.wire.kalium.network.api.base.authenticated.AccessTokenApi
import com.wire.kalium.network.session.SessionManager
import dev.mokkery.mock
import dev.zacsweers.metro.createGraphFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.isActive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CellsFeatureGraphTest {

    private val arrangement = Arrangement()

    @AfterTest
    fun tearDown() {
        arrangement.close()
    }

    @Test
    fun givenCellsGraphOwner_whenFeatureIsNotRequested_thenCellsScopeIsNotCreated() {
        arrangement.createOwner()

        assertEquals(0, arrangement.scopeCreations)
    }

    @Test
    fun givenCellsFeatureIsRequestedMultipleTimes_whenNotReleased_thenScopeIsShared() {
        val owner = arrangement.createOwner()

        val first = owner.cellsScope
        val second = owner.cellsScope

        assertSame(first, second)
        assertEquals(1, arrangement.scopeCreations)
    }

    @Test
    fun givenCellsFeatureWasReleased_whenRequestedAgain_thenNewScopeIsCreated() {
        val owner = arrangement.createOwner()
        val first = owner.cellsScope

        owner.release()
        val second = owner.cellsScope

        assertFalse(first.isActive)
        assertTrue(second.isActive)
        assertNotSame(first, second)
        assertEquals(2, arrangement.scopeCreations)
    }

    @Test
    fun givenCellsScopeWasClosedExternally_whenRequestedAgain_thenClosedGraphIsReplaced() {
        val owner = arrangement.createOwner()
        val first = owner.cellsScope

        first.close()
        val second = owner.cellsScope

        assertFalse(first.isActive)
        assertTrue(second.isActive)
        assertNotSame(first, second)
        assertEquals(2, arrangement.scopeCreations)
    }

    private class Arrangement {
        private val cellsClient = HttpClient(MockEngine { respondOk() })
        private val createdScopes = mutableListOf<CellsScope>()
        var scopeCreations: Int = 0
            private set

        fun createOwner(): CellsFeatureGraphOwner {
            val userGraph = createGraphFactory<UserSessionGraph.Factory>()
                .create(mock<ConversationDependencies>(), mock<MessageDependencies>())
            return CellsFeatureGraphOwner(
                graphFactory = userGraph,
                cellsScopeFactory = CellsScopeFactory {
                    scopeCreations += 1
                    createCellsScope().also(createdScopes::add)
                }
            )
        }

        fun close() {
            createdScopes.forEach(CellsScope::close)
            cellsClient.close()
        }

        private fun createCellsScope(): CellsScope = CellsScope(
            cellsClient = cellsClient,
            dao = CellsScope.CellScopeDao(
                attachmentDraftDao = mock(),
                conversationsDao = mock(),
                attachmentsDao = mock(),
                cellFileDao = mock(),
                userDao = mock(),
                memberDao = mock(),
                publicLinkDao = mock(),
                userConfigDAO = mock(),
            ),
            sessionManager = mock<SessionManager>(),
            accessTokenApi = mock<AccessTokenApi>(),
        )
    }
}
