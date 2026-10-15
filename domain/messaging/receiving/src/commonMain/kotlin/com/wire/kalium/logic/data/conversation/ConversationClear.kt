/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.conversation

enum class ConversationClear { IGNORE, CONTENT, CONVERSATION }

/** Only our own devices may clear local history, through a registered self conversation. */
fun conversationClear(selfSender: Boolean, selfConversation: Boolean, removeLocally: Boolean): ConversationClear = when {
    !selfSender || !selfConversation -> ConversationClear.IGNORE
    removeLocally -> ConversationClear.CONVERSATION
    else -> ConversationClear.CONTENT
}
