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
package com.wire.kalium.logic.util.arrangement

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface MessageSenderArrangement {
    @Mock
    val messageSender: MessageSender

    fun withSendMessageSucceed(
        message: Matcher<Message.Sendable> = any(),
        target: Matcher<MessageTarget> = any()
    )

    fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: Matcher<Message.Sendable> = any(),
        target: Matcher<MessageTarget> = any()
    )
}

internal open class MessageSenderArrangementImpl : MessageSenderArrangement {
    @Mock
    override val messageSender: MessageSender = mock(MessageSender::class)

    override fun withSendMessageSucceed(
        message: Matcher<Message.Sendable>,
        target: Matcher<MessageTarget>
    ) {
        given(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .whenInvokedWith(message, target)
            .thenReturn(Either.Right(Unit))
    }

    override fun withMessageSenderFailure(
        result: Either.Left<CoreFailure>,
        message: Matcher<Message.Sendable>,
        target: Matcher<MessageTarget>
    ) {
        given(messageSender)
            .suspendFunction(messageSender::sendMessage)
            .whenInvokedWith(message, target)
            .thenReturn(result)
    }

}
