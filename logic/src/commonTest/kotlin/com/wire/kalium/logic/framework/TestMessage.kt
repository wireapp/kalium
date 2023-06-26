/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.BroadcastMessage
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.datetime.toInstant

object TestMessage {
    const val TEST_MESSAGE_ID = "messageId"
    const val TEST_DATE_STRING = "2000-01-01T12:00:00.000Z"
    val TEST_MESSAGE_SENT = MessageSent(TEST_DATE_STRING)
    val TEST_DATE = TEST_DATE_STRING.toInstant()
    val TEST_SENDER_USER_ID = TestUser.USER_ID
    val TEST_SENDER_CLIENT_ID = TestClient.CLIENT_ID
    val TEXT_CONTENT = MessageContent.Text("Ciao!")
    val ASSET_CONTENT = MessageContent.Asset(
        AssetContent(
            sizeInBytes = 0,
            name = null,
            mimeType = "",
            metadata = null,
            remoteData = AssetContent.RemoteData(
                otrKey = byteArrayOf(),
                sha256 = byteArrayOf(),
                assetId = "",
                assetToken = null,
                assetDomain = null,
                encryptionAlgorithm = null
            ),
            downloadStatus = Message.DownloadStatus.SAVED_INTERNALLY
        )
    )
    val TEXT_MESSAGE = Message.Regular(
        id = TEST_MESSAGE_ID,
        content = TEXT_CONTENT,
        conversationId = TestConversation.ID,
        date = TEST_DATE_STRING,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.PENDING,
        editStatus = Message.EditStatus.NotEdited,
        isSelfMessage = false
    )

    val MISSED_CALL_MESSAGE = Message.System(
        id = TEST_MESSAGE_ID,
        content = MessageContent.MissedCall,
        conversationId = ConversationId("conv", "id"),
        date = TEST_DATE_STRING,
        senderUserId = TEST_SENDER_USER_ID,
        status = Message.Status.PENDING,
    )

    fun assetMessage(assetId: String = TEST_MESSAGE_ID) = Message.Regular(
        id = TEST_MESSAGE_ID,
        content = ASSET_CONTENT,
        conversationId = ConversationId("conv", "id"),
        date = TEST_DATE_STRING,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.PENDING,
        editStatus = Message.EditStatus.NotEdited,
        isSelfMessage = false
    )

    val ENTITY = MessageEntity.Regular(
        TEST_MESSAGE_ID,
        TestConversation.ENTITY_ID,
        date = TEST_DATE,
        senderUserId = TestUser.ENTITY_ID,
        status = MessageEntity.Status.SENT,
        visibility = MessageEntity.Visibility.VISIBLE,
        content = MessageEntityContent.Text("Ciao!"),
        senderClientId = "clientId",
        editStatus = MessageEntity.EditStatus.NotEdited,
        senderName = "senderName"
    )

    val BROADCAST_MESSAGE = BroadcastMessage(
        id = TEST_MESSAGE_ID,
        content = MessageContent.Availability(UserAvailabilityStatus.AVAILABLE),
        date = TEST_DATE_STRING,
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.PENDING,
        isSelfMessage = false
    )

    fun signalingMessage(
        content: MessageContent.Signaling
    ) = Message.Signaling(
        id = TEST_MESSAGE_ID,
        content = content,
        conversationId = TestConversation.ID,
        date = "currentDate",
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.SENT,
        isSelfMessage = false
    )
}
