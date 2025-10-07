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
package com.wire.kalium.conversation.history.data

import app.cash.turbine.test
import com.wire.kalium.conversation.history.data.dao.SQLiteHistoryClientDAO
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class SQLiteHistoryClientDAOTest {

    private lateinit var testDatabase: TestUserDatabase
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var historyClientDAO: SQLiteHistoryClientDAO

    @BeforeTest
    fun setup() = runTest(StandardTestDispatcher()) {
        testDispatcher = StandardTestDispatcher()
        testDatabase = TestUserDatabase(TEST_USER_ID, testDispatcher)
        historyClientDAO = SQLiteHistoryClientDAO(
            historyClientQueries = testDatabase.builder.historyClientQueries,
            queriesContext = testDispatcher
        )

        // Insert fake conversations to satisfy foreign key constraint
        insertFakeConversations()
    }

    private suspend fun insertFakeConversations() {
        // Insert the test conversation used in most tests
        insertFakeConversation(TEST_CONVERSATION_ID)

        // Insert the second test conversation used in some tests
        val secondConversationId = QualifiedIDEntity("conversation2", "domain")
        insertFakeConversation(secondConversationId)
    }

    private suspend fun insertFakeConversation(conversationId: QualifiedIDEntity) {
        val now = Instant.DISTANT_PAST

        val conversationEntity = ConversationEntity(
            id = conversationId,
            name = "Test Conversation",
            type = ConversationEntity.Type.GROUP,
            teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            creatorId = TEST_USER_ID.value,
            lastNotificationDate = now,
            lastModifiedDate = now,
            lastReadDate = now,
            access = listOf(ConversationEntity.Access.PRIVATE),
            accessRole = listOf(ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.ENABLED,
            messageTimer = null,
            userMessageTimer = null,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            isChannel = false,
            channelAccess = null,
            channelAddPermission = null,
            wireCell = null,
            historySharingRetentionSeconds = 0,
        )

        testDatabase.builder.conversationDAO.insertConversation(conversationEntity)
    }

    @AfterTest
    fun tearDown() {
        testDatabase.delete()
    }

    @Test
    fun givenHistoryClientInserted_whenGettingAllForConversation_thenShouldReturnInsertedClient() = runTest(testDispatcher) {
        // Given
        val conversationId = TEST_CONVERSATION_ID
        val clientId = "client1"
        val secret = byteArrayOf(1, 2, 3, 4)
        val creationDate = Instant.fromEpochMilliseconds(42L)

        historyClientDAO.insertClient(
            conversationId = conversationId,
            id = clientId,
            secret = secret,
            creationDate = creationDate
        )

        // When
        val result = historyClientDAO.getAllForConversation(conversationId)

        // Then
        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(clientId, this.id)
            assertEquals(creationDate, this.creationTime)
            assertTrue(this.secret.value.contentEquals(secret))
        }
    }

    @Test
    fun givenMultipleHistoryClientsInserted_whenGettingAllForConversation_thenShouldReturnAllClientsForThatConversation() =
        runTest(testDispatcher) {
            // Given
            val conversationId1 = TEST_CONVERSATION_ID
            val conversationId2 = QualifiedIDEntity("conversation2", "domain")
            val clientId1 = "client1"
            val clientId2 = "client2"
            val secret1 = byteArrayOf(1, 2, 3, 4)
            val secret2 = byteArrayOf(5, 6, 7, 8)
            val baseInstant = Instant.DISTANT_PAST
            val creationDate1 = baseInstant
            val creationDate2 = baseInstant + 1.days // One day later

            // Insert clients for first conversation
            historyClientDAO.insertClient(
                conversationId = conversationId1,
                id = clientId1,
                secret = secret1,
                creationDate = creationDate1
            )

            // Insert client for second conversation
            historyClientDAO.insertClient(
                conversationId = conversationId2,
                id = clientId2,
                secret = secret2,
                creationDate = creationDate2
            )

            // When
            val result1 = historyClientDAO.getAllForConversation(conversationId1)
            val result2 = historyClientDAO.getAllForConversation(conversationId2)

            // Then
            assertEquals(1, result1.size)
            assertEquals(1, result2.size)

            with(result1.first()) {
                assertEquals(clientId1, this.id)
            }

            with(result2.first()) {
                assertEquals(clientId2, this.id)
            }
        }

    @Test
    fun givenHistoryClientsWithDifferentDates_whenGettingAllFromDateOnwards_thenShouldReturnOnlyClientsFromThatDateOnwards() =
        runTest(testDispatcher) {
            // Given
            val conversationId = TEST_CONVERSATION_ID
            val clientId1 = "client1"
            val clientId2 = "client2"
            val clientId3 = "client3"
            val secret = byteArrayOf(1, 2, 3, 4)

            val baseInstant = Instant.DISTANT_PAST
            val date1 = baseInstant
            val date2 = baseInstant + 1.days // One day later
            val date3 = baseInstant + 2.days // Two days later

            // Insert clients with different dates
            historyClientDAO.insertClient(conversationId, clientId1, secret, date1)
            historyClientDAO.insertClient(conversationId, clientId2, secret, date2)
            historyClientDAO.insertClient(conversationId, clientId3, secret, date3)

            // When - get clients from date2 onwards
            val result = historyClientDAO.getAllForConversationFromDateOnwards(conversationId, date2)

            // Then
            assertEquals(2, result.size)
            val clientIds = result.map { it.id }.toSet()
            assertTrue(clientIds.contains(clientId2))
            assertTrue(clientIds.contains(clientId3))
            assertTrue(!clientIds.contains(clientId1))
        }

    @Test
    fun givenHistoryClientsInserted_whenObservingAllForConversation_thenShouldEmitClientsForThatConversation() = runTest(testDispatcher) {
        // Given
        val conversationId = TEST_CONVERSATION_ID
        val clientId1 = "client1"
        val clientId2 = "client2"
        val secret = byteArrayOf(1, 2, 3, 4)
        val date = Instant.DISTANT_PAST

        // Insert clients
        historyClientDAO.insertClient(conversationId, clientId1, secret, date)

        // When - observe clients
        historyClientDAO.observeAllForConversation(conversationId).test {
            // Then - initial emission should contain the first client
            val initialClients = awaitItem()
            assertEquals(1, initialClients.size)
            assertEquals(clientId1, initialClients.first().id)

            // When - insert another client
            historyClientDAO.insertClient(conversationId, clientId2, secret, date)

            // Then - should emit updated list with both clients
            val updatedClients = awaitItem()
            assertEquals(2, updatedClients.size)
            val clientIds = updatedClients.map { it.id }.toSet()
            assertTrue(clientIds.contains(clientId1))
            assertTrue(clientIds.contains(clientId2))

            cancelAndIgnoreRemainingEvents()
        }
    }

    companion object {
        private val TEST_USER_ID = UserIDEntity("testUser", "domain")
        private val TEST_CONVERSATION_ID = QualifiedIDEntity("conversation1", "domain")
    }
}
