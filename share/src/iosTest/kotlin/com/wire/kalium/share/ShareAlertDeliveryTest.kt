/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.share

import com.wire.kalium.logic.sync.receiving.UnnotifiedMessageAlerts
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ShareAlertDeliveryTest {
    @Test
    fun acceptedAlertsAreAcknowledgedAndOthersStayPendingForTheNextOwner() = runTest {
        withDatabase { database ->
            database.userDAO.upsertUsers(listOf(user(OTHER, ConnectionEntity.State.ACCEPTED)))
            conversation(database, "group", ConversationEntity.Type.GROUP, T1)
            listOf("accepted", "refused").forEach { database.messageDAO.insertOrIgnoreMessage(message(it)) }
            val claims = UnnotifiedMessageAlerts(database, SELF) { T2 }
            val alerts = claims.take().map {
                KaliumShareAlert(it.messageId, it.conversationValue, it.conversationDomain, null, null, null, false)
            }
            val delivery = object : KaliumShareMessageDelivery {
                override suspend fun deliver(userValue: String, userDomain: String, alert: KaliumShareAlert) =
                    alert.messageId == "accepted"
            }
            delivery.deliverAll(SELF, alerts, claims.pending)
            assertEquals(listOf("refused"), claims.pending.read().map { it.messageId })
        }
    }

    private fun message(id: String) = MessageEntity.Regular(
        id = id, content = MessageEntityContent.Text("text"), conversationId = QualifiedIDEntity("group", DOMAIN),
        date = T2, senderUserId = OTHER.toEntity(), senderClientId = "client", status = MessageEntity.Status.SENT,
        editStatus = MessageEntity.EditStatus.NotEdited, visibility = MessageEntity.Visibility.VISIBLE,
        senderName = "other", expectsReadConfirmation = false, readCount = 0,
        expireAfterMs = null, selfDeletionEndDate = null, sender = null,
    )
}
