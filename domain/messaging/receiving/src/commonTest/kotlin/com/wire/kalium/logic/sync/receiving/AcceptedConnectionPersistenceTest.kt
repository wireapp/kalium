/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.pendingConversation
import com.wire.kalium.logic.data.id.toDao
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
import kotlin.test.assertNotNull
import kotlin.time.Instant

class AcceptedConnectionPersistenceTest {
    @Test
    fun acceptanceMarksTheRequestAcceptedAndAddsThePeerAndReplayKeepsThatState() = runTest {
        fixture { db ->
            repeat(2) { db.writeMessageEffects { persistAcceptedConnection(accepted) } }

            assertEquals(ConnectionEntity.State.ACCEPTED, db.connectionDAO.getConnection(conversation)?.status)
            assertEquals(ConnectionEntity.State.ACCEPTED, db.userDAO.getUserDetailsByQualifiedID(peer)?.connectionStatus)
            assertNotNull(db.memberDAO.getMemberRole(peer, conversation))
            assertEquals(0, db.messageDAO.getMessagesByConversationAndVisibility(conversation, 10, 0).first().size)
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
