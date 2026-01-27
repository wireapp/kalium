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

package com.wire.kalium.mocks.responses

import com.wire.kalium.network.api.authenticated.remoteBackup.ConversationMessagesDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncRequestDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncResultDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncUpsertDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBAckupMessageContentDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBackupPayloadDTO
import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RemoteBackupResponseJson {

    private const val TEST_USER_ID = "user-123-abc"
    private const val TEST_CONVERSATION_ID = "conv-456-def"
    private const val TEST_DOMAIN = "wire.com"

    private fun qualifiedIdToJson(qualifiedId: QualifiedID): JsonObject = buildJsonObject {
        put("id", qualifiedId.value)
        put("domain", qualifiedId.domain)
    }

    private fun messageContentToJson(content: RemoteBAckupMessageContentDTO): JsonObject = when (content) {
        is RemoteBAckupMessageContentDTO.Text -> buildJsonObject {
            put("type", "text")
            put("text", content.text)
            if (content.mentions.isNotEmpty()) {
                put("mentions", buildJsonArray {
                    content.mentions.forEach { mention ->
                        add(buildJsonObject {
                            put("userId", qualifiedIdToJson(mention.userId))
                            put("start", mention.start)
                            put("length", mention.length)
                        })
                    }
                })
            }
            content.quotedMessageId?.let { put("quotedMessageId", it) }
        }
        is RemoteBAckupMessageContentDTO.Asset -> buildJsonObject {
            put("type", "asset")
            put("mimeType", content.mimeType)
            put("size", content.size)
            content.name?.let { put("name", it) }
            put("otrKey", content.otrKey)
            put("sha256", content.sha256)
            put("assetId", content.assetId)
            content.assetToken?.let { put("assetToken", it) }
            content.assetDomain?.let { put("assetDomain", it) }
            content.encryption?.let { put("encryption", it) }
        }
        is RemoteBAckupMessageContentDTO.Location -> buildJsonObject {
            put("type", "location")
            put("longitude", content.longitude)
            put("latitude", content.latitude)
            content.name?.let { put("name", it) }
            content.zoom?.let { put("zoom", it) }
        }
    }

    private fun payloadToJson(payload: RemoteBackupPayloadDTO): JsonObject = buildJsonObject {
        put("id", payload.id)
        put("conversationId", qualifiedIdToJson(payload.conversationId))
        put("senderUserId", qualifiedIdToJson(payload.senderUserId))
        put("senderClientId", payload.senderClientId)
        put("creationDate", payload.creationDate)
        put("content", messageContentToJson(payload.content))
        payload.lastEditTime?.let { put("lastEditTime", it) }
    }

    private fun upsertToJson(upsert: MessageSyncUpsertDTO): JsonObject = buildJsonObject {
        put("message_id", upsert.messageId)
        put("timestamp", upsert.timestamp)
        put("payload", payloadToJson(upsert.payload))
    }

    private val syncRequestJsonProvider = { request: MessageSyncRequestDTO ->
        buildJsonObject {
            put("user_id", request.userId)
            put("upserts", buildJsonObject {
                request.upserts.forEach { (conversationId, upserts) ->
                    put(conversationId, buildJsonArray {
                        upserts.forEach { add(upsertToJson(it)) }
                    })
                }
            })
            put("deletions", buildJsonObject {
                request.deletions.forEach { (conversationId, deletions) ->
                    put(conversationId, JsonArray(deletions.map { JsonPrimitive(it) }))
                }
            })
            put("conversations_last_read", buildJsonObject {
                request.conversationsLastRead.forEach { (conversationId, lastRead) ->
                    put(conversationId, lastRead)
                }
            })
        }.toString()
    }

    private fun messageResultToJson(result: MessageSyncResultDTO): JsonObject = buildJsonObject {
        put("message_id", result.messageId)
        put("timestamp", result.timestamp)
        put("payload", payloadToJson(result.payload))
    }

    private fun conversationMessagesToJson(messages: ConversationMessagesDTO): JsonObject = buildJsonObject {
        put("last_read", messages.lastRead)
        put("messages", buildJsonArray {
            messages.messages.forEach { add(messageResultToJson(it)) }
        })
    }

    private val fetchResponseJsonProvider = { response: MessageSyncFetchResponseDTO ->
        buildJsonObject {
            put("has_more", response.hasMore)
            put("conversations", buildJsonObject {
                response.conversations.forEach { (conversationId, messages) ->
                    put(conversationId, conversationMessagesToJson(messages))
                }
            })
            response.paginationToken?.let { put("pagination_token", it) }
        }.toString()
    }

    private val deleteResponseJsonProvider = { response: DeleteMessagesResponseDTO ->
        buildJsonObject {
            put("deleted_count", response.deletedCount)
        }.toString()
    }

    // Test data
    private val testPayload = RemoteBackupPayloadDTO(
        id = "msg-1",
        conversationId = QualifiedID(value = TEST_CONVERSATION_ID, domain = TEST_DOMAIN),
        senderUserId = QualifiedID(value = TEST_USER_ID, domain = TEST_DOMAIN),
        senderClientId = "client-123",
        creationDate = 999L,
        content = RemoteBAckupMessageContentDTO.Text(text = "Hello")
    )

    private val testUpsert = MessageSyncUpsertDTO(
        messageId = "msg-1",
        timestamp = -62135596800000L, // Instant.DISTANT_PAST
        payload = testPayload
    )

    private val testSyncRequest = MessageSyncRequestDTO(
        userId = TEST_USER_ID,
        upserts = mapOf(
            TEST_CONVERSATION_ID to listOf(testUpsert)
        ),
        deletions = mapOf(
            TEST_CONVERSATION_ID to listOf("deleted-msg-1", "deleted-msg-2")
        ),
        conversationsLastRead = mapOf(
            TEST_CONVERSATION_ID to 1234567890L
        )
    )

    private val testMessageResult = MessageSyncResultDTO(
        messageId = "msg-1",
        timestamp = 999L,
        payload = testPayload
    )

    private val testFetchResponse = MessageSyncFetchResponseDTO(
        hasMore = true,
        conversations = mapOf(
            TEST_CONVERSATION_ID to ConversationMessagesDTO(
                lastRead = 1000L,
                messages = listOf(testMessageResult)
            )
        ),
        paginationToken = "next-token"
    )

    private val testDeleteResponse = DeleteMessagesResponseDTO(
        deletedCount = 42
    )

    val validSyncRequest = ValidJsonProvider(testSyncRequest, syncRequestJsonProvider)

    val validFetchResponse = ValidJsonProvider(testFetchResponse, fetchResponseJsonProvider)

    val validDeleteResponse = ValidJsonProvider(testDeleteResponse, deleteResponseJsonProvider)
}
