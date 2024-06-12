/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.message.receipt

import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.datetime.Instant

/**
 * The input of a conversation time-event.
 * Used to schedule [ConversationTimeEventWork] through the [ConversationWorkQueue].
 * @property eventTime that says when the event in question happened.
 */
internal data class ConversationTimeEventInput(
    val conversationId: ConversationId,
    val eventTime: Instant
)

/**
 * Represents a conversation time-event work, consisting of the input and the worker.
 *
 * @property conversationTimeEventInput The input of a conversation time-event.
 * @property worker The responsible for performing the work.
 */
internal data class ConversationTimeEventWork(
    val conversationTimeEventInput: ConversationTimeEventInput,
    val worker: ConversationTimeEventWorker
)
