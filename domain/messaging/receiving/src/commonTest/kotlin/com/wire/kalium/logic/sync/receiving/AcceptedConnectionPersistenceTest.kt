/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.conversation.unverifiedConversationMessage
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.withTestUserDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AcceptedConnectionPersistenceTest {
    @Test
    fun replayRepairsAMissingWarningButPreservesLaterHistoryClearing() = runTest {
        fixture { db ->
            db.connectionDAO.updateConnectionStatus(peer, ConnectionEntity.State.ACCEPTED)

            db.accept("first-warning")
            val first = assertNotNull(db.messageDAO.getConversationStartedUnverifiedWarning(conversation))
            db.accept("replay-warning")

            assertEquals(first, db.messageDAO.getConversationStartedUnverifiedWarning(conversation))
            assertEquals(1, db.messageDAO.getMessagesByConversationAndVisibility(conversation, 10, 0).first().size)

            db.messageDAO.deleteMessage(first.id, conversation)
            db.accept("after-history-clear")
            assertNull(db.messageDAO.getConversationStartedUnverifiedWarning(conversation))
        }
    }

    @Test
    fun warningWriteFailureRollsBackAcceptanceAndRetryRepairsIt() = runTest {
        fixture { db ->
            // A missing sender makes the actual warning INSERT fail its SQLite foreign-key constraint.
            assertFails {
                db.writeMessageEffects {
                    persistAcceptedConnection(accepted, UserId("missing-sender", self.domain), "failed-warning")
                }
            }
            assertEquals(ConnectionEntity.State.PENDING, db.connectionDAO.getConnection(conversation)?.status)
            assertEquals(ConnectionEntity.State.PENDING, db.userDAO.getUserDetailsByQualifiedID(peer)?.connectionStatus)
            assertNull(db.memberDAO.getMemberRole(peer, conversation))
            assertNull(db.messageDAO.getConversationStartedUnverifiedWarning(conversation))

            db.accept("successful-warning")

            assertEquals(ConnectionEntity.State.ACCEPTED, db.connectionDAO.getConnection(conversation)?.status)
            assertNotNull(db.memberDAO.getMemberRole(peer, conversation))
            val messages = db.messageDAO.getMessagesByConversationAndVisibility(conversation, 10, 0).first()
            assertEquals(listOf("successful-warning"), messages.map { it.id })
        }
    }

    @Test
    fun acceptanceReusesMovedLegacyWarningWithItsIdentityAndTimestamp() = runTest {
        fixture { db ->
            val active = QualifiedIDEntity("active-conversation", self.domain)
            db.conversationDAO.insertConversation(pending.copy(qualifiedConversationId = active).pendingConversation())
            val warning = unverifiedConversationMessage("legacy-random-id", conversation.toModel(), time, self).toEntity()
            db.messageDAO.insertOrIgnoreMessages(listOf(warning))
            db.messageDAO.moveMessages(conversation, active)
            db.userDAO.updateActiveOneOnOneConversation(peer, active)

            db.accept("new-warning")

            assertNull(db.messageDAO.getConversationStartedUnverifiedWarning(conversation))
            val messages = db.messageDAO.getMessagesByConversationAndVisibility(active, 10, 0).first()
            assertEquals(1, messages.size)
            assertEquals(warning.id, messages.single().id)
            assertEquals(warning.date, messages.single().date)
        }
    }

    @Test
    fun legalHoldSuppressionSurvivesReplayButDoesNotHideANewAcceptance() = runTest {
        fixture { db ->
            db.connectionDAO.insertConnection(pending.copy(status = ConnectionEntity.State.MISSING_LEGALHOLD_CONSENT))

            db.accept("first-warning")
            db.accept("replay-warning")

            assertNull(db.messageDAO.getConversationStartedUnverifiedWarning(conversation))
            db.connectionDAO.insertConnection(pending)

            db.writeMessageEffects {
                persistAcceptedConnection(accepted.copy(lastUpdateDate = time + 1.seconds), self, "new-warning")
            }

            assertEquals("new-warning", db.messageDAO.getConversationStartedUnverifiedWarning(conversation)?.id)
        }
    }

    private suspend fun TestScope.fixture(block: suspend (UserDatabaseBuilder) -> Unit) {
        withTestUserDatabase(self.toDao()) { database ->
            val db = database.builder
            db.userDAO.insertOrIgnoreIncompleteUsers(listOf(self.toDao(), peer))
            db.userDAO.upsertConnectionStatuses(mapOf(peer to ConnectionEntity.State.PENDING))
            db.conversationDAO.insertConversation(pending.pendingConversation())
            db.connectionDAO.insertConnection(pending)
            block(db)
        }
    }

    private suspend fun UserDatabaseBuilder.accept(warningId: String) = writeMessageEffects {
        persistAcceptedConnection(accepted, self, warningId)
    }

    private companion object {
        val self = UserId("self", "wire.com")
        val peer = QualifiedIDEntity("peer", self.domain)
        val conversation = QualifiedIDEntity("request", self.domain)
        val time = Instant.parse("2026-10-31T10:00:00Z")
        val pending = ConnectionEntity(
            conversationId = conversation.value,
            from = self.value,
            lastUpdateDate = time,
            qualifiedConversationId = conversation,
            qualifiedToId = peer,
            status = ConnectionEntity.State.PENDING,
            toId = peer.value,
        )
        val accepted = pending.copy(status = ConnectionEntity.State.ACCEPTED)
    }
}
