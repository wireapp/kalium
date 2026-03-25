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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadata
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataItem
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SyncNomadConversationMetadataUseCaseTest {

    @Test
    fun givenMetadataResponse_whenInvoking_thenApplyLastReadDates() = runTest {
        val repository = FakeNomadConversationMetadataSyncRepository(
            metadataResponse = Either.Right(metadataResponse()),
            updatedConversations = 1
        )
        val useCase = SyncNomadConversationMetadataUseCase(
            repository = repository
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadConversationMetadataSyncResult>>(result).value
        assertEquals(1, success.downloadedConversations)
        assertEquals(1, success.updatedConversations)
        assertEquals(0, success.skippedConversations)
        assertEquals(1, repository.appliedMetadata.size)
        assertEquals(qid(CONVERSATION_ID), repository.appliedMetadata.single().conversationId)
        assertEquals(Instant.fromEpochMilliseconds(LAST_READ_TIMESTAMP), repository.appliedMetadata.single().lastReadDate)
        assertEquals(Instant.fromEpochMilliseconds(LAST_MODIFIED_TIMESTAMP), repository.appliedMetadata.single().lastModifiedDate)
    }

    @Test
    fun givenMissingStorage_whenInvoking_thenSkipUpdates() = runTest {
        val repository = FakeNomadConversationMetadataSyncRepository(
            metadataResponse = Either.Right(metadataResponse()),
            updatedConversations = 0
        )
        val useCase = SyncNomadConversationMetadataUseCase(
            repository = repository
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadConversationMetadataSyncResult>>(result).value
        assertEquals(1, success.downloadedConversations)
        assertEquals(0, success.updatedConversations)
        assertEquals(1, success.skippedConversations)
    }

    @Test
    fun givenApiFailure_whenInvoking_thenDoNotTouchStorage() = runTest {
        val repository = FakeNomadConversationMetadataSyncRepository(
            metadataResponse = Either.Left(NetworkFailure.NoNetworkConnection(null)),
            updatedConversations = 0
        )
        val useCase = SyncNomadConversationMetadataUseCase(
            repository = repository
        )

        val result = useCase(SELF_USER_ID)

        assertIs<Either.Left<*>>(result)
        assertIs<NetworkFailure.NoNetworkConnection>((result as Either.Left).value)
        assertEquals(emptyList(), repository.appliedMetadata)
    }

    private class FakeNomadConversationMetadataSyncRepository(
        private val metadataResponse: Either<CoreFailure, NomadConversationMetadataResponse>,
        private val updatedConversations: Int,
    ) : NomadConversationMetadataSyncRepository {
        val appliedMetadata = mutableListOf<NomadConversationMetadataToSync>()

        override suspend fun getConversationMetadata(selfUserId: UserId): Either<CoreFailure, NomadConversationMetadataResponse> =
            metadataResponse

        override suspend fun applyMetadata(
            selfUserId: UserId,
            metadata: List<NomadConversationMetadataToSync>,
        ): Either<CoreFailure, Int> {
            appliedMetadata += metadata
            return Either.Right(updatedConversations)
        }
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

    private companion object {
        val SELF_USER_ID = UserId("self-user", "wire.test")
        const val CONVERSATION_ID = "conversation-id"
        const val CONVERSATION_DOMAIN = "wire.test"
        const val LAST_READ_TIMESTAMP = 1_707_235_200_000L
        const val LAST_MODIFIED_TIMESTAMP = 1_707_235_300_000L
    }
}

private fun qid(value: String): QualifiedIDEntity = QualifiedIDEntity(value = value, domain = "wire.test")
