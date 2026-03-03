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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationWithMessages
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.backup.NomadMessageStoreResult
import com.wire.kalium.persistence.dao.backup.NomadMessageToInsert
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceText
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncNomadAllMessagesUseCaseTest {

    @Test
    fun givenNomadMessages_whenImporting_thenTheyAreMappedAndStoredInBatches() = runTest {
        val response = NomadAllMessagesResponse(
            conversations = listOf(
                NomadConversationWithMessages(
                    conversation = Conversation(id = CONVERSATION_ID, domain = CONVERSATION_DOMAIN),
                    messages = listOf(
                        nomadStoredMessage(messageId = "msg-1", sender = SENDER_ID, text = "hello"),
                        nomadStoredMessage(messageId = "msg-2", sender = SENDER_ID, text = "second"),
                        nomadStoredMessage(messageId = "msg-3", sender = SELF_USER_ID, text = "self"),
                    )
                )
            )
        )
        val fakeDao = FakeNomadMessagesDAO()
        val useCase = SyncNomadAllMessagesUseCase(
            nomadDeviceSyncApiProvider = { FakeNomadDeviceSyncApi(NetworkResponse.Success(response, emptyMap(), 200)) },
            nomadMessagesDAOProvider = { fakeDao },
            batchSize = 2
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadAllMessagesSyncResult>>(result).value
        assertEquals(3, success.downloadedMessages)
        assertEquals(3, success.storedMessages)
        assertEquals(0, success.skippedMessages)
        assertEquals(2, success.batches)
        assertEquals(2, fakeDao.batchSize)
        assertEquals(3, fakeDao.messages.size)
        assertEquals("hello", (fakeDao.messages.first().payload as SyncableMessagePayloadEntity.Text).text)
    }

    @Test
    fun givenInvalidPayload_whenImporting_thenItIsSkipped() = runTest {
        val response = NomadAllMessagesResponse(
            conversations = listOf(
                NomadConversationWithMessages(
                    conversation = Conversation(id = CONVERSATION_ID, domain = CONVERSATION_DOMAIN),
                    messages = listOf(
                        nomadStoredMessage(messageId = "valid", sender = SENDER_ID, text = "hello"),
                        NomadStoredMessage(
                            messageId = "invalid",
                            timestamp = 1_707_235_200,
                            payload = "###"
                        )
                    )
                )
            )
        )
        val fakeDao = FakeNomadMessagesDAO()
        val useCase = SyncNomadAllMessagesUseCase(
            nomadDeviceSyncApiProvider = { FakeNomadDeviceSyncApi(NetworkResponse.Success(response, emptyMap(), 200)) },
            nomadMessagesDAOProvider = { fakeDao },
            batchSize = 50
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadAllMessagesSyncResult>>(result).value
        assertEquals(2, success.downloadedMessages)
        assertEquals(1, success.storedMessages)
        assertEquals(1, success.skippedMessages)
        assertEquals(1, success.batches)
        assertEquals(1, fakeDao.messages.size)
    }

    @Test
    fun givenMissingUserStorage_whenImporting_thenMessagesAreNotStored() = runTest {
        val response = NomadAllMessagesResponse(
            conversations = listOf(
                NomadConversationWithMessages(
                    conversation = Conversation(id = CONVERSATION_ID, domain = CONVERSATION_DOMAIN),
                    messages = listOf(nomadStoredMessage(messageId = "msg-1", sender = SENDER_ID, text = "hello"))
                )
            )
        )
        val useCase = SyncNomadAllMessagesUseCase(
            nomadDeviceSyncApiProvider = { FakeNomadDeviceSyncApi(NetworkResponse.Success(response, emptyMap(), 200)) },
            nomadMessagesDAOProvider = { null },
            batchSize = 10
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadAllMessagesSyncResult>>(result).value
        assertEquals(1, success.downloadedMessages)
        assertEquals(0, success.storedMessages)
        assertEquals(1, success.skippedMessages)
        assertEquals(0, success.batches)
    }

    @Test
    fun givenApiFailure_whenImporting_thenStorageIsNotCalled() = runTest {
        val fakeDao = FakeNomadMessagesDAO()
        val useCase = SyncNomadAllMessagesUseCase(
            nomadDeviceSyncApiProvider = { FakeNomadDeviceSyncApi(NetworkResponse.Error(KaliumException.NoNetwork())) },
            nomadMessagesDAOProvider = { fakeDao },
            batchSize = 10
        )

        val result = useCase(SELF_USER_ID)

        assertIs<Either.Left<*>>(result)
        assertIs<NetworkFailure.NoNetworkConnection>((result as Either.Left).value)
        assertTrue(fakeDao.messages.isEmpty())
    }

    private class FakeNomadMessagesDAO : NomadMessagesDAO {
        var batchSize: Int = 0
        val messages = mutableListOf<NomadMessageToInsert>()

        override suspend fun storeMessages(
            messages: List<NomadMessageToInsert>,
            batchSize: Int,
        ): NomadMessageStoreResult {
            this.batchSize = batchSize
            this.messages += messages
            val batches = if (messages.isEmpty()) 0 else (messages.size + batchSize - 1) / batchSize
            return NomadMessageStoreResult(storedMessages = messages.size, batches = batches)
        }
    }

    private class FakeNomadDeviceSyncApi(
        private val allMessagesResponse: NetworkResponse<NomadAllMessagesResponse>
    ) : NomadDeviceSyncApi {
        override suspend fun postMessageEvents(
            request: com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
        ): NetworkResponse<Unit> {
            error("Not needed in this test")
        }

        override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> = allMessagesResponse

        override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> {
            error("Not needed in this test")
        }
    }

    private fun nomadStoredMessage(
        messageId: String,
        sender: UserId,
        text: String,
    ): NomadStoredMessage {
        val payload = NomadDeviceMessagePayload(
            senderUserId = NomadDeviceQualifiedId(value = sender.value, domain = sender.domain),
            senderClientId = "sender-client",
            creationDate = 1_707_235_200_000,
            content = NomadDeviceMessageContent(
                content = NomadDeviceMessageContent.Content.Text(
                    NomadDeviceText(
                        text = text,
                        mentions = emptyList(),
                        quotedMessageId = null
                    )
                )
            ),
            lastEditTime = null
        )
        return NomadStoredMessage(
            messageId = messageId,
            timestamp = 1_707_235_200,
            payload = Base64.Default.encode(payload.encodeToByteArray())
        )
    }

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.test")
        val SENDER_ID = UserId("sender-user", "wire.test")
        const val CONVERSATION_ID = "conversation-id"
        const val CONVERSATION_DOMAIN = "wire.test"
    }
}
