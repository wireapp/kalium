package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.datetime.toInstant

object TestMessage {
    const val TEST_MESSAGE_ID = "messageId"
    const val TEST_DATE_STRING = "2000-01-01T12:00:00.000Z"
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
        editStatus = Message.EditStatus.NotEdited
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
        editStatus = Message.EditStatus.NotEdited
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

    fun signalingMessage(
        content: MessageContent.Signaling
    ) = Message.Signaling(
        id = TEST_MESSAGE_ID,
        content = content,
        conversationId = TestConversation.ID,
        date = "currentDate",
        senderUserId = TEST_SENDER_USER_ID,
        senderClientId = TEST_SENDER_CLIENT_ID,
        status = Message.Status.SENT
    )
}
