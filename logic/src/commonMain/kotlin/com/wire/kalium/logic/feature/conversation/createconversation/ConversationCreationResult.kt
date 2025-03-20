/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.createconversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation

sealed interface ConversationCreationResult {
    /**
     * Conversation created successfully.
     */
    class Success(
        /**
         * Details of the newly created conversation
         */
        val conversation: Conversation
    ) : ConversationCreationResult

    /**
     * There was a failure trying to Sync with the server
     */
    data object SyncFailure : ConversationCreationResult

    /**
     * Other, unknown failure.
     */
    class UnknownFailure(
        /**
         * The root cause of the failure
         */
        val cause: CoreFailure
    ) : ConversationCreationResult

    class BackendConflictFailure(
        val domains: List<String>
    ) : ConversationCreationResult
}

