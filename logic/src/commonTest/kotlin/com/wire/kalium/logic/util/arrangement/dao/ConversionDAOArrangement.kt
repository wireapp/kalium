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
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

interface ConversionDAOArrangement {
    val conversionDAO: ConversationDAO

    suspend fun withDeleteGustLink(conversationId: Matcher<ConversationIDEntity> = AnyMatcher(valueOf())) {
        coEvery {
            conversionDAO.deleteGuestRoomLink(matches { conversationId.matches(it) })
        }.returns(Unit)
    }

    suspend fun withUpdatedGuestRoomLink(
        conversationId: Matcher<ConversationIDEntity> = AnyMatcher(valueOf()),
        uri: Matcher<String?> = AnyMatcher(valueOf()),
        isPasswordProtected: Matcher<Boolean> = AnyMatcher(valueOf())
    ) {
        coEvery {
            conversionDAO.updateGuestRoomLink(
                matches { conversationId.matches(it) },
                matches { uri.matches(it) },
                matches { isPasswordProtected.matches(it) }
            )
        }.returns(Unit)
    }
}

class ConversionDAOArrangementImpl : ConversionDAOArrangement {

    override val conversionDAO: ConversationDAO = mock(ConversationDAO::class)
}
