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
package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.conversation.LegacyConversationDependencies
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class LegacyBindingIdentityTest {
    @Test
    fun observersResolveOnlyTheirOwnCaptureAndPreserveFreshUseCasesAndCrossFeatureAlias() {
        var conversationCreations = 0
        var messageCreations = 0
        lateinit var graph: KaliumUserGraph
        val conversations = object : LegacyConversationDependencies by mock<LegacyConversationDependencies>() {
            override val conversationRepository: ConversationRepository
                get() = mock<ConversationRepository>().also { conversationCreations++ }
            override val messageRepository: MessageRepository
                get() = graph.messageMessageRepository
        }
        val messages = object : LegacyMessageDependencies by mock<LegacyMessageDependencies>() {
            override val messageRepository: MessageRepository
                get() = mock<MessageRepository>().also { messageCreations++ }
        }
        graph = createKaliumGraph().userGraphFactory.create(
            mock<UserGraphDependencies>(), conversations, messages,
        )
        // Every unconfigured legacy dependency throws if read. This exercises the actual generated
        // graph, proving these paths do not pull in sender, typing, deletion or other feature inputs.
        assertNotSame(graph.conversationObserveConversationDetails, graph.conversationObserveConversationDetails)
        assertEquals(1, conversationCreations)
        assertEquals(0, messageCreations)
        assertNotSame(graph.messageGetMessageById, graph.messageGetMessageById)
        assertNotSame(graph.messageObserveMessageById, graph.messageObserveMessageById)
        assertEquals(1, conversationCreations)
        assertEquals(1, messageCreations)
        assertSame(graph.messageMessageRepository, graph.conversationMessageRepository)
        assertEquals(1, messageCreations)
    }

    @Test
    fun eachUserGraphRetainsItsOwnLegacyCapturesWithoutChangingFactoryInputs() {
        var captures = 0
        val messages = object : LegacyMessageDependencies by mock<LegacyMessageDependencies>() {
            override val messageRepository: MessageRepository
                get() = mock<MessageRepository>().also { captures++ }
        }
        val root = createKaliumGraph()
        val first = root.userGraphFactory.create(mock<UserGraphDependencies>(), mock<LegacyConversationDependencies>(), messages)
        val second = root.userGraphFactory.create(mock<UserGraphDependencies>(), mock<LegacyConversationDependencies>(), messages)
        assertEquals(0, captures)
        assertSame(first.messageMessageRepository, first.messageMessageRepository)
        assertNotSame(first.messageMessageRepository, second.messageMessageRepository)
        assertEquals(2, captures)
    }

    @Test
    fun extensionAccessorsConstructFreshUseCasesWithoutUnrelatedCaptures() {
        val messages = object : LegacyMessageDependencies by mock<LegacyMessageDependencies>() {
            override val messageRepository: MessageRepository = mock<MessageRepository>()
            override val dispatcher = KaliumDispatcherImpl
        }
        val graph = createKaliumGraph().userGraphFactory.create(
            mock<UserGraphDependencies>(), mock<LegacyConversationDependencies>(), messages,
        )
        assertNotSame(graph.messageGetPaginatedFlowOfMessagesByConversation, graph.messageGetPaginatedFlowOfMessagesByConversation)
        assertNotSame(graph.messageFetchOlderMessagesByConversationId, graph.messageFetchOlderMessagesByConversationId)
    }
}
