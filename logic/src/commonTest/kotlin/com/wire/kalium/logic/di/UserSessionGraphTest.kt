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
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.UserSessionScopedFactory
import com.wire.kalium.logic.feature.conversation.ConversationDependencies
import com.wire.kalium.logic.feature.conversation.ConversationScope
import com.wire.kalium.logic.feature.message.MessageDependencies
import com.wire.kalium.logic.feature.message.MessageScope
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
        assertEquals(0, arrangement.userRepositoryResolutions)
        assertEquals(0, arrangement.userPropertyRepositoryResolutions)
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

    @Test
    fun givenUnscopedUseCaseIsRequestedTwice_whenUsingOneGraph_thenUseCasesDifferAndRepositoryIsShared() {
        val arrangement = Arrangement()
        val scope = arrangement.createConversationScope()

        val first = scope.getConversations
        val second = scope.getConversations

        assertNotSame(first, second)
        assertEquals(1, arrangement.repositoryCreations)
    }

    @Test
    fun givenUseCaseNeedsOnlyUserRepository_whenRequested_thenUnrelatedConversationRepositoryIsNotCreated() {
        val arrangement = Arrangement()
        val scope = arrangement.createConversationScope()

        val first = scope.observeUserListById
        val second = scope.observeUserListById

        assertNotSame(first, second)
        assertEquals(1, arrangement.userRepositoryResolutions)
        assertEquals(0, arrangement.repositoryCreations)
    }

    @Test
    fun givenStatefulTypingRepositoryIsRequestedTwice_whenUsingOneGraph_thenInstanceIsShared() {
        val arrangement = Arrangement()
        val scope = arrangement.createConversationScope()

        val first = scope.typingIndicatorIncomingRepository
        val second = scope.typingIndicatorIncomingRepository

        assertSame(first, second)
        assertEquals(1, arrangement.userPropertyRepositoryResolutions)
        assertEquals(0, arrangement.repositoryCreations)
    }

    @Test
    fun givenMessageScopeIsCreated_whenNoEntryPointIsRequested_thenRepositoriesAreNotCreated() {
        val arrangement = Arrangement()

        arrangement.createMessageScope()

        assertEquals(0, arrangement.messageRepositoryResolutions)
        assertEquals(0, arrangement.messageDraftRepositoryResolutions)
        assertEquals(0, arrangement.repositoryCreations)
    }

    @Test
    fun givenMessageUseCaseIsRequestedTwice_whenUsingOneGraph_thenUseCasesDifferAndRepositoryIsShared() {
        val arrangement = Arrangement()
        val scope = arrangement.createMessageScope()

        val first = scope.getMessageById
        val second = scope.getMessageById

        assertNotSame(first, second)
        assertEquals(1, arrangement.messageRepositoryResolutions)
        assertEquals(0, arrangement.repositoryCreations)
    }

    @Test
    fun givenDraftUseCaseIsRequestedTwice_whenUsingOneGraph_thenDraftRepositoryIsShared() {
        val arrangement = Arrangement()
        val scope = arrangement.createMessageScope()

        val first = scope.saveMessageDraftUseCase
        val second = scope.saveMessageDraftUseCase

        assertNotSame(first, second)
        assertEquals(1, arrangement.messageDraftRepositoryResolutions)
        assertEquals(0, arrangement.messageRepositoryResolutions)
        assertEquals(0, arrangement.repositoryCreations)
    }

    private class Arrangement {
        val createdRepositories = mutableListOf<ConversationRepository>()
        var repositoryCreations = 0
        var userRepositoryResolutions = 0
        var userPropertyRepositoryResolutions = 0
        var messageRepositoryResolutions = 0
        var messageDraftRepositoryResolutions = 0

        private val mockUserRepository = mock<UserRepository>()
        private val mockUserPropertyRepository = mock<UserPropertyRepository>()
        private val mockMessageRepository = mock<MessageRepository>()
        private val mockMessageDraftRepository = mock<MessageDraftRepository>()
        private val messageDependencies = mock<MessageDependencies> {
            every { messageDraftRepositoryFactory } returns UserSessionScopedFactory {
                messageDraftRepositoryResolutions += 1
                mockMessageDraftRepository
            }
        }

        private val dependencies = mock<ConversationDependencies> {
            every { conversationRepositoryFactory } returns UserSessionScopedFactory {
                repositoryCreations += 1
                mock<ConversationRepository>().also(createdRepositories::add)
            }
            every { userRepositoryFactory } returns UserSessionScopedFactory {
                userRepositoryResolutions += 1
                mockUserRepository
            }
            every { userPropertyRepositoryFactory } returns UserSessionScopedFactory {
                userPropertyRepositoryResolutions += 1
                mockUserPropertyRepository
            }
            every { messageRepositoryFactory } returns UserSessionScopedFactory {
                messageRepositoryResolutions += 1
                mockMessageRepository
            }
        }

        fun createConversationScope(): ConversationScope {
            val graph = createGraphFactory<UserSessionGraph.Factory>().create(dependencies, messageDependencies)
            return ConversationScope(graph)
        }

        fun createMessageScope(): MessageScope {
            val graph = createGraphFactory<UserSessionGraph.Factory>().create(dependencies, messageDependencies)
            return MessageScope(graph)
        }
    }
}
