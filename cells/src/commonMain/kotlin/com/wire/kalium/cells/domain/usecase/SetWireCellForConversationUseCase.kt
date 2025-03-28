/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This UseCase is a temporary solution to set the wire cell for a conversation.
 * To be removed once the wire cell feature is fully integrated on BE.
 */
public interface SetWireCellForConversationUseCase {
    public suspend operator fun invoke(conversationId: ConversationId, enabled: Boolean)
}

internal class SetWireCellForConversationUseCaseImpl internal constructor(
    private val repository: CellConversationRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : SetWireCellForConversationUseCase {
    override suspend operator fun invoke(conversationId: ConversationId, enabled: Boolean) {
        withContext(dispatchers.io) {
            val cellName = if (enabled) "$conversationId" else null
            repository.setWireCell(conversationId, cellName)
        }
    }
}
