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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface MessageSenderArrangement {
    @Mock
    val messageSender: MessageSender

    suspend fun withSendMessageSucceed(
        message: Matcher<Message.Sendable> = AnyMatcher(valueOf()),
        target: Matcher<MessageTarget> = AnyMatcher(valueOf())
    )

    suspend fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: Matcher<Message.Sendable> = AnyMatcher(valueOf()),
        target: Matcher<MessageTarget> = AnyMatcher(valueOf())
    )
}

internal open class MessageSenderArrangementImpl : MessageSenderArrangement {
    @Mock
    override val messageSender: MessageSender = mock(MessageSender::class)

    override suspend fun withSendMessageSucceed(
        message: Matcher<Message.Sendable>,
        target: Matcher<MessageTarget>
    ) {
        coEvery {
            messageSender.sendMessage(matches { message.matches(it) }, matches { target.matches(it) })
        }.returns(Either.Right(Unit))
    }

    override suspend fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: Matcher<Message.Sendable>,
        target: Matcher<MessageTarget>
    ) {
        coEvery {
            messageSender.sendMessage(matches { message.matches(it) }, matches { target.matches(it) })
        }.returns(result)
    }

}
