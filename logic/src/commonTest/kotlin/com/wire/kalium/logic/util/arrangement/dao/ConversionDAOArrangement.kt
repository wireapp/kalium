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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.mock

interface ConversionDAOArrangement {
    val conversionDAO: ConversationDAO

    suspend fun withDeleteGustLink(conversationId: (ConversationIDEntity) -> Boolean = { true }) {
        everySuspend {
            conversionDAO.deleteGuestRoomLink(matches { conversationId(it) })
        } returns Unit
    }

    suspend fun withUpdatedGuestRoomLink(
        conversationId: (ConversationIDEntity) -> Boolean = { true },
        uri: (String?) -> Boolean = { true },
        isPasswordProtected: (Boolean) -> Boolean = { true }
    ) {
        everySuspend {
            conversionDAO.updateGuestRoomLink(
                matches { conversationId(it) },
                matches { uri(it) },
                matches { isPasswordProtected(it) }
            )
        } returns Unit
    }
}

class ConversionDAOArrangementImpl : ConversionDAOArrangement {

    override val conversionDAO: ConversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
}
