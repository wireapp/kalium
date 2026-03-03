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

package com.wire.kalium.nomaddevice

import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationWithMessages
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAttachment
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceImageMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceLocation
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMultipart
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceText
import kotlinx.datetime.Instant
import pbandk.ByteArr
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NomadAllMessagesMapperTest {

    private val mapper = NomadAllMessagesMapper()

    @Test
    fun givenAssetLocationAndMultipartMessages_whenMapping_thenSupportedTypesAreMapped() {
        val mapped = mapper.map(
            responseWith(
                storedMessage(
                    messageId = "asset-message",
                    timestamp = 1_707_235_200_000L,
                    content = assetContent(assetId = "asset-1", fileName = "asset-1.png")
                ),
                storedMessage(
                    messageId = "location-message",
                    timestamp = 1_707_235_300_000L,
                    content = locationContent()
                ),
                storedMessage(
                    messageId = "multipart-message",
                    timestamp = 1_707_235_400_000L,
                    content = multipartContent(attachmentAssetId = "multipart-attachment")
                ),
            )
        )

        assertEquals(3, mapped.totalMessages)
        assertEquals(3, mapped.messages.size)
        assertEquals(0, mapped.skippedMessages)

        val assetPayload = assertIs<SyncableMessagePayloadEntity.Asset>(
            mapped.messages.single { it.id == "asset-message" }.payload
        )
        assertEquals("image/png", assetPayload.mimeType)
        assertEquals(64L, assetPayload.size)
        assertEquals("asset-1", assetPayload.assetId)
        assertEquals("asset-1.png", assetPayload.name)
        assertContentEquals(byteArrayOf(1), assetPayload.otrKey)
        assertContentEquals(byteArrayOf(2), assetPayload.sha256)
        assertEquals(320, assetPayload.width)
        assertEquals(240, assetPayload.height)

        val locationPayload = assertIs<SyncableMessagePayloadEntity.Location>(
            mapped.messages.single { it.id == "location-message" }.payload
        )
        assertEquals(52.52f, locationPayload.latitude)
        assertEquals(13.40f, locationPayload.longitude)
        assertEquals("Berlin", locationPayload.name)
        assertEquals(15, locationPayload.zoom)

        val multipartPayload = assertIs<SyncableMessagePayloadEntity.Multipart>(
            mapped.messages.single { it.id == "multipart-message" }.payload
        )
        assertEquals("multipart-text", multipartPayload.text)
        assertEquals("quoted-id", multipartPayload.quotedMessageId)
        assertEquals(1, multipartPayload.attachments.size)
        val attachment = multipartPayload.attachments.single()
        assertEquals("multipart-attachment", attachment.assetId)
        assertEquals("multipart-attachment.png", attachment.assetPath)
        assertEquals(12L, attachment.assetSize)
        assertEquals(128, attachment.assetWidth)
        assertEquals(72, attachment.assetHeight)
    }

    @Test
    fun givenSecondsBasedTimestamps_whenMapping_thenTimestampsAreInterpretedAsSeconds() {
        val mapped = mapper.map(
            responseWith(
                storedMessage(
                    messageId = "seconds-message",
                    timestamp = 1_707_235_200L,
                    creationDate = 1_707_235_201L,
                    lastEditTime = 1_707_235_202L,
                    content = textContent("seconds")
                )
            )
        )

        val message = mapped.messages.single()
        val payload = assertIs<SyncableMessagePayloadEntity.Text>(message.payload)
        assertEquals(Instant.fromEpochSeconds(1_707_235_200L), message.date)
        assertEquals(Instant.fromEpochSeconds(1_707_235_201L), payload.creationDate)
        assertEquals(Instant.fromEpochSeconds(1_707_235_202L), payload.lastEditDate)
    }

    @Test
    fun givenMillisecondsBasedTimestamps_whenMapping_thenTimestampsAreInterpretedAsMilliseconds() {
        val mapped = mapper.map(
            responseWith(
                storedMessage(
                    messageId = "millis-message",
                    timestamp = 1_707_235_200_000L,
                    creationDate = 1_707_235_201_000L,
                    lastEditTime = 1_707_235_202_000L,
                    content = textContent("millis")
                )
            )
        )

        val message = mapped.messages.single()
        val payload = assertIs<SyncableMessagePayloadEntity.Text>(message.payload)
        assertEquals(Instant.fromEpochMilliseconds(1_707_235_200_000L), message.date)
        assertEquals(Instant.fromEpochMilliseconds(1_707_235_201_000L), payload.creationDate)
        assertEquals(Instant.fromEpochMilliseconds(1_707_235_202_000L), payload.lastEditDate)
    }

    private fun responseWith(vararg messages: NomadStoredMessage): NomadAllMessagesResponse =
        NomadAllMessagesResponse(
            conversations = listOf(
                NomadConversationWithMessages(
                    conversation = Conversation(id = CONVERSATION_ID, domain = TEST_DOMAIN),
                    messages = messages.toList()
                )
            )
        )

    private fun storedMessage(
        messageId: String,
        timestamp: Long,
        content: NomadDeviceMessageContent,
        creationDate: Long = timestamp,
        lastEditTime: Long? = null,
    ): NomadStoredMessage {
        val payload = NomadDeviceMessagePayload(
            senderUserId = NomadDeviceQualifiedId(value = SENDER_ID, domain = TEST_DOMAIN),
            senderClientId = "sender-client",
            creationDate = creationDate,
            content = content,
            lastEditTime = lastEditTime
        )
        return NomadStoredMessage(
            messageId = messageId,
            timestamp = timestamp,
            payload = Base64.Default.encode(payload.encodeToByteArray())
        )
    }

    private fun textContent(text: String): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Text(
                NomadDeviceText(
                    text = text,
                    mentions = emptyList(),
                    quotedMessageId = null
                )
            )
        )

    private fun assetContent(assetId: String, fileName: String): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Asset(
                NomadDeviceAsset(
                    mimeType = "image/png",
                    size = 64L,
                    name = fileName,
                    otrKey = ByteArr(byteArrayOf(1)),
                    sha256 = ByteArr(byteArrayOf(2)),
                    assetId = assetId,
                    metaData = NomadDeviceAsset.MetaData.Image(
                        NomadDeviceImageMetaData(
                            width = 320,
                            height = 240
                        )
                    )
                )
            )
        )

    private fun locationContent(): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Location(
                NomadDeviceLocation(
                    longitude = 13.40f,
                    latitude = 52.52f,
                    name = "Berlin",
                    zoom = 15
                )
            )
        )

    private fun multipartContent(attachmentAssetId: String): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Multipart(
                NomadDeviceMultipart(
                    text = NomadDeviceText(
                        text = "multipart-text",
                        mentions = emptyList(),
                        quotedMessageId = "quoted-id"
                    ),
                    attachments = listOf(
                        NomadDeviceAttachment(
                            content = NomadDeviceAttachment.Content.Asset(
                                NomadDeviceAsset(
                                    mimeType = "image/png",
                                    size = 12L,
                                    name = "$attachmentAssetId.png",
                                    otrKey = ByteArr(byteArrayOf(9)),
                                    sha256 = ByteArr(byteArrayOf(8)),
                                    assetId = attachmentAssetId,
                                    metaData = NomadDeviceAsset.MetaData.Image(
                                        NomadDeviceImageMetaData(
                                            width = 128,
                                            height = 72
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

    private companion object {
        const val TEST_DOMAIN = "wire.test"
        const val CONVERSATION_ID = "conversation-id"
        const val SENDER_ID = "sender-id"
    }
}
