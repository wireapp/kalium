/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.content.ButtonContentQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

interface CompositeMessageDAO {
    suspend fun markAsSelected(
        messageId: String,
        conversationId: QualifiedIDEntity,
        buttonId: String
    )

    suspend fun resetSelection(
        messageId: String,
        conversationId: QualifiedIDEntity
    )
}

internal class CompositeMessageDAOImpl internal constructor(
    private val buttonContentQueries: ButtonContentQueries,
    private val context: CoroutineContext
) : CompositeMessageDAO {
    override suspend fun markAsSelected(messageId: String, conversationId: QualifiedIDEntity, buttonId: String) = withContext(context) {
        buttonContentQueries.markSelected(conversation_id = conversationId, message_id = messageId, id = buttonId)
    }

    override suspend fun resetSelection(messageId: String, conversationId: QualifiedIDEntity) = withContext(context) {
        buttonContentQueries.remmoveAllSelection(conversation_id = conversationId, message_id = messageId)
    }
}
