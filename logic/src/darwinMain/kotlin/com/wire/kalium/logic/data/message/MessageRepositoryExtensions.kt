package com.wire.kalium.logic.data.message

import com.wire.kalium.persistence.dao.message.MessageDAO

actual interface MessageRepositoryExtensions
actual class MessageRepositoryExtensionsImpl actual constructor(
    messageDAO: MessageDAO,
    messageMapper: MessageMapper
) : MessageRepositoryExtensions
