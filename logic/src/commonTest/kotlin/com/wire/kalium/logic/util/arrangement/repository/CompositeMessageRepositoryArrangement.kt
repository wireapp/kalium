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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageButtonId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

interface CompositeMessageRepositoryArrangement {
    @Mock
    val compositeMessageRepository: CompositeMessageRepository

    suspend fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf()),
        buttonId: Matcher<MessageButtonId> = AnyMatcher(valueOf())
    )

    suspend fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    )
}

class CompositeMessageRepositoryArrangementImpl : CompositeMessageRepositoryArrangement {
    @Mock
    override val compositeMessageRepository: CompositeMessageRepository = mock(CompositeMessageRepository::class)

    override suspend fun withMarkSelected(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId>,
        conversationId: Matcher<ConversationId>,
        buttonId: Matcher<MessageButtonId>
    ) {
        coEvery {
            compositeMessageRepository.markSelected(
                matches { messageId.matches(it) },
                matches { conversationId.matches(it) },
                matches { buttonId.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withClearSelection(
        result: Either<StorageFailure, Unit>,
        messageId: Matcher<MessageId>,
        conversationId: Matcher<ConversationId>
    ) {
        coEvery {
            compositeMessageRepository.resetSelection(
                matches { messageId.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(result)
    }

}
