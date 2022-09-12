package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries

expect class MessageExtensions(messagesQueries: MessagesQueries, messageMapper: MessageMapper)
