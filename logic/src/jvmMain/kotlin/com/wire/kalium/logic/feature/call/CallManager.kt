package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.kaliumLogger

actual class CallManager {

    init {
        kaliumLogger.w("CallManager initialized for JVM but no supported yet.")
    }

    actual suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) {
        kaliumLogger.w("onCallingMessageReceived for JVM but no supported yet.")
    }
}
