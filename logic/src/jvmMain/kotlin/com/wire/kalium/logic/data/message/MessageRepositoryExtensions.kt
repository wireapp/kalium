package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.message.MessageDAO

actual interface MessageRepositoryExtensions

actual class MessageRepositoryExtensionsImpl actual constructor(
    messageDAO: MessageDAO,
    idMapper: IdMapper,
    messageMapper: MessageMapper
) : MessageRepositoryExtensions
