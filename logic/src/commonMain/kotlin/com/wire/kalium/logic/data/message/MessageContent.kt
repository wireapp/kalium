package com.wire.kalium.logic.data.message

sealed class MessageContent {
    data class Text(val value: String) : MessageContent()
    data class Calling(val value: String) : MessageContent()
    data class Asset(val value: AssetContent) : MessageContent()
    data class DeleteMessage(val messageId: String) : MessageContent()
    data class DeleteForMe(val messageId: String) : MessageContent()
    object Unknown : MessageContent()
}
