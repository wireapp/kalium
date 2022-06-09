package com.wire.kalium.logic.data.message

data class ProtoContent(
    val messageUid: String,
    val messageContent: MessageContent.Client,
    val visibility: Message.Visibility = Message.Visibility.VISIBLE
)
