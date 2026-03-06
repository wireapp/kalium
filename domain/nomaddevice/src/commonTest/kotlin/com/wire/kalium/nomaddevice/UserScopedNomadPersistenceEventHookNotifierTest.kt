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
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserScopedNomadPersistenceEventHookNotifierTest {

    @Test
    fun givenMatchingUser_whenMessagePersisted_thenDelegateIsInvoked() = runTest {
        val recordingHook = RecordingPersistenceHookNotifier()
        val notifier = UserScopedNomadPersistenceEventHookNotifier(
            selfUserId = USER_ID,
            delegate = recordingHook
        )

        notifier.onMessagePersisted(MESSAGE, USER_ID)

        assertEquals(listOf(USER_ID), recordingHook.calls)
    }

    @Test
    fun givenDifferentUser_whenMessagePersisted_thenDelegateIsNotInvoked() = runTest {
        val recordingHook = RecordingPersistenceHookNotifier()
        val notifier = UserScopedNomadPersistenceEventHookNotifier(
            selfUserId = USER_ID,
            delegate = recordingHook
        )

        notifier.onMessagePersisted(MESSAGE, OTHER_USER_ID)

        assertEquals(emptyList(), recordingHook.calls)
    }

    private class RecordingPersistenceHookNotifier : PersistenceEventHookNotifier {
        val calls = mutableListOf<UserId>()

        override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
            calls += selfUserId
        }
    }

    private companion object {
        val USER_ID = UserId("user", "domain")
        val OTHER_USER_ID = UserId("other", "domain")
        val MESSAGE = PersistedMessageData(
            conversationId = ConversationId("conversation", "domain"),
            messageId = "message-id",
            content = MessageContent.Text("hello", emptyList(), emptyList()),
            date = Instant.fromEpochMilliseconds(1),
            expireAfter = null
        )
    }
}
