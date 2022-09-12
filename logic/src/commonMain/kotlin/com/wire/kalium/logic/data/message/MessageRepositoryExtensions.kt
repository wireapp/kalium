package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.message.MessageDAO

expect interface MessageRepositoryExtensions

expect class MessageRepositoryExtensionsImpl(
    messageDAO: MessageDAO,
    idMapper: IdMapper,
    messageMapper: MessageMapper
) : MessageRepositoryExtensions
