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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageButtonId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.common.functional.Either
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock

internal interface CompositeMessageRepositoryArrangement {

    val compositeMessageRepository: CompositeMessageRepository

    suspend fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: (MessageId) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true },
        buttonId: (MessageButtonId) -> Boolean = { true }
    )

    suspend fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: (MessageId) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true }
    )
}

internal class CompositeMessageRepositoryArrangementImpl : CompositeMessageRepositoryArrangement {

    override val compositeMessageRepository: CompositeMessageRepository = mock<CompositeMessageRepository>(mode = MockMode.autoUnit)

    override suspend fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: (MessageId) -> Boolean,
        conversationId: (ConversationId) -> Boolean,
        buttonId: (MessageButtonId) -> Boolean
    ) {
        everySuspend {
            compositeMessageRepository.markSelected(
                matches { messageId(it) },
                matches { conversationId(it) },
                matches { buttonId(it) }
            )
        }.returns(result)
    }

    override suspend fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: (MessageId) -> Boolean,
        conversationId: (ConversationId) -> Boolean
    ) {
        everySuspend {
            compositeMessageRepository.resetSelection(
                matches { messageId(it) },
                matches { conversationId(it) }
            )
        }.returns(result)
    }

}
