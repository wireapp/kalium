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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationMetadataResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogEntry
import com.wire.kalium.persistence.dao.backup.ChangeLogEventType
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncEvent
import com.wire.kalium.persistence.dao.backup.ConversationLastReadSyncEntity
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import pbandk.decodeFromByteArray
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncNomadRemoteBackupChangeLogUseCaseTest {

    @Test
    fun givenPendingBatch_whenSyncSucceeds_thenEventsArePostedAndBatchIsDeleted() = runTest {
        val dao = FakeRemoteBackupChangeLogDAO(
            batch = ChangeLogSyncBatch(
                events = listOf(
                    ChangeLogSyncEvent.MessageUpsert(
                        conversationId = CONVERSATION_ID,
                        messageId = MESSAGE_ID,
                        change = ChangeLogEntry(
                            conversationId = CONVERSATION_ID,
                            messageId = MESSAGE_ID,
                            eventType = ChangeLogEventType.MESSAGE_UPSERT,
                            timestampMs = 2000L,
                            messageTimestampMs = 1500L
                        ),
                        message = SyncableMessagePayloadEntity.Text(
                            creationDate = Instant.fromEpochMilliseconds(1000L),
                            senderUserId = SENDER_ID,
                            senderClientId = "sender-client",
                            lastEditDate = Instant.fromEpochMilliseconds(1200L),
                            text = "hello from db",
                            quotedMessageId = "quoted-message",
                            mentions = listOf(
                                MessageEntity.Mention(
                                    start = 0,
                                    length = 5,
                                    userId = QualifiedIDEntity("mention-user", "wire.test")
                                )
                            )
                        )
                    )
                ),
                conversationLastReads = listOf(
                    ConversationLastReadSyncEntity(
                        conversationId = CONVERSATION_ID,
                        lastReadDate = Instant.parse("2026-02-25T10:15:00Z")
                    )
                )
            )
        )
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Success(Unit, emptyMap(), 200))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { dao },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 50
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadRemoteBackupChangeLogSyncResult>>(result).value
        assertEquals(1, success.syncedEntries)
        assertEquals(2, success.postedEvents)
        assertEquals(1, api.requests.size)
        assertEquals(1, dao.deletedChanges.size)
        assertEquals(ChangeLogEventType.MESSAGE_UPSERT, dao.deletedChanges.first().eventType)

        val request = api.requests.single()
        val upsertEvent = assertIs<NomadMessageEvent.UpsertMessageEvent>(request.events.first())
        assertEquals(MESSAGE_ID, upsertEvent.messageId)
        assertEquals(1500L, upsertEvent.timestamp)
        assertEquals(CONVERSATION_ID.value, upsertEvent.conversation.id)
        assertEquals(CONVERSATION_ID.domain, upsertEvent.conversation.domain)

        val decodedPayload = NomadDeviceMessagePayload.decodeFromByteArray(Base64.Default.decode(upsertEvent.payload))
        assertEquals("hello from db", decodedPayload.content.text?.text)
        assertEquals("sender-client", decodedPayload.senderClientId)
        assertEquals("quoted-message", decodedPayload.content.text?.quotedMessageId)
        assertEquals(1, decodedPayload.content.text?.mentions?.size)

        val lastReadEvent = assertIs<NomadMessageEvent.LastReadEvent>(request.events.last())
        assertEquals(1, lastReadEvent.lastRead.size)
        assertEquals(CONVERSATION_ID.toString(), lastReadEvent.lastRead.first().conversationId)
        assertEquals(1772014500, lastReadEvent.lastRead.first().lastReadTimestamp)
    }

    @Test
    fun givenApiFailure_whenSyncingPage_thenBatchIsNotDeleted() = runTest {
        val dao = FakeRemoteBackupChangeLogDAO(
            batch = ChangeLogSyncBatch(
                events = listOf(messageDeleteEvent()),
                conversationLastReads = emptyList()
            )
        )
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Error(KaliumException.NoNetwork()))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { dao },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        assertIs<Either.Left<*>>(result)
        assertTrue((result as Either.Left).value is NetworkFailure.NoNetworkConnection)
        assertEquals(1, api.requests.size)
        assertTrue(dao.deletedChanges.isEmpty())
    }

    @Test
    fun givenNoPendingEvents_whenSyncingPage_thenNothingIsPostedOrDeleted() = runTest {
        val dao = FakeRemoteBackupChangeLogDAO(
            batch = ChangeLogSyncBatch(
                events = emptyList(),
                conversationLastReads = emptyList()
            )
        )
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Success(Unit, emptyMap(), 200))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { dao },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadRemoteBackupChangeLogSyncResult>>(result).value
        assertEquals(0, success.syncedEntries)
        assertEquals(0, success.postedEvents)
        assertTrue(api.requests.isEmpty())
        assertTrue(dao.deletedChanges.isEmpty())
    }

    @Test
    fun givenMissingUserStorage_whenSyncingPage_thenNothingIsPostedOrDeleted() = runTest {
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Success(Unit, emptyMap(), 200))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { null },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadRemoteBackupChangeLogSyncResult>>(result).value
        assertEquals(0, success.syncedEntries)
        assertEquals(0, success.postedEvents)
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun givenUnmappableUpsertWithoutLastReads_whenSyncingPage_thenBatchIsDroppedAndDeletedWithoutPost() = runTest {
        val dao = FakeRemoteBackupChangeLogDAO(
            batch = ChangeLogSyncBatch(
                events = listOf(
                    ChangeLogSyncEvent.MessageUpsert(
                        conversationId = CONVERSATION_ID,
                        messageId = MESSAGE_ID,
                        change = messageUpsertChange(),
                        message = null
                    )
                ),
                conversationLastReads = emptyList()
            )
        )
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Success(Unit, emptyMap(), 200))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { dao },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadRemoteBackupChangeLogSyncResult>>(result).value
        assertEquals(1, success.syncedEntries)
        assertEquals(0, success.postedEvents)
        assertTrue(api.requests.isEmpty())
        assertEquals(1, dao.deletedChanges.size)
    }

    @Test
    fun givenUnmappableUpsertWithLastRead_whenSyncingPage_thenLastReadEventIsStillPosted() = runTest {
        val dao = FakeRemoteBackupChangeLogDAO(
            batch = ChangeLogSyncBatch(
                events = listOf(
                    ChangeLogSyncEvent.MessageUpsert(
                        conversationId = CONVERSATION_ID,
                        messageId = MESSAGE_ID,
                        change = messageUpsertChange(),
                        message = null
                    )
                ),
                conversationLastReads = listOf(
                    ConversationLastReadSyncEntity(
                        conversationId = CONVERSATION_ID,
                        lastReadDate = Instant.parse("2026-02-25T10:15:00Z")
                    )
                )
            )
        )
        val api = FakeNomadDeviceSyncApi(NetworkResponse.Success(Unit, emptyMap(), 200))
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            remoteBackupChangeLogDAOProvider = { dao },
            nomadDeviceSyncApiProvider = { api },
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        val success = assertIs<Either.Right<NomadRemoteBackupChangeLogSyncResult>>(result).value
        assertEquals(1, success.syncedEntries)
        assertEquals(1, success.postedEvents)
        assertEquals(1, api.requests.size)
        assertIs<NomadMessageEvent.LastReadEvent>(api.requests.single().events.single())
        assertEquals(1, dao.deletedChanges.size)
    }

    @Test
    fun givenStorageReadFailure_whenSyncingPage_thenItIsReturnedAndPostNotCalled() = runTest {
        val repository = FakeSyncRepository(
            readResult = Either.Left(StorageFailure.DataNotFound),
            postResult = Either.Right(Unit),
            deleteResult = Either.Right(Unit)
        )
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            repository = repository,
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        assertIs<Either.Left<*>>(result)
        assertEquals(StorageFailure.DataNotFound, (result as Either.Left).value)
        assertEquals(0, repository.postCalls)
        assertEquals(0, repository.deleteCalls)
    }

    @Test
    fun givenDeleteFailureAfterSuccessfulPost_whenSyncingPage_thenDeleteFailureIsReturned() = runTest {
        val repository = FakeSyncRepository(
            readResult = Either.Right(
                ChangeLogSyncBatch(
                    events = listOf(messageDeleteEvent()),
                    conversationLastReads = emptyList()
                )
            ),
            postResult = Either.Right(Unit),
            deleteResult = Either.Left(StorageFailure.DataNotFound)
        )
        val useCase = SyncNomadRemoteBackupChangeLogUseCase(
            repository = repository,
            pageSize = 10
        )

        val result = useCase(SELF_USER_ID)

        assertIs<Either.Left<*>>(result)
        assertEquals(StorageFailure.DataNotFound, (result as Either.Left).value)
        assertEquals(1, repository.postCalls)
        assertEquals(1, repository.deleteCalls)
    }

    private class FakeSyncRepository(
        private val readResult: Either<com.wire.kalium.common.error.CoreFailure, ChangeLogSyncBatch>,
        private val postResult: Either<com.wire.kalium.common.error.CoreFailure, Unit>,
        private val deleteResult: Either<com.wire.kalium.common.error.CoreFailure, Unit>,
    ) : NomadRemoteBackupChangeLogSyncRepository {
        var postCalls: Int = 0
        var deleteCalls: Int = 0

        override suspend fun getLastPendingChangesBatch(
            selfUserId: UserId,
            limit: Long
        ): Either<com.wire.kalium.common.error.CoreFailure, ChangeLogSyncBatch> = readResult

        override suspend fun postMessageEvents(
            selfUserId: UserId,
            request: NomadMessageEventsRequest
        ): Either<com.wire.kalium.common.error.CoreFailure, Unit> {
            postCalls += 1
            return postResult
        }

        override suspend fun deleteChanges(
            selfUserId: UserId,
            changes: List<ChangeLogEntry>
        ): Either<com.wire.kalium.common.error.CoreFailure, Unit> {
            deleteCalls += 1
            return deleteResult
        }
    }

    private class FakeNomadDeviceSyncApi(
        private val response: NetworkResponse<Unit>
    ) : NomadDeviceSyncApi {
        val requests = mutableListOf<NomadMessageEventsRequest>()

        override suspend fun postMessageEvents(request: NomadMessageEventsRequest): NetworkResponse<Unit> {
            requests += request
            return response
        }

        override suspend fun getAllMessages(): NetworkResponse<NomadAllMessagesResponse> {
            error("Not needed for test")
        }

        override suspend fun getConversationMetadata(): NetworkResponse<NomadConversationMetadataResponse> {
            error("Not needed for test")
        }
    }

    private class FakeRemoteBackupChangeLogDAO(
        private val batch: ChangeLogSyncBatch
    ) : RemoteBackupChangeLogDAO {
        val deletedChanges = mutableListOf<ChangeLogEntry>()

        override suspend fun logMessageUpsert(
            conversationId: QualifiedIDEntity,
            messageId: String,
            timestampMs: Long,
            messageTimestampMs: Long
        ) = Unit

        override suspend fun logMessageDelete(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) = Unit

        override suspend fun logReactionsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) = Unit

        override suspend fun logReadReceiptsSync(conversationId: QualifiedIDEntity, messageId: String, timestampMs: Long) = Unit

        override suspend fun logConversationDelete(conversationId: QualifiedIDEntity, timestampMs: Long) = Unit

        override suspend fun logConversationClear(conversationId: QualifiedIDEntity, timestampMs: Long) = Unit

        override suspend fun getPendingChanges(): List<ChangeLogEntry> = batch.events.map { it.change }
        override suspend fun getLastPendingChangesBatch(limit: Long): ChangeLogSyncBatch = batch

        override fun observeLastPendingChangesBatch(limit: Long): Flow<ChangeLogSyncBatch> = flowOf(batch)

        override suspend fun deleteChanges(changes: List<ChangeLogEntry>) {
            deletedChanges += changes
        }
    }

    private fun messageDeleteEvent(): ChangeLogSyncEvent.MessageDelete =
        ChangeLogSyncEvent.MessageDelete(
            conversationId = CONVERSATION_ID,
            messageId = MESSAGE_ID,
            change = ChangeLogEntry(
                conversationId = CONVERSATION_ID,
                messageId = MESSAGE_ID,
                eventType = ChangeLogEventType.MESSAGE_DELETE,
                timestampMs = 2000L,
                messageTimestampMs = 2000L
            )
        )

    private fun messageUpsertChange(): ChangeLogEntry = ChangeLogEntry(
        conversationId = CONVERSATION_ID,
        messageId = MESSAGE_ID,
        eventType = ChangeLogEventType.MESSAGE_UPSERT,
        timestampMs = 2000L,
        messageTimestampMs = 1500L
    )

    private companion object {
        val SELF_USER_ID: UserId = UserId("self-user", "wire.test")
        val CONVERSATION_ID = QualifiedIDEntity("conversation-id", "wire.test")
        val MESSAGE_ID = "message-id"
        val SENDER_ID = QualifiedIDEntity("sender-id", "wire.test")
    }
}
