package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries
import kotlin.coroutines.CoroutineContext

actual interface MessageExtensions
actual class MessageExtensionsImpl actual constructor(
    messagesQueries: MessagesQueries,
    messageMapper: MessageMapper,
    coroutineContext: CoroutineContext
) : MessageExtensions
