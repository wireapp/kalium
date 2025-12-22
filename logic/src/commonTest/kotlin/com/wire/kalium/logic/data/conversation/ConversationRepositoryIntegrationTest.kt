/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.fake
import com.wire.kalium.logic.data.toModel
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.stubs.ClientApiStub
import com.wire.kalium.logic.util.stubs.ConversationApiStub
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.ConversationPersistenceApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

// Uses concrete DAOs backed by in-memory Database, and fake APIs, instead of mocks for 100% of the dependencies
@OptIn(ConversationPersistenceApi::class)
class ConversationRepositoryIntegrationTest {

    private val userId = TestUser.USER_ID
    private val userIdEntity = TestUser.ENTITY_ID
    private val testDispatcher = StandardTestDispatcher()
    private val database = TestUserDatabase(userIdEntity, testDispatcher)

    private fun createSubject(
        conversationApi: ConversationApi = ConversationApiStub(),
        clientApi: ClientApi = ClientApiStub(),
    ): ConversationRepository = ConversationDataSource(
        selfUserId = userId,
        conversationDAO = database.builder.conversationDAO,
        memberDAO = database.builder.memberDAO,
        conversationApi = conversationApi,
        messageDAO = database.builder.messageDAO,
        messageDraftDAO = database.builder.messageDraftDAO,
        clientDAO = database.builder.clientDAO,
        clientApi = clientApi,
        conversationMetaDataDAO = database.builder.conversationMetaDataDAO,
        metadataDAO = database.builder.metadataDAO,
        conversationSyncDAO = database.builder.conversationSyncDAO
    )

    @Test
    fun givenPersistedConversations_whenGettingThem_thenShouldReturnWithCorrectBasicData() = runTest(testDispatcher) {
        val conversations = listOf(
            MockConversation.entity(id = ConversationIDEntity.fake(1)),
        )
        val subject = createSubject()

        subject.persistConversations(conversations).shouldSucceed()

        val firstConversation = conversations.first()
        subject.getConversationById(firstConversation.id.toModel()).shouldSucceed {
            assertEquals(firstConversation.id.toModel().value, it.id.value)
            assertEquals(firstConversation.name, it.name)
            assertEquals(firstConversation.type, it.type.toDAO())
        }
    }

    @Test
    fun givenPersistedChannelWithoutSharingRetention_whenGettingDetails_thenShouldReturnWithPrivateHistorySharing() =
        runTest(testDispatcher) {
            val conversationEntity = MockConversation.entity(
                id = ConversationIDEntity.fake(1)
            ).copy(
                type = ConversationEntity.Type.GROUP,
                isChannel = true,
                historySharingRetentionSeconds = 0,
            )
            val conversations = listOf(conversationEntity)
            val subject = createSubject()

            subject.persistConversations(conversations)
            subject.observeConversationDetailsById(conversations.first().id.toModel()).test {
                awaitItem().shouldSucceed {
                    assertIs<ConversationDetails.Group.Channel>(it)
                    val historySharing = it.historySharing
                    assertIs<ConversationHistorySettings.Private>(historySharing)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun givenPersistedChannelWithSharingRetention_whenGettingDetails_thenShouldReturnWithCorrectHistorySharingSeconds() =
        runTest(testDispatcher) {
            val expectedSeconds = 42312L
            val conversationEntity = MockConversation.entity(
                id = ConversationIDEntity.fake(1)
            ).copy(
                type = ConversationEntity.Type.GROUP,
                isChannel = true,
                historySharingRetentionSeconds = expectedSeconds,
            )
            val conversations = listOf(conversationEntity)
            val subject = createSubject()

            subject.persistConversations(conversations)
            subject.observeConversationDetailsById(conversations.first().id.toModel()).test {
                awaitItem().shouldSucceed {
                    assertIs<ConversationDetails.Group.Channel>(it)
                    val historySharing = it.historySharing
                    assertIs<ConversationHistorySettings.ShareWithNewMembers>(historySharing)
                    assertEquals(expectedSeconds, historySharing.retention.inWholeSeconds)
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
