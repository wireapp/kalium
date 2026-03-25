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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadata
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataItem
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Sink
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NomadConversationMetadataSyncRepositoryTest {

    @Test
    fun givenApiResponse_whenFetchingMetadata_thenReturnIt() = runTest {
        val repository = NomadConversationMetadataSyncDataSource(
            nomadDeviceSyncApiProvider = {
                FakeNomadDeviceSyncApi(NetworkResponse.Success(metadataResponse(), emptyMap(), 200))
            },
            metadataStoreProvider = { null }
        )

        val result = repository.getConversationMetadata(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadConversationMetadataResponse>>(result).value
        assertEquals(metadataResponse(), success)
    }

    @Test
    fun givenMissingStore_whenApplyingMetadata_thenReturnZero() = runTest {
        val repository = NomadConversationMetadataSyncDataSource(
            nomadDeviceSyncApiProvider = {
                FakeNomadDeviceSyncApi(NetworkResponse.Success(metadataResponse(), emptyMap(), 200))
            },
            metadataStoreProvider = { null }
        )

        val result = repository.applyMetadata(SELF_USER_ID, metadataToSync())

        assertEquals(0, assertIs<Either.Right<Int>>(result).value)
    }

    @Test
    fun givenStore_whenApplyingMetadata_thenForwardAndReturnCount() = runTest {
        val store = FakeNomadConversationMetadataStore(updatedConversations = 2)
        val repository = NomadConversationMetadataSyncDataSource(
            nomadDeviceSyncApiProvider = {
                FakeNomadDeviceSyncApi(NetworkResponse.Success(metadataResponse(), emptyMap(), 200))
            },
            metadataStoreProvider = { store }
        )

        val result = repository.applyMetadata(SELF_USER_ID, metadataToSync())

        assertEquals(2, assertIs<Either.Right<Int>>(result).value)
        assertEquals(metadataToSync(), store.appliedMetadata)
    }

    private class FakeNomadConversationMetadataStore(
        private val updatedConversations: Int,
    ) : NomadConversationMetadataStore {
        var appliedMetadata: List<NomadConversationMetadataToSync> = emptyList()

        override suspend fun applyMetadata(metadata: List<NomadConversationMetadataToSync>): Int {
            appliedMetadata = metadata
            return updatedConversations
        }
    }

    private class FakeNomadDeviceSyncApi(
        private val metadataResponse: NetworkResponse<NomadConversationMetadataResponse>
    ) : NomadDeviceSyncApi {
        override suspend fun postMessageEvents(
            request: com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
        ): NetworkResponse<Unit> = error("Not needed in this test")

        override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> =
            error("Not needed in this test")

        override suspend fun syncAllMessages(limit: Int): NetworkResponse<NomadAllMessagesResponse> =
            error("Not needed in this test")

        override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> = metadataResponse

        override suspend fun uploadCryptoState(
            clientId: String,
            backupSource: () -> Source,
            backupSize: Long
        ): NetworkResponse<Unit> = error("Not needed in this test")

        override suspend fun downloadCryptoState(tempBackupFileSink: Sink): NetworkResponse<Unit> =
            error("Not needed in this test")

        override suspend fun setLastDeviceId(deviceId: String): NetworkResponse<Unit> =
            error("Not needed in this test")
    }

    private fun metadataResponse(): NomadConversationMetadataResponse =
        NomadConversationMetadataResponse(
            conversations = listOf(
                NomadConversationMetadataItem(
                    conversation = Conversation(id = CONVERSATION_ID, domain = CONVERSATION_DOMAIN),
                    metadata = NomadConversationMetadata(lastRead = LAST_READ_TIMESTAMP, lastModified = LAST_MODIFIED_TIMESTAMP)
                )
            )
        )

    private fun metadataToSync(): List<NomadConversationMetadataToSync> =
        listOf(
            NomadConversationMetadataToSync(
                conversationId = QualifiedIDEntity(CONVERSATION_ID, CONVERSATION_DOMAIN),
                lastReadDate = Instant.fromEpochMilliseconds(LAST_READ_TIMESTAMP),
                lastModifiedDate = Instant.fromEpochMilliseconds(LAST_MODIFIED_TIMESTAMP)
            ),
            NomadConversationMetadataToSync(
                conversationId = QualifiedIDEntity("conversation-id-2", CONVERSATION_DOMAIN),
                lastReadDate = Instant.fromEpochMilliseconds(LAST_READ_TIMESTAMP + 1_000),
                lastModifiedDate = Instant.fromEpochMilliseconds(LAST_MODIFIED_TIMESTAMP + 1_000)
            )
        )

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.test")
        const val CONVERSATION_ID = "conversation-id"
        const val CONVERSATION_DOMAIN = "wire.test"
        const val LAST_READ_TIMESTAMP = 1_707_235_200_000L
        const val LAST_MODIFIED_TIMESTAMP = 1_707_235_300_000L
    }
}
