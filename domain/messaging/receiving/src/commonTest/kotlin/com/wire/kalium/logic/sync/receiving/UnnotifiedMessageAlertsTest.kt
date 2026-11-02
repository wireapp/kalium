/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.notification.NotificationMessageKind
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.config.UserConfigStorage
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TestTimeSource

class UnnotifiedMessageAlertsTest {
    @Test
    fun everyoneCaptionIsDeliveredOnceAndSurvivesPendingReplayAsText() = runTest {
        fixture { database, alerts, _ ->
            database.builder.messageDAO.deleteMessage("first", conversation.id)
            database.builder.conversationDAO.insertConversations(listOf(conversation.copy(type = ConversationEntity.Type.GROUP)))
            database.builder.messageDAO.insertOrIgnoreMessage(
                message("multipart", time).copy(
                    content = MessageEntityContent.Multipart("@all caption", mentions = listOf(MessageEntity.Mention(0, 4, null)))
                )
            )
            val shown = alerts.take().single()
            assertEquals("multipart", shown.messageId)
            assertEquals("@all caption", shown.text)
            assertEquals(NotificationMessageKind.TEXT, shown.messageKind)
            assertTrue(shown.isGroupConversation)
            assertEquals(emptyList(), alerts.take())
            val reopened = UnnotifiedMessageAlerts(database.builder, self) { time + 2.seconds }
            assertEquals("@all caption", reopened.pending.read().single().text)
            assertNull(reopened.pending.read(showContent = false).single().text)
        }
    }

    @Test
    fun preparationAndExpiryLeaveMessagesAvailableForRetry() = runTest {
        fixture { database, alerts, clock ->
            val prepared = alerts.prepare()
            assertEquals(1, database.builder.messageDAO.getNotificationMessage().size)
            clock += 20.seconds
            assertEquals(UnnotifiedMessageAlerts.CommitResult.Expired, alerts.commit(prepared))
            assertNull(database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
            assertEquals(listOf("first"), alerts.take().map { it.messageId })
        }
    }

    @Test
    fun cancellationDuringCommitLeavesMessagesAvailable() = runTest {
        fixture { database, alerts, clock ->
            val pending = async {
                val job = currentCoroutineContext().job
                val cancelled = UnnotifiedMessageAlerts(database.builder, self, clock) {
                    job.cancel()
                    time
                }
                cancelled.commit(cancelled.prepare())
            }
            assertFailsWith<CancellationException> { pending.await() }
            assertNull(database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
            assertEquals(listOf("first"), alerts.take().map { it.messageId })
        }
    }

    @Test
    fun expiryDuringEligibilityCheckDoesNotMarkMessages() = runTest {
        fixture { database, alerts, clock ->
            val expiring = UnnotifiedMessageAlerts(database.builder, self, clock) {
                clock += 20.seconds
                time
            }
            assertEquals(UnnotifiedMessageAlerts.CommitResult.Expired, expiring.commit(expiring.prepare()))
            assertNull(database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
            assertEquals(listOf("first"), alerts.take().map { it.messageId })
        }
    }

    @Test
    fun concurrentOwnersClaimOnlyOnce() = runTest {
        fixture { database, alerts, _ ->
            val other = UnnotifiedMessageAlerts(database.builder, self) { time }
            val first = alerts.prepare()
            val second = other.prepare()
            val results = listOf(async { alerts.commit(first) }, async { other.commit(second) }).awaitAll().flatMap {
                assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(it).alerts
            }
            assertEquals(listOf("first"), results.map { it.messageId })
            assertEquals(emptyList(), assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(first)).alerts)
        }
    }

    @Test
    fun committingOldSnapshotDoesNotClaimLaterArrival() = runTest {
        fixture { database, alerts, _ ->
            val prepared = alerts.prepare()
            database.builder.messageDAO.insertOrIgnoreMessage(message("later", time + 1.seconds))
            val committed = assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(prepared))
            assertEquals(listOf("first"), committed.alerts.map { it.messageId })
            assertEquals(listOf("later"), alerts.take().map { it.messageId })
            assertEquals(emptyList(), assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(prepared)).alerts)
            assertEquals(time + 1.seconds, database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
        }
    }

    @Test
    fun olderEventArrivingAfterPreparationRequiresFreshSnapshot() = runTest {
        fixture { database, alerts, _ ->
            val prepared = alerts.prepare()
            database.builder.messageDAO.insertOrIgnoreMessage(message("late-older", time - 1.seconds))
            assertEquals(UnnotifiedMessageAlerts.CommitResult.Stale, alerts.commit(prepared))
            assertNull(database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
            assertEquals(listOf("late-older", "first"), alerts.take().map { it.messageId })
        }
    }

    @Test
    fun privacyIsReadAtCommitAndCorruptPreferenceFailsClosed() = runTest {
        fixture { database, alerts, _ ->
            val prepared = alerts.prepare()
            database.builder.userPrefsDAO.persistMessageContentPreference(false)
            val hidden = assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(prepared)).alerts.single()
            assertNull(hidden.text)
            assertNull(hidden.senderName)
            assertNull(hidden.conversationName)
            assertFalse(hidden.mayPreview)
            database.builder.messageDAO.insertOrIgnoreMessage(message("later", time + 1.seconds))
            database.builder.metadataDAO.insertValue("invalid", UserConfigStorage.UserPreferences.MESSAGE_CONTENT_ENABLED.key)
            // Unreadable preferences hide content instead of assuming that previews are enabled.
            assertNull(assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(alerts.prepare())).alerts.single().text)
        }
    }

    @Test
    fun messageReadAfterPreparationIsNotPresented() = runTest {
        fixture { database, alerts, _ ->
            val prepared = alerts.prepare()
            database.builder.conversationDAO.updateReadDatesAndGetHasUnreadEvents(mapOf(conversation.id to time))
            assertEquals(emptyList(), assertIs<UnnotifiedMessageAlerts.CommitResult.Committed>(alerts.commit(prepared)).alerts)
        }
    }

    @Test
    fun snapshotCannotBeCommittedByAnotherAccountOwner() = runTest {
        fixture { database, alerts, _ ->
            val other = UnnotifiedMessageAlerts(database.builder, self) { time }
            assertFailsWith<IllegalArgumentException> { other.commit(alerts.prepare()) }
            assertEquals(listOf("first"), alerts.take().map { it.messageId })
        }
    }

    @Test
    fun currentOwnerIsWokenByNewMessagesWithoutPolling() = runTest {
        fixture { database, alerts, _ ->
            val seen = Channel<String>(Channel.UNLIMITED)
            val observation = backgroundScope.launch {
                alerts.observeChanges().collect {
                    alerts.take()
                    val pending = alerts.pending.read()
                    pending.forEach { seen.send(it.messageId) }
                    alerts.pending.acknowledge(pending.map {
                        NotificationMessageKey(it.messageId, QualifiedIDEntity(it.conversationValue, it.conversationDomain))
                    })
                }
            }
            try {
                assertEquals("first", seen.receive())
                database.builder.messageDAO.insertOrIgnoreMessage(message("later", time + 1.seconds))
                assertEquals("later", seen.receive())
            } finally {
                observation.cancelAndJoin()
            }
            assertTrue(observation.isCompleted)
        }
    }

    @Test
    fun pendingReplayKeepsOldestFirstAcrossBatchesAndAcknowledgement() = runTest {
        fixture { database, alerts, _ ->
            database.builder.messageDAO.insertOrIgnoreMessage(message("second", time + 1.seconds))
            assertEquals(listOf("first", "second"), alerts.take().map { it.messageId })
            database.builder.messageDAO.insertOrIgnoreMessage(message("third", time + 2.seconds))
            alerts.take()
            val reopened = UnnotifiedMessageAlerts(database.builder, self) { time + 3.seconds }
            assertEquals(listOf("first", "second", "third"), reopened.pending.read().map { it.messageId })
            reopened.pending.acknowledge(listOf(NotificationMessageKey("second", conversation.id)))
            assertEquals(listOf("first", "third"), reopened.pending.read().map { it.messageId })
        }
    }

    @Test
    fun committedAlertsSurviveRecreationUntilAcknowledged() = runTest {
        fixture { database, alerts, _ ->
            alerts.take()
            val reopened = UnnotifiedMessageAlerts(database.builder, self) { time }
            assertEquals(listOf("first"), reopened.pending.read().map { it.messageId })
            assertEquals(emptyList(), reopened.take())
            database.builder.userPrefsDAO.persistMessageContentPreference(false)
            assertNull(reopened.pending.read().single().text)
            reopened.pending.acknowledge(listOf(NotificationMessageKey("first", conversation.id)))
            assertEquals(emptyList(), reopened.pending.read())
            assertEquals(emptyList(), alerts.pending.read())
        }
    }

    @Test
    fun pendingImageRebuildsKindUsingCurrentPrivacyUntilAccepted() = runTest {
        fixture { database, alerts, _ ->
            val image = message("image", time + 1.seconds).copy(content = MessageEntityContent.Asset(
                assetSizeInBytes = 10, assetName = "image.png", assetMimeType = "image/png",
                assetOtrKey = byteArrayOf(1), assetSha256Key = byteArrayOf(2), assetId = "asset",
                assetEncryptionAlgorithm = "AES_CBC",
            ))
            database.builder.messageDAO.insertOrIgnoreMessage(image)
            assertEquals(NotificationMessageKind.PICTURE, alerts.take().single { it.messageId == "image" }.messageKind)
            val reopened = UnnotifiedMessageAlerts(database.builder, self) { time + 2.seconds }
            assertEquals(NotificationMessageKind.PICTURE, reopened.pending.read().single { it.messageId == "image" }.messageKind)
            database.builder.userPrefsDAO.persistMessageContentPreference(false)
            val hidden = reopened.pending.read().single { it.messageId == "image" }
            assertNull(hidden.messageKind)
            assertNull(hidden.senderName)
            database.builder.userPrefsDAO.persistMessageContentPreference(true)
            assertEquals(NotificationMessageKind.PICTURE, reopened.pending.read().single { it.messageId == "image" }.messageKind)
            assertNull(reopened.pending.read(showContent = false).single { it.messageId == "image" }.messageKind)
            reopened.pending.acknowledge(listOf(NotificationMessageKey("image", conversation.id)))
            assertFalse(reopened.pending.read().any { it.messageId == "image" })
        }
    }

    @Test
    fun readMessageIsRemovedFromPendingDelivery() = runTest {
        fixture { database, alerts, _ ->
            alerts.take()
            database.builder.conversationDAO.updateReadDatesAndGetHasUnreadEvents(mapOf(conversation.id to time))
            assertEquals(emptyList(), alerts.pending.read())
        }
    }

    @Test
    fun editKeepsDeliveryIdentityAndUsesCurrentContent() = runTest {
        fixture { database, alerts, _ ->
            alerts.take()
            database.builder.messageDAO.updateTextMessageContent(
                time + 1.seconds, conversation.id, "first", MessageEntityContent.Text("edited text"), "edited-id",
            )
            val pending = alerts.pending.read().single()
            assertEquals("first", pending.messageId)
            assertEquals("edited text", pending.text)
            alerts.pending.acknowledge(listOf(NotificationMessageKey("first", conversation.id)))
            assertEquals(emptyList(), alerts.pending.read())
        }
    }

    @Test
    fun failedPendingWriteRollsBackNotificationCursor() = runTest {
        fixture { database, alerts, _ ->
            database.builder.metadataDAO.insertValue("invalid", "apple.pending-message-alerts.v1")
            assertFailsWith<kotlinx.serialization.SerializationException> { alerts.take() }
            assertNull(database.builder.conversationDAO.getConversationById(conversation.id)?.lastNotificationDate)
            database.builder.metadataDAO.deleteValue("apple.pending-message-alerts.v1")
            assertEquals(listOf("first"), alerts.take().map { it.messageId })
        }
    }

    private suspend fun TestScope.fixture(block: suspend (TestUserDatabase, UnnotifiedMessageAlerts, TestTimeSource) -> Unit) {
        val database = TestUserDatabase(UserIDEntity(self.value, self.domain), StandardTestDispatcher(testScheduler))
        try {
            database.builder.userDAO.upsertUsers(listOf(sender))
            database.builder.conversationDAO.insertConversations(listOf(conversation))
            database.builder.messageDAO.insertOrIgnoreMessage(message("first", time))
            val clock = TestTimeSource()
            block(database, UnnotifiedMessageAlerts(database.builder, self, clock) { time + 2.seconds }, clock)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    private companion object {
        val self = UserId("self", "wire.com")
        val time = Instant.parse("2026-10-16T12:00:00Z")
        val sender = UserEntity(
                id = QualifiedIDEntity("sender", "wire.com"),
                name = "usersender",
                handle = "handlesender",
                email = "emailsender",
                phone = "phonesender",
                accentId = 1,
                team = "team",
                ConnectionEntity.State.ACCEPTED,
                null,
                null,
                UserAvailabilityStatusEntity.NONE,
                UserTypeEntity.STANDARD,
                botService = null,
                deleted = false,
                hasIncompleteMetadata = false,
                expiresAt = null,
                defederated = false,
                supportedProtocols = setOf(SupportedProtocolEntity.PROTEUS),
                activeOneOnOneConversationId = null
            )
        val conversation = ConversationEntity(
            id = QualifiedIDEntity("conversation", "wire.com"),
            name = "conversation1",
            type = ConversationEntity.Type.ONE_ON_ONE,
            teamId = "teamID",
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus,
            creatorId = "someValue",
            lastNotificationDate = null,
            lastModifiedDate = Instant.parse("2022-03-30T15:36:00.000Z"),
            lastReadDate = Instant.parse("2000-01-01T12:00:00.000Z"),
            access = listOf(ConversationEntity.Access.LINK, ConversationEntity.Access.INVITE),
            accessRole = listOf(ConversationEntity.AccessRole.NON_TEAM_MEMBER, ConversationEntity.AccessRole.TEAM_MEMBER),
            receiptMode = ConversationEntity.ReceiptMode.DISABLED,
            messageTimer = null,
            userMessageTimer = null,
            archived = false,
            archivedInstant = null,
            mlsVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            proteusVerificationStatus = ConversationEntity.VerificationStatus.NOT_VERIFIED,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED,
            channelAccess = ConversationEntity.ChannelAccess.PRIVATE,
            channelAddPermission = ConversationEntity.ChannelAddPermission.EVERYONE,
            wireCell = null,
            historySharingRetentionSeconds = 0,
        )
        fun message(id: String, date: Instant) = MessageEntity.Regular(
            id = id, content = MessageEntityContent.Text("private text"), conversationId = conversation.id,
            date = date, senderUserId = sender.id, senderClientId = "client", status = MessageEntity.Status.SENT,
            editStatus = MessageEntity.EditStatus.NotEdited, visibility = MessageEntity.Visibility.VISIBLE,
            senderName = "private name", expectsReadConfirmation = false, readCount = 0,
            expireAfterMs = null, selfDeletionEndDate = null, sender = null,
        )
    }
}
