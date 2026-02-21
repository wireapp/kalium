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

package com.wire.kalium.messaging.hooks

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersistMessageCallbackManagerTest {

    private val selfUserId = UserId("self-user-id", "domain")

    private val persistedMessageData = PersistedMessageData(
        conversationId = QualifiedID("conversation-id", "domain"),
        messageId = "message-id",
        content = MessageContent.Unknown(),
        date = Instant.parse("2026-01-01T00:00:00Z"),
        expireAfter = null
    )

    @Test
    fun givenRegisteredCallback_whenMessageIsPersisted_thenItExecutesAsynchronously() = runTest {
        val manager = PersistMessageCallbackManagerImpl(this)
        var invoked = false
        val callback = object : PersistMessageCallback {
            override suspend fun invoke(message: PersistedMessageData, selfUserId: UserId) {
                invoked = true
            }
        }
        manager.register(callback)

        manager.onMessagePersisted(persistedMessageData, selfUserId)

        assertFalse(invoked)
        advanceUntilIdle()
        assertTrue(invoked)
    }

    @Test
    fun givenUnregisteredCallback_whenMessageIsPersisted_thenItIsNotExecuted() = runTest {
        val manager = PersistMessageCallbackManagerImpl(this)
        var invoked = false
        val callback = object : PersistMessageCallback {
            override suspend fun invoke(message: PersistedMessageData, selfUserId: UserId) {
                invoked = true
            }
        }
        manager.register(callback)
        manager.unregister(callback)

        manager.onMessagePersisted(persistedMessageData, selfUserId)

        advanceUntilIdle()
        assertFalse(invoked)
    }
}
