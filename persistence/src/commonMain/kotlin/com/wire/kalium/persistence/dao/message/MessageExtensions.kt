package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries

expect interface MessageExtensions
expect class MessageExtensionsImpl(messagesQueries: MessagesQueries, messageMapper: MessageMapper) : MessageExtensions
