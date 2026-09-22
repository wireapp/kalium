/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

enum class ConversationClear { IGNORE, CONTENT, CONVERSATION }

/** Preserve the app's sender/carrier rule, including clears sent by another member. */
fun conversationClear(selfSender: Boolean, selfConversation: Boolean, removeLocally: Boolean): ConversationClear = when {
    selfSender != selfConversation -> ConversationClear.IGNORE
    selfSender && removeLocally -> ConversationClear.CONVERSATION
    else -> ConversationClear.CONTENT
}
