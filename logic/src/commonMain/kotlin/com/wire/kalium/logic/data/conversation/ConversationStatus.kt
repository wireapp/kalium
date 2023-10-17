/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

/**
 * Conversation muting settings type
 */
sealed class MutedConversationStatus(open val status: Int = 0) {
    /**
     * 0 -> All notifications are displayed
     */
    data object AllAllowed : MutedConversationStatus(0)

    /**
     * 1 -> Only mentions and replies are displayed (normal messages muted)
     */
    data object OnlyMentionsAndRepliesAllowed : MutedConversationStatus(1)

    /**
     * 3 -> No notifications are displayed
     */
    data object AllMuted : MutedConversationStatus(3)
}
