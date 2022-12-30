package com.wire.kalium.logic.data.message

import com.wire.kalium.persistence.dao.message.MessageDAO

expect interface MessageRepositoryExtensions

expect class MessageRepositoryExtensionsImpl(
    messageDAO: MessageDAO,
    messageMapper: MessageMapper
) : MessageRepositoryExtensions
