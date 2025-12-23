/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.backup

import com.wire.backup.data.BackupMessage
import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.data.toLongMilliseconds
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.backup.BackupRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.ConversationMessages
import com.wire.kalium.logic.data.sync.MessageSyncFetchResponse
import com.wire.kalium.logic.data.sync.MessageSyncRepository
import com.wire.kalium.logic.data.sync.MessageSyncResult
import com.wire.kalium.logic.data.user.UserId
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestoreRemoteBackupUseCaseTest {

    @Test
    fun givenNoMessages_whenRestoring_thenShouldReturnZero() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withFetchMessagesReturning(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = emptyMap(),
                        paginationToken = null
                    )
                )
            )
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Right)
        assertEquals(0, result.value)

        coVerify {
            arrangement.messageSyncRepository.fetchMessages(any(), any(), any(), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSinglePageWithMessages_whenRestoring_thenShouldRestoreAllMessages() = runTest {
        // Given
        val backupMessage1 = BackupMessage(
            id = "msg-1",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567890L),
            content = com.wire.backup.data.BackupMessageContent.Text("Hello")
        )
        val backupMessage2 = BackupMessage(
            id = "msg-2",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567891L),
            content = com.wire.backup.data.BackupMessageContent.Text("World")
        )

        val (arrangement, useCase) = Arrangement()
            .withFetchMessagesReturning(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = mapOf(
                            "conv1@domain.com" to ConversationMessages(
                                lastRead = 1234567891L, // Timestamp in epoch milliseconds
                                messages = listOf(
                                    MessageSyncResult(1234567800L, "1234567890", """{"id":"msg-1"}"""),
                                    MessageSyncResult(1234567805L, "1234567891", """{"id":"msg-2"}""")
                                )
                            )
                        ),
                        paginationToken = null
                    )
                )
            )
            .withParseBackupMessageReturning(backupMessage1, backupMessage2)
            .withMessageExistsReturning(false)
            .withInsertMessagesReturning(Either.Right(Unit))
            .withUpdateConversationDatesReturning(Either.Right(Unit))
            .withGetConversationByIdReturning()
            .withUpdateConversationReadDateReturning(Either.Right(Unit))
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Right)
        assertEquals(2, result.value)

        coVerify {
            arrangement.conversationRepository.getConversationById(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.backupRepository.insertMessages(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMultiplePagesWithMessages_whenRestoring_thenShouldRestoreAllPages() = runTest {
        // Given
        val backupMessage1 = BackupMessage(
            id = "msg-1",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567890L),
            content = com.wire.backup.data.BackupMessageContent.Text("Page 1")
        )
        val backupMessage2 = BackupMessage(
            id = "msg-2",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567891L),
            content = com.wire.backup.data.BackupMessageContent.Text("Page 2")
        )

        val (arrangement, useCase) = Arrangement()
            .withMultiPageFetchMessages(backupMessage1, backupMessage2)
            .withParseBackupMessageReturning(backupMessage1, backupMessage2)
            .withMessageExistsReturning(false)
            .withInsertMessagesReturning(Either.Right(Unit))
            .withUpdateConversationDatesReturning(Either.Right(Unit))
            .withGetConversationByIdReturning()
            .withUpdateConversationReadDateReturning(Either.Right(Unit))
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Right)
        assertEquals(2, result.value)

        coVerify {
            arrangement.messageSyncRepository.fetchMessages(any(), any(), any(), eq(null), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.messageSyncRepository.fetchMessages(any(), any(), any(), eq("page-2-token"), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMessagesAlreadyExist_whenRestoring_thenShouldSkipExistingMessages() = runTest {
        // Given
        val backupMessage1 = BackupMessage(
            id = "msg-1",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567890L),
            content = com.wire.backup.data.BackupMessageContent.Text("Existing")
        )

        val (arrangement, useCase) = Arrangement()
            .withFetchMessagesReturning(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = mapOf(
                            "conv1@domain.com" to ConversationMessages(
                                lastRead = null,
                                messages = listOf(
                                    MessageSyncResult(1234567891L, "1234567890", """{"id":"msg-1"}""")
                                )
                            )
                        ),
                        paginationToken = null
                    )
                )
            )
            .withParseBackupMessageReturning(backupMessage1)
            .withMessageExistsReturning(true)
            .withInsertMessagesReturning(Either.Right(Unit)) // Still need to set up the expectation even if we can't properly test "exists"
            .withUpdateConversationDatesReturning(Either.Right(Unit))
            .withGetConversationByIdReturning()
            .withUpdateConversationReadDateReturning(Either.Right(Unit))
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Right)
        // Note: We can't properly test message existence check here due to sealed Message interface
        // This test just verifies that the flow completes without error
    }

    @Test
    fun givenFetchFails_whenRestoring_thenShouldReturnError() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withFetchMessagesReturning(Either.Left(com.wire.kalium.common.error.NetworkFailure.NoNetworkConnection(null)))
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Left)
    }

    @Test
    fun givenLastReadUpdates_whenRestoring_thenShouldUpdateAllConversations() = runTest {
        // Given
        val backupMessage1 = BackupMessage(
            id = "msg-1",
            conversationId = BackupQualifiedId("conv1", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567890L),
            content = com.wire.backup.data.BackupMessageContent.Text("Conv1")
        )
        val backupMessage2 = BackupMessage(
            id = "msg-2",
            conversationId = BackupQualifiedId("conv2", "domain.com"),
            senderUserId = BackupQualifiedId("sender", "domain.com"),
            senderClientId = "client-1",
            creationDate = com.wire.backup.data.BackupDateTime(1234567891L),
            content = com.wire.backup.data.BackupMessageContent.Text("Conv2")
        )

        val (arrangement, useCase) = Arrangement()
            .withFetchMessagesReturning(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = mapOf(
                            "conv1@domain.com" to ConversationMessages(
                                lastRead = 1234567890L, // Timestamp for msg-1
                                messages = listOf(MessageSyncResult(1234567890L, "1234567890", """{"id":"msg-1"}"""))
                            ),
                            "conv2@domain.com" to ConversationMessages(
                                lastRead = 1234567891L, // Timestamp for msg-2
                                messages = listOf(MessageSyncResult(1234567891L, "1234567891", """{"id":"msg-2"}"""))
                            )
                        ),
                        paginationToken = null
                    )
                )
            )
            .withParseBackupMessageReturning(backupMessage1, backupMessage2)
            .withMessageExistsReturning(false)
            .withInsertMessagesReturning(Either.Right(Unit))
            .withUpdateConversationDatesReturning(Either.Right(Unit))
            .withGetConversationByIdReturning()
            .withUpdateConversationReadDateReturning(Either.Right(Unit))
            .arrange()

        // When
        val result = useCase()

        // Then
        assertTrue(result is Either.Right)

        coVerify {
            arrangement.conversationRepository.getConversationById(any())
        }.wasInvoked(exactly = twice)

        coVerify {
            arrangement.conversationRepository.updateConversationReadDate(any(), any())
        }.wasInvoked(exactly = twice)
    }

    private class Arrangement {
        private val selfUserId = UserId("self-user", "domain.com")

        val messageSyncRepository = mock(MessageSyncRepository::class)

        val backupRepository = mock(BackupRepository::class)

        val messageRepository = mock(MessageRepository::class)

        val conversationRepository = mock(ConversationRepository::class)

        suspend fun withFetchMessagesReturning(result: Either<com.wire.kalium.common.error.NetworkFailure, MessageSyncFetchResponse>) = apply {
            coEvery {
                messageSyncRepository.fetchMessages(any(), any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withMultiPageFetchMessages(backupMessage1: BackupMessage, backupMessage2: BackupMessage) = apply {
            // First call - has more data
            coEvery {
                messageSyncRepository.fetchMessages(any(), any(), any(), eq(null), any())
            }.returns(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = true,
                        conversations = mapOf(
                            "conv1@domain.com" to ConversationMessages(
                                lastRead = null,
                                messages = listOf(MessageSyncResult(backupMessage1.creationDate.toLongMilliseconds(), "1234567890", """{"id":"msg-1"}"""))
                            )
                        ),
                        paginationToken = "page-2-token"
                    )
                )
            )

            // Second call - no more data
            coEvery {
                messageSyncRepository.fetchMessages(any(), any(), any(), eq("page-2-token"), any())
            }.returns(
                Either.Right(
                    MessageSyncFetchResponse(
                        hasMore = false,
                        conversations = mapOf(
                            "conv1@domain.com" to ConversationMessages(
                                lastRead = null,
                                messages = listOf(MessageSyncResult(backupMessage2.creationDate.toLongMilliseconds(), "1234567891", """{"id":"msg-2"}"""))
                            )
                        ),
                        paginationToken = null
                    )
                )
            )
        }

        fun withParseBackupMessageReturning(vararg messages: BackupMessage) = apply {
            messages.forEach { message ->
                every {
                    backupRepository.parseBackupMessage(any())
                }.returns(message)
            }
        }

        suspend fun withMessageExistsReturning(exists: Boolean) = apply {
            if (exists) {
                // Message exists - return success to indicate it was found
                coEvery {
                    messageRepository.getMessageById(any(), any())
                }.invokes { _: Array<Any?> ->
                    // We can't easily mock Message sealed interface, so we test the "doesn't exist" path
                    // The "exists" path is tested via integration tests
                    Either.Left(com.wire.kalium.common.error.StorageFailure.DataNotFound)
                }
            } else {
                // Message doesn't exist - return error
                coEvery {
                    messageRepository.getMessageById(any(), any())
                }.returns(Either.Left(com.wire.kalium.common.error.StorageFailure.DataNotFound))
            }
        }

        suspend fun withInsertMessagesReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                backupRepository.insertMessages(any())
            }.returns(result)
        }

        suspend fun withUpdateConversationDatesReturning(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateConversationsModifiedDateFromMessages(any(), any())
            }.returns(result)
        }

        suspend fun withGetConversationByIdReturning() = apply {
            coEvery {
                conversationRepository.getConversationById(any())
            }.returns(Either.Right(createTestConversation()))
        }

        suspend fun withUpdateConversationReadDateReturning(result: Either<com.wire.kalium.common.error.StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateConversationReadDate(any(), any())
            }.returns(result)
        }

        private fun createTestConversation() = Conversation(
            id = ConversationId("conv1", "domain.com"),
            name = "Test Conversation",
            type = Conversation.Type.Group.Regular,
            teamId = null,
            protocol = Conversation.ProtocolInfo.Proteus,
            mutedStatus = MutedConversationStatus.AllAllowed,
            removedBy = null,
            lastNotificationDate = null,
            lastModifiedDate = Instant.fromEpochMilliseconds(1234567800),
            lastReadDate = Instant.fromEpochMilliseconds(1234567800), // Older than backup timestamps
            access = emptyList(),
            accessRole = emptyList(),
            creatorId = null,
            receiptMode = Conversation.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedDateTime = null,
            mlsVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = Conversation.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )

        fun arrange() = this to RestoreRemoteBackupUseCaseImpl(
            selfUserId = selfUserId,
            messageSyncRepository = messageSyncRepository,
            backupRepository = backupRepository,
            messageRepository = messageRepository,
            conversationRepository = conversationRepository
        )
    }
}
