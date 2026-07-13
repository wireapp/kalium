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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.feature.conversation.ConversationDependencies
import com.wire.kalium.logic.feature.conversation.ConversationRepositoryFactory
import com.wire.kalium.logic.feature.conversation.ConversationScope
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class UserSessionGraphTest {

    @Test
    fun givenConversationScopeIsCreated_whenNoEntryPointIsRequested_thenRepositoryIsNotCreated() {
        val arrangement = Arrangement()

        arrangement.createConversationScope()

        assertEquals(0, arrangement.repositoryCreations)
    }

    @Test
    fun givenMultipleEntryPointsAreRequested_whenUsingOneGraph_thenRepositoryIsCreatedOnce() {
        val arrangement = Arrangement()
        val scope = arrangement.createConversationScope()

        scope.getOneToOneConversation
        scope.getConversations
        val repository = scope.conversationRepository

        assertEquals(1, arrangement.repositoryCreations)
        assertSame(arrangement.createdRepositories.single(), repository)
    }

    @Test
    fun givenTwoUserSessionGraphs_whenResolvingRepositories_thenEachGraphOwnsOneInstance() {
        val arrangement = Arrangement()
        val firstScope = arrangement.createConversationScope()
        val secondScope = arrangement.createConversationScope()

        firstScope.getOneToOneConversation
        firstScope.getConversations
        secondScope.getOneToOneConversation
        secondScope.getConversations
        val firstRepository = firstScope.conversationRepository
        val secondRepository = secondScope.conversationRepository

        assertEquals(2, arrangement.repositoryCreations)
        assertNotSame(firstRepository, secondRepository)
    }

    private class Arrangement {
        val createdRepositories = mutableListOf<ConversationRepository>()
        var repositoryCreations = 0

        private val dependencies = mock<ConversationDependencies> {
            every { conversationRepositoryFactory } returns ConversationRepositoryFactory {
                repositoryCreations += 1
                mock<ConversationRepository>().also(createdRepositories::add)
            }
        }

        fun createConversationScope(): ConversationScope {
            val graph = createGraphFactory<UserSessionGraph.Factory>().create(dependencies)
            return ConversationScope(graph, dependencies)
        }
    }
}
