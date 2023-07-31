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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageButtonId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

interface CompositeMessageRepositoryArrangement {
    @Mock
    val compositeMessageRepository: CompositeMessageRepository

    fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId> = any(),
        conversationId: Matcher<ConversationId> = any(),
        buttonId: Matcher<MessageButtonId> = any()
    )

    fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId> = any(),
        conversationId: Matcher<ConversationId> = any()
    )
}

class CompositeMessageRepositoryArrangementImpl: CompositeMessageRepositoryArrangement {
    @Mock
    override val compositeMessageRepository: CompositeMessageRepository = mock(CompositeMessageRepository::class)

    override fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId>,
        conversationId: Matcher<ConversationId>,
        buttonId: Matcher<MessageButtonId>
    ) {
        given(compositeMessageRepository)
            .suspendFunction(compositeMessageRepository::markSelected)
            .whenInvokedWith(messageId, conversationId, buttonId)
            .thenReturn(result)
    }

    override fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId>,
        conversationId: Matcher<ConversationId>
    ) {
        given(compositeMessageRepository)
            .suspendFunction(compositeMessageRepository::resetSelection)
            .whenInvokedWith(messageId, conversationId)
            .thenReturn(result)
    }

}
