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
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MessageInsertExtensionTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private lateinit var messageExtensions: MessageInsertExtension

    private val conversationEntity1 = newConversationEntity("Test1")
    private val userEntity1 = newUserEntity("userEntity1")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        messageExtensions = MessageInsertExtensionImpl(
            db.database.messagesQueries,
            db.database.messageAttachmentsQueries,
            db.database.unreadEventsQueries,
            db.database.conversationsQueries,
            db.database.buttonContentQueries,
            selfUserId
        )
    }

    @Test
    fun givenDeletedAssetMessage_whenUpdateUploadStatus_thenFail() = runTest {
        conversationDAO.insertConversation(conversationEntity1)
        userDAO.upsertUser(userEntity1)
        val assetMessage = newRegularMessageEntity(
            id = "messageId",
            date = Instant.DISTANT_PAST,
            conversationId = conversationEntity1.id,
            senderUserId = userEntity1.id,
            senderClientId = "client1",
            visibility = MessageEntity.Visibility.VISIBLE,
            content = MessageEntityContent.Asset(
                assetSizeInBytes = 0,
                assetMimeType = "*/*",
                assetOtrKey = "some-otr-key".encodeToByteArray(),
                assetSha256Key = "some-sha256-key".encodeToByteArray(),
                assetId = "some-asset-id",
                assetEncryptionAlgorithm = "AES/GCM"
            )
        )

        messageDAO.insertOrIgnoreMessage(assetMessage)
        messageDAO.markMessageAsDeleted(assetMessage.id, conversationEntity1.id)

        val deletedMessage = messageDAO.getMessageById(assetMessage.id, conversationEntity1.id)

        messageExtensions.updateAssetMessage(assetMessage)

        messageDAO.getMessageById(assetMessage.id, conversationEntity1.id).also {
            assertEquals(deletedMessage, it)
        }
    }
}
