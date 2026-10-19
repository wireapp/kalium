/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.conversation

import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationClearTest {
    @Test
    fun unauthorizedSenderAndCarrierCombinationsAreIgnored() {
        for ((selfSender, selfConversation) in listOf(false to false, false to true, true to false)) {
            for (removeLocally in listOf(false, true)) {
                assertEquals(ConversationClear.IGNORE, conversationClear(selfSender, selfConversation, removeLocally),
                    "selfSender=$selfSender, selfConversation=$selfConversation, removeLocally=$removeLocally")
            }
        }
    }

    @Test
    fun selfSenderInSelfConversationCanClearOrRemove() {
        assertEquals(ConversationClear.CONTENT, conversationClear(selfSender = true, selfConversation = true, removeLocally = false))
        assertEquals(ConversationClear.CONVERSATION, conversationClear(selfSender = true, selfConversation = true, removeLocally = true))
    }
}
