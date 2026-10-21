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

import com.wire.kalium.logic.data.conversation.ConversationDataSource
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.persistence.withTestUserDatabase
import com.wire.kalium.userstorage.di.UserStorage
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Real generated graphs and datasource recipes; only authenticated APIs are substituted. */
class KaliumReadGraphIdentityTest {
    @Test
    fun generatedConsumerCachesRealRepositoriesAndBorrowsExistingUserResources() = runTest {
        val user = UserId("read-graph", "example.com")
        withTestUserDatabase(user.toDao()) { database ->
            val messageApi = mock<MessageApi>()
            val mlsMessageApi = mock<MLSMessageApi>()
            val conversationApi = mock<ConversationApi>()
            val clientApi = mock<ClientApi>()
            val network = mock<AuthenticatedNetworkContainer>()
            every { network.messageApi } returns messageApi
            every { network.mlsMessageApi } returns mlsMessageApi
            every { network.conversationApi } returns conversationApi
            every { network.clientApi } returns clientApi
            val factory = ReadRepositoryFactory(user, UserStorage(database.builder), network)
            val messages = mutableListOf<MessageRepository>()
            val conversations = mutableListOf<ConversationRepository>()
            val graph by lazy {
                createKaliumReadGraph(
                    ReadRepositories(
                        messages = { factory.message().also(messages::add) },
                        conversations = { factory.conversation().also(conversations::add) },
                        dispatchers = StandardTestDispatcher(testScheduler).testKaliumDispatcher(),
                    )
                )
            }
            val registry = KaliumConsumerRegistry { graph.consumerGraphFactory.create(it) }
            try {
                val first = registry.createConsumer()
                val second = registry.createConsumer()
                assertTrue(messages.isEmpty())
                assertTrue(conversations.isEmpty())

                val get = first.getMessageById
                val observe = first.observeMessageById
                val firstGuard = get.field("messageRepository")!!
                assertSame(firstGuard, observe.field("messageRepository"))
                assertNotSame(get, first.getMessageById)
                assertNotSame(observe, first.observeMessageById)
                assertSame(firstGuard, first.getMessageById.field("messageRepository"))
                assertEquals(1, messages.size)
                assertTrue(conversations.isEmpty())
                val firstMessage = assertIs<MessageDataSource>(messages.single())
                assertSame(firstMessage, firstGuard.field("delegate"))

                val secondGuard = second.getMessageById.field("messageRepository")!!
                assertNotSame(firstGuard, secondGuard)
                val secondMessage = assertIs<MessageDataSource>(messages[1])
                assertNotSame(firstMessage, secondMessage)
                assertSame(secondMessage, secondGuard.field("delegate"))
                // DAO wrappers remain factory values; their database query objects share the user owner.
                assertNotSame(firstMessage.field("messageDAO"), secondMessage.field("messageDAO"))
                assertSame(firstMessage.field("messageDAO")!!.field("queries"), secondMessage.field("messageDAO")!!.field("queries"))
                for (repository in messages) {
                    assertSame(messageApi, repository.field("transport")!!.field("messageApi"))
                    assertSame(mlsMessageApi, repository.field("transport")!!.field("mlsMessageApi"))
                }

                val firstDetails = first.observeConversationDetails
                val firstConversationGuard = firstDetails.field("conversationRepository")!!
                assertNotSame(firstDetails, first.observeConversationDetails)
                assertSame(firstConversationGuard, first.observeConversationDetails.field("conversationRepository"))
                val secondConversationGuard = second.observeConversationDetails.field("conversationRepository")!!
                assertNotSame(firstConversationGuard, secondConversationGuard)
                val firstConversation = assertIs<ConversationDataSource>(conversations[0])
                val secondConversation = assertIs<ConversationDataSource>(conversations[1])
                assertSame(firstConversation, firstConversationGuard.field("delegate"))
                assertSame(secondConversation, secondConversationGuard.field("delegate"))
                assertNotSame(firstConversation, secondConversation)
                assertSame(
                    firstConversation.field("conversationDAO")!!.field("conversationQueries"),
                    secondConversation.field("conversationDAO")!!.field("conversationQueries"),
                )
                assertSame(
                    firstMessage.field("messageDAO")!!.field("conversationsQueries"),
                    firstConversation.field("conversationDAO")!!.field("conversationQueries"),
                )
                for (repository in conversations) {
                    assertSame(conversationApi, repository.field("conversationApi"))
                    assertSame(clientApi, repository.field("clientApi"))
                }

                first.closeAndJoin()
                second.getMessageById(TestConversation.ID, "unwritten")
                registry.drainConsumers()
                // Consumer shutdown must leave the actual user database usable.
                assertNull(database.builder.messageDAO.getMessageById("unwritten", TestConversation.ID.toDao()))

                // Ordinary legacy factory calls keep separate repository captures.
                val legacyFactoryFirst = factory.message()
                val legacyFactorySecond = factory.message()
                assertNotSame(legacyFactoryFirst, legacyFactorySecond)
                assertNotSame(factory.conversation(), factory.conversation())
                // The legacy message capture keeps its own repository.
                val legacyCapture = factory.message()
                assertNotSame(firstMessage, legacyCapture)
            } finally {
                registry.drainConsumers()
            }
        }
    }

    @Test
    fun compatibilityGraphsKeepCapturedRepositoriesAndFreshUseCasesWithDefaultDispatchers() {
        val messageCapture = mock<MessageRepository>()
        val conversationCapture = mock<ConversationRepository>()
        val get = messageReads(messageCapture).getMessageById
        val observe = messageReads(messageCapture).observeMessageById
        val details = conversationReads(conversationCapture).observeConversationDetails
        assertSame(messageCapture, get.field("messageRepository"))
        assertSame(messageCapture, observe.field("messageRepository"))
        assertSame(conversationCapture, details.field("conversationRepository"))
        assertNotSame(get, messageReads(messageCapture).getMessageById)
        assertNotSame(observe, messageReads(messageCapture).observeMessageById)
        assertNotSame(details, conversationReads(conversationCapture).observeConversationDetails)
        assertSame(KaliumDispatcherImpl, get.field("dispatchers"))
        assertSame(KaliumDispatcherImpl, observe.field("dispatchers"))
        assertSame(KaliumDispatcherImpl, details.field("dispatcher"))
    }
}

// Inspect existing captures without adding production accessors or replacing the generated graph.
private fun Any.field(name: String): Any? = javaClass.getDeclaredField(name).also { it.isAccessible = true }.get(this)
