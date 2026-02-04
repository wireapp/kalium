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

import com.wire.kalium.network.api.authenticated.remoteBackup.DeleteMessagesResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncFetchResponseDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.MessageSyncRequestDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBackupEventDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBAckupMessageContentDTO
import com.wire.kalium.network.api.authenticated.remoteBackup.RemoteBackupPayloadDTO
import com.wire.kalium.network.api.model.QualifiedID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object RemoteBackupResponseJson {

    private const val TEST_USER_ID = "user-123-abc"
    private const val TEST_CONVERSATION_ID = "conv-456-def"
    private const val TEST_DOMAIN = "wire.com"

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

    private val testUpsert = RemoteBackupEventDTO.Upsert(
        messageId = "msg-1",
        timestamp = -62135596800000L, // Instant.DISTANT_PAST
        payload = testPayload
    )

    private val testDelete = RemoteBackupEventDTO.Delete(
        conversationId = TEST_CONVERSATION_ID,
        messageId = "deleted-msg-1"
    )

    private val testLastRead = RemoteBackupEventDTO.LastRead(
        conversationId = TEST_CONVERSATION_ID,
        lastRead = 1234567890L
    )

    private val testSyncRequest = MessageSyncRequestDTO(
        userId = TEST_USER_ID,
        events = listOf(
            testUpsert,
            testDelete,
            testLastRead
        )
    )

    private val testFetchResponse = MessageSyncFetchResponseDTO(
        hasMore = true,
        events = listOf(
            testUpsert,
            testDelete,
            testLastRead
        ),
        paginationToken = "next-token"
    )

    private val testDeleteResponse = DeleteMessagesResponseDTO(
        deletedCount = 42
    )

    val validSyncRequest = testSyncRequest

    val validFetchResponse = testFetchResponse

    val validDeleteResponse = ValidJsonProvider(testDeleteResponse, deleteResponseJsonProvider)
}
