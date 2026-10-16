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
package com.wire.kalium.persistence.dao.message.attachment

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.utils.IgnoreJS
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

// sql.js surfaces the query's expression columns as null, which breaks the generated mapper.
@IgnoreJS
class MessageAttachmentsDaoTest : BaseDatabaseTest() {

    private lateinit var messageAttachmentsDao: MessageAttachmentsDao
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")
    private val senderId = UserIDEntity("user", "domain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageAttachmentsDao = db.messageAttachments
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenAttachmentsInDb_whenGettingByAssetIds_thenOnlyRequestedAttachmentsAreReturned() = runTest {
        insertMessageWithAttachments("message_1", "asset_1", "asset_2", "asset_3")

        val result = messageAttachmentsDao.getAttachmentsByAssetIds(listOf("asset_1", "asset_3", "missing"))

        assertEquals(setOf("asset_1", "asset_3"), result.map { it.assetId }.toSet())
    }

    @Test
    fun givenAssetAttachedToSeveralMessages_whenGettingByAssetIds_thenARowIsReturnedPerMessage() = runTest {
        insertMessageWithAttachments("message_1", "asset_1")
        insertMessageWithAttachments("message_2", "asset_1")

        val result = messageAttachmentsDao.getAttachmentsByAssetIds(listOf("asset_1"))

        assertEquals(listOf("asset_1", "asset_1"), result.map { it.assetId })
    }

    @Test
    fun givenEmptyAssetIds_whenGettingByAssetIds_thenEmptyListIsReturned() = runTest {
        insertMessageWithAttachments("message_1", "asset_1")

        val result = messageAttachmentsDao.getAttachmentsByAssetIds(emptyList())

        assertEquals(emptyList(), result)
    }

    private suspend fun insertMessageWithAttachments(messageId: String, vararg assetIds: String) {
        userDAO.upsertUser(newUserEntity(qualifiedID = senderId))
        conversationDAO.insertConversation(newConversationEntity(id = CONVERSATION_ID))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = messageId,
                conversationId = CONVERSATION_ID,
                senderUserId = senderId,
                content = MessageEntityContent.Multipart(
                    messageBody = "multipart",
                    attachments = assetIds.mapIndexed { index, assetId -> attachment(assetId, index) }
                )
            )
        )
    }

    private fun attachment(assetId: String, index: Int) = MessageAttachmentEntity(
        assetId = assetId,
        cellAsset = true,
        mimeType = "image/png",
        assetPath = null,
        assetSize = 128,
        localPath = "/tmp/$assetId",
        previewUrl = null,
        assetWidth = 64,
        assetHeight = 64,
        assetDuration = null,
        assetTransferStatus = "SAVED_INTERNALLY",
        contentUrl = null,
        contentHash = null,
        assetIndex = index,
        contentExpiresAt = null,
        isEditSupported = false,
    )

    private companion object {
        val CONVERSATION_ID = ConversationIDEntity("conversation", "domain")
    }
}