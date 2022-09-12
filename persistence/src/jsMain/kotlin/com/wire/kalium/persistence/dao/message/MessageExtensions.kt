package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries

actual interface MessageExtensions
actual class MessageExtensionsImpl actual constructor(
    messagesQueries: MessagesQueries,
    messageMapper: MessageMapper
) : MessageExtensions
