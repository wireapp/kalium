package com.wire.kalium.logic.data.message

sealed class MessageContent {

    data class Text(val value: String): MessageContent()

    object Unknown: MessageContent()
}
