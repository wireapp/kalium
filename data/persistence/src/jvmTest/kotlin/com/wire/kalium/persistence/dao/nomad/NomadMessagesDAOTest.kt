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

package com.wire.kalium.persistence.dao.nomad

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.NomadMessageToInsert
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.clearInMemoryDatabase
import com.wire.kalium.persistence.db.userDatabaseBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NomadMessagesDAOTest {

    @BeforeTest
    fun setUp() {
        clearInMemoryDatabase(SELF_USER_ID_DAO)
    }

    @AfterTest
    fun tearDown() {
        clearInMemoryDatabase(SELF_USER_ID_DAO)
    }

    @Test
    fun givenMessages_whenStoring_thenTheyAreInsertedInConfiguredBatches() = runTest {
        val database = newDatabase()
        val dao = newDao(database)
        val messages = listOf(
            textMessage(id = "m1", conversationId = "c1", senderId = "u1", timestampMs = 1_000),
            textMessage(id = "m2", conversationId = "c1", senderId = "u2", timestampMs = 1_100),
            textMessage(id = "m3", conversationId = "c2", senderId = "u3", timestampMs = 1_200),
            textMessage(id = "m4", conversationId = "c2", senderId = "u1", timestampMs = 1_300),
            textMessage(id = "m5", conversationId = "c3", senderId = "u2", timestampMs = 1_400),
        )

        val result = dao.storeMessages(

            messages = messages,
            batchSize = 2
        )

        assertEquals(5, result.storedMessages)
        assertEquals(3, result.batches)
        messages.forEach { message ->
            assertNotNull(database.messageDAO.getMessageById(message.id, message.conversationId))
        }
        assertEquals(
            3,
            database.userDAO.getUsersDetailsByQualifiedIDList(listOf(qid("u1"), qid("u2"), qid("u3"))).size
        )
        assertNotNull(database.conversationDAO.getConversationById(qid("c1")))
        assertNotNull(database.conversationDAO.getConversationById(qid("c2")))
        assertNotNull(database.conversationDAO.getConversationById(qid("c3")))
    }

    @Test
    fun givenDuplicateMessages_whenStoring_thenStoredCountReflectsOnlyNewRows() = runTest {
        val database = newDatabase()
        val dao = newDao(database)
        val messages = listOf(
            textMessage(id = "m1", conversationId = "c1", senderId = "u1", timestampMs = 1_000),
            textMessage(id = "m2", conversationId = "c1", senderId = "u1", timestampMs = 1_100),
        )

        val first = dao.storeMessages(

            messages = messages,
            batchSize = 20
        )
        val second = dao.storeMessages(

            messages = messages,
            batchSize = 20
        )

        assertEquals(2, first.storedMessages)
        assertEquals(1, first.batches)
        assertEquals(0, second.storedMessages)
        assertEquals(1, second.batches)
    }

    @Test
    fun givenAssetLocationAndMultipartMessages_whenStoring_thenEachTypeIsStoredAsExpected() = runTest {
        val database = newDatabase()
        val dao = newDao(database)
        val messages = listOf(
            assetMessage(id = "m-asset", conversationId = "c1", senderId = "u1", timestampMs = 1_000),
            locationMessage(id = "m-location", conversationId = "c1", senderId = "u1", timestampMs = 1_100),
            multipartMessage(
                id = "m-multipart",
                conversationId = "c1",
                senderId = "u1",
                timestampMs = 1_200,
                attachmentAssetIds = listOf("att-1")
            ),
        )

        val result = dao.storeMessages(

            messages = messages,
            batchSize = 20
        )

        assertEquals(3, result.storedMessages)
        assertEquals(1, result.batches)

        val assetStored = assertNotNull(database.messageDAO.getMessageById("m-asset", qid("c1")))
        assertIs<MessageEntityContent.Asset>(assetStored.content)

        val locationStored = assertNotNull(database.messageDAO.getMessageById("m-location", qid("c1")))
        assertIs<MessageEntityContent.Location>(locationStored.content)

        val multipartStored = assertNotNull(database.messageDAO.getMessageById("m-multipart", qid("c1")))
        val multipartContent = assertIs<MessageEntityContent.Multipart>(multipartStored.content)
        assertEquals(1, multipartContent.attachments.size)
        assertEquals("att-1", multipartContent.attachments.single().assetId)

        val rawAttachments = database.messageAttachments.getAttachments("m-multipart", qid("c1"))
        assertEquals(1, rawAttachments.size)
        assertEquals("att-1", rawAttachments.single().assetId)
    }

    @Test
    fun givenInvalidSecondBatch_whenStoring_thenOnlyThatBatchIsRolledBack() = runTest {
        val database = newDatabase()
        val dao = newDao(database)
        val validMessage = textMessage(id = "m1", conversationId = "c1", senderId = "u1", timestampMs = 1_000)
        // MessageAttachments has a primary key on (conversation_id, message_id, asset_id).
        // Reusing the same asset_id twice in one multipart payload triggers a constraint failure.
        val invalidMessage = multipartMessageWithDuplicateAttachmentIds(
            id = "m2",
            conversationId = "c2",
            senderId = "u2",
            timestampMs = 1_100
        )

        assertFails {
            dao.storeMessages(
    
                messages = listOf(validMessage, invalidMessage),
                batchSize = 1
            )
        }

        assertNotNull(database.messageDAO.getMessageById(validMessage.id, validMessage.conversationId))
        assertNotNull(database.conversationDAO.getConversationById(validMessage.conversationId))
        assertEquals(
            1,
            database.userDAO.getUsersDetailsByQualifiedIDList(listOf(validMessage.payload.senderUserId)).size
        )

        assertNull(database.messageDAO.getMessageById(invalidMessage.id, invalidMessage.conversationId))
        assertNull(database.conversationDAO.getConversationById(invalidMessage.conversationId))
        assertTrue(database.userDAO.getUsersDetailsByQualifiedIDList(listOf(invalidMessage.payload.senderUserId)).isEmpty())
    }

    private fun newDatabase(): UserDatabaseBuilder = userDatabaseBuilder(
        platformDatabaseData = PlatformDatabaseData(StorageData.InMemory),
        userId = SELF_USER_ID_DAO,
        passphrase = null,
        dispatcher = Dispatchers.Default,
        enableWAL = false,
    )

    private fun newDao(database: UserDatabaseBuilder): NomadMessagesDAO = database.nomadMessagesDAO

    private fun textMessage(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
    ): NomadMessageToInsert = NomadMessageToInsert(
        id = id,
        conversationId = qid(conversationId),
        date = Instant.fromEpochMilliseconds(timestampMs),
        payload = SyncableMessagePayloadEntity.Text(
            creationDate = Instant.fromEpochMilliseconds(timestampMs),
            senderUserId = qid(senderId),
            senderClientId = "sender-client",
            lastEditDate = null,
            text = "text-$id",
            quotedMessageId = null,
            mentions = emptyList()
        ),
    )

    private fun multipartMessageWithDuplicateAttachmentIds(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
    ): NomadMessageToInsert = multipartMessage(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        timestampMs = timestampMs,
        attachmentAssetIds = listOf("duplicate-asset", "duplicate-asset")
    )

    private fun multipartMessage(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
        attachmentAssetIds: List<String>,
    ): NomadMessageToInsert = NomadMessageToInsert(
        id = id,
        conversationId = qid(conversationId),
        date = Instant.fromEpochMilliseconds(timestampMs),
        payload = SyncableMessagePayloadEntity.Multipart(
            creationDate = Instant.fromEpochMilliseconds(timestampMs),
            senderUserId = qid(senderId),
            senderClientId = "sender-client",
            lastEditDate = null,
            text = "multipart-$id",
            quotedMessageId = null,
            mentions = emptyList(),
            attachments = attachmentAssetIds.map { attachment(assetId = it) }
        ),
    )

    private fun assetMessage(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
    ): NomadMessageToInsert = NomadMessageToInsert(
        id = id,
        conversationId = qid(conversationId),
        date = Instant.fromEpochMilliseconds(timestampMs),
        payload = SyncableMessagePayloadEntity.Asset(
            creationDate = Instant.fromEpochMilliseconds(timestampMs),
            senderUserId = qid(senderId),
            senderClientId = "sender-client",
            lastEditDate = null,
            mimeType = "image/png",
            size = 42L,
            name = "asset-$id.png",
            otrKey = byteArrayOf(1),
            sha256 = byteArrayOf(2),
            assetId = "asset-$id",
            assetToken = null,
            assetDomain = null,
            encryptionAlgorithm = "A256GCM",
            width = 320,
            height = 200,
            durationMs = null,
            normalizedLoudness = null
        ),
    )

    private fun locationMessage(
        id: String,
        conversationId: String,
        senderId: String,
        timestampMs: Long,
    ): NomadMessageToInsert = NomadMessageToInsert(
        id = id,
        conversationId = qid(conversationId),
        date = Instant.fromEpochMilliseconds(timestampMs),
        payload = SyncableMessagePayloadEntity.Location(
            creationDate = Instant.fromEpochMilliseconds(timestampMs),
            senderUserId = qid(senderId),
            senderClientId = "sender-client",
            lastEditDate = null,
            latitude = 52.52f,
            longitude = 13.4f,
            name = "Berlin",
            zoom = 15,
        ),
    )

    private fun attachment(assetId: String): MessageAttachmentEntity = MessageAttachmentEntity(
        assetId = assetId,
        cellAsset = true,
        mimeType = "image/png",
        assetPath = "asset-$assetId.png",
        assetSize = 12L,
        assetWidth = null,
        assetHeight = null,
        assetDuration = null,
        assetTransferStatus = "NOT_DOWNLOADED",
        isEditSupported = false
    )

    private fun qid(value: String): QualifiedIDEntity = QualifiedIDEntity(value, TEST_DOMAIN)

    private companion object {
        const val TEST_DOMAIN = "wire.test"
        val SELF_USER_ID = QualifiedIDEntity("self-user", TEST_DOMAIN)
        val SELF_USER_ID_DAO = SELF_USER_ID
    }
}
