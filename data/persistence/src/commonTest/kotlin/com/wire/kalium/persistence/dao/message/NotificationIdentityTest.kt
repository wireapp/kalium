/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.message

import app.cash.turbine.test
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

class NotificationIdentityTest : BaseMessageTest() {
    @Test
    fun alertsForMultipleEditsFollowReadStateWithoutRemovingNewerOrOtherConversationAlerts() = runTest(dispatcher) {
        insertInitialData()
        val original = message("original")
        val otherConversation = original.copy(conversationId = TEST_CONVERSATION_2.id)
        val newer = original.copy(id = "newer", date = original.date + 2.hours)
        messageDAO.insertOrIgnoreMessages(listOf(original, otherConversation, newer))
        messageDAO.rememberNotificationMessages(listOf(original.key(), otherConversation.key(), newer.key()))
        edit(original.id, "edit-one")
        messageDAO.rememberNotificationMessages(listOf(original.copy(id = "edit-one").key()))
        edit("edit-one", "edit-two")
        // A stale OS snapshot must not discard or overwrite an existing alias.
        messageDAO.rememberNotificationMessages(listOf(original.key()))
        conversationDAO.updateReadDatesAndGetHasUnreadEvents(mapOf(original.conversationId to original.date))
        val ids = listOf("original", "edit-one", "edit-two", "newer")
        val expected = setOf("original", "edit-one", "edit-two").map { original.copy(id = it).key() }.toSet()
        assertEquals(expected, messageDAO.observeNotificationRemovalCandidates(ids).first().toSet())
        // A fresh subscription reads durable identities, independent of an earlier host attempt.
        assertEquals(expected, messageDAO.observeNotificationRemovalCandidates(ids).first().toSet())
    }

    @Test
    fun originalAlertFollowsDeletionAfterSeveralEdits() = runTest(dispatcher) {
        insertInitialData()
        val original = message("original")
        messageDAO.insertOrIgnoreMessage(original)
        messageDAO.rememberNotificationMessages(listOf(original.key()))
        messageDAO.observeNotificationRemovalCandidates(listOf(original.id)).test {
            assertEquals(emptyList(), awaitItem())
            edit(original.id, "edit-one")
            edit("edit-one", "edit-two")
            messageDAO.markMessageAsDeleted("edit-two", original.conversationId)
            assertEquals(listOf(original.key()), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun ephemeralRemovalDropsIdentityWithoutRequestingAlertRemoval() = runTest(dispatcher) {
        insertInitialData()
        val original = message("ephemeral").copy(expireAfterMs = 1000)
        messageDAO.insertOrIgnoreMessage(original)
        messageDAO.rememberNotificationMessages(listOf(original.key()))
        edit(original.id, "edited-ephemeral")
        messageDAO.markMessageAsDeleted("edited-ephemeral", original.conversationId)
        assertEquals(emptyList(), messageDAO.observeNotificationRemovalCandidates(listOf(original.id)).first())
        messageDAO.deleteMessage("edited-ephemeral", original.conversationId)
        messageDAO.rememberNotificationMessages(listOf(original.key()))
        assertEquals(emptyList(), messageDAO.observeNotificationRemovalCandidates(listOf(original.id)).first())
    }

    @Test
    fun editsWithoutAppleRegistrationDoNotRetainNotificationAliases() = runTest(dispatcher) {
        insertInitialData()
        val original = message("android-message")
        messageDAO.insertOrIgnoreMessage(original)
        edit(original.id, "android-edit")
        messageDAO.markMessageAsDeleted("android-edit", original.conversationId)
        assertEquals(emptyList(), messageDAO.observeNotificationRemovalCandidates(listOf(original.id)).first())
        assertEquals(listOf(original.copy(id = "android-edit").key()),
            messageDAO.observeNotificationRemovalCandidates(listOf("android-edit")).first())
    }

    @Test
    fun emptyCandidatesCompleteWithoutInstallingAnObserver() = runTest(dispatcher) {
        assertEquals(listOf(emptyList()), messageDAO.observeNotificationRemovalCandidates(emptyList()).toList())
    }

    private fun message(id: String) = newRegularMessageEntity(
        id = id, conversationId = TEST_CONVERSATION_1.id, senderUserId = OTHER_USER.id,
        content = MessageEntityContent.Text("before")
    )

    private fun MessageEntity.Regular.key() = NotificationMessageKey(id, conversationId)

    private suspend fun edit(oldId: String, newId: String) {
        messageDAO.updateTextMessageContent(
            editInstant = message(oldId).date + 1.hours,
            conversationId = TEST_CONVERSATION_1.id,
            currentMessageId = oldId,
            newTextContent = MessageEntityContent.Text("after"),
            newMessageId = newId
        )
    }
}
