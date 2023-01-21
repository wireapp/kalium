package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries
import kotlin.coroutines.CoroutineContext

expect interface MessageExtensions
expect class MessageExtensionsImpl(
    messagesQueries: MessagesQueries,
    messageMapper: MessageMapper,
    coroutineContext: CoroutineContext
) : MessageExtensions
