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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.common.functional.Either
import com.wire.kalium.messaging.sending.MessageTarget
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher

internal interface MessageSenderArrangement {

    val messageSender: MessageSender

    suspend fun withSendMessageSucceed(
        message: (Message.Sendable) -> Boolean = { true },
        target: (MessageTarget) -> Boolean = { true },
        threadId: Matcher<String?> = AnyMatcher(valueOf())
    )

    suspend fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: (Message.Sendable) -> Boolean = { true },
        target: (MessageTarget) -> Boolean = { true },
        threadId: Matcher<String?> = AnyMatcher(valueOf())
    )
}

internal open class MessageSenderArrangementImpl : MessageSenderArrangement {

    override val messageSender: MessageSender = mock<MessageSender>(mode = MockMode.autoUnit)

    override suspend fun withSendMessageSucceed(
        message: (Message.Sendable) -> Boolean,
        target: (MessageTarget) -> Boolean
    ) {
        everySuspend {
            messageSender.sendMessage(
                matches { message(it) },
                matches { target(it) },
                matches { threadId.matches(it) }
            )
        }.returns(Either.Right(Unit))
    }

    override suspend fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: (Message.Sendable) -> Boolean,
        target: (MessageTarget) -> Boolean
    ) {
        everySuspend {
            messageSender.sendMessage(
                matches { message(it) },
                matches { target(it) },
                matches { threadId.matches(it) }
            )
        }.returns(result)
    }

}
