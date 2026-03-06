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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserScopedNomadPersistenceEventHookNotifierTest {

    @Test
    fun givenMatchingUser_whenPersistenceHooksAreTriggered_thenDelegateIsInvoked() = runTest {
        val recordingHook = RecordingPersistenceHookNotifier()
        val notifier = UserScopedNomadPersistenceEventHookNotifier(
            selfUserId = USER_ID,
            delegate = recordingHook
        )

        notifier.onMessagePersisted(MESSAGE, USER_ID)
        notifier.onMessageDeleted(MESSAGE_DELETE, USER_ID)
        notifier.onReactionPersisted(REACTION, USER_ID)
        notifier.onReadReceiptPersisted(READ_RECEIPT, USER_ID)
        notifier.onConversationDeleted(CONVERSATION_DELETE, USER_ID)
        notifier.onConversationCleared(CONVERSATION_CLEAR, USER_ID)

        assertEquals(
            listOf(
                "persisted:$USER_ID",
                "deleted:$USER_ID",
                "reaction:$USER_ID",
                "receipt:$USER_ID",
                "conversationDeleted:$USER_ID",
                "conversationCleared:$USER_ID",
            ),
            recordingHook.calls
        )
    }

    @Test
    fun givenDifferentUser_whenPersistenceHooksAreTriggered_thenDelegateIsNotInvoked() = runTest {
        val recordingHook = RecordingPersistenceHookNotifier()
        val notifier = UserScopedNomadPersistenceEventHookNotifier(
            selfUserId = USER_ID,
            delegate = recordingHook
        )

        notifier.onMessagePersisted(MESSAGE, OTHER_USER_ID)
        notifier.onMessageDeleted(MESSAGE_DELETE, OTHER_USER_ID)
        notifier.onReactionPersisted(REACTION, OTHER_USER_ID)
        notifier.onReadReceiptPersisted(READ_RECEIPT, OTHER_USER_ID)
        notifier.onConversationDeleted(CONVERSATION_DELETE, OTHER_USER_ID)
        notifier.onConversationCleared(CONVERSATION_CLEAR, OTHER_USER_ID)

        assertEquals(emptyList(), recordingHook.calls)
    }

    private class RecordingPersistenceHookNotifier : PersistenceEventHookNotifier {
        val calls = mutableListOf<String>()

        override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
            calls += "persisted:$selfUserId"
        }

        override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
            calls += "deleted:$selfUserId"
        }

        override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
            calls += "reaction:$selfUserId"
        }

        override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
            calls += "receipt:$selfUserId"
        }

        override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
            calls += "conversationDeleted:$selfUserId"
        }

        override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
            calls += "conversationCleared:$selfUserId"
        }
    }

    private companion object {
        val USER_ID = UserId("user", "domain")
        val OTHER_USER_ID = UserId("other", "domain")
        val CONVERSATION_ID = ConversationId("conversation", "domain")
        val EVENT_DATE = Instant.fromEpochMilliseconds(1)
        val MESSAGE = PersistedMessageData(
            conversationId = CONVERSATION_ID,
            messageId = "message-id",
            content = MessageContent.Text("hello", emptyList(), emptyList()),
            date = EVENT_DATE,
            expireAfter = null
        )
        val MESSAGE_DELETE = MessageDeleteEventData(
            conversationId = CONVERSATION_ID,
            messageId = "message-id"
        )
        val REACTION = ReactionEventData(
            conversationId = CONVERSATION_ID,
            messageId = "message-id",
            date = EVENT_DATE
        )
        val READ_RECEIPT = ReadReceiptEventData(
            conversationId = CONVERSATION_ID,
            messageIds = listOf("message-id"),
            date = EVENT_DATE
        )
        val CONVERSATION_DELETE = ConversationDeleteEventData(conversationId = CONVERSATION_ID)
        val CONVERSATION_CLEAR = ConversationClearEventData(conversationId = CONVERSATION_ID)
    }
}
