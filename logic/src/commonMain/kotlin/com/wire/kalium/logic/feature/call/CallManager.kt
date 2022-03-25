package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent

expect class CallManager {

    suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling)
}
