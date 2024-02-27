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
import io.mockative.*
import io.mockative.matchers.Matcher

interface ConversionDAOArrangement {
    val conversionDAO: ConversationDAO

    fun withDeleteGustLink(conversationId: Matcher<ConversationIDEntity> = any()) {
        given(conversionDAO)
            .suspendFunction(conversionDAO::deleteGuestRoomLink)
            .whenInvokedWith(conversationId)
            .thenReturn(Unit)
    }

    fun withUpdatedGuestRoomLink(
        conversationId: Matcher<ConversationIDEntity> = any(),
        uri: Matcher<String?> = any(),
        isPasswordProtected: Matcher<Boolean> = any()
    ) {
        given(conversionDAO)
            .suspendFunction(conversionDAO::updateGuestRoomLink)
            .whenInvokedWith(conversationId, uri, isPasswordProtected)
            .thenReturn(Unit)
    }
}

class ConversionDAOArrangementImpl : ConversionDAOArrangement {
    @Mock
    override val conversionDAO: ConversationDAO = mock(ConversationDAO::class)
}
