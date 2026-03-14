/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventMapperTest {

    @Test
    fun givenSessionRefreshSuggestedDTO_whenMapping_thenSessionRefreshSuggestedEventIsReturned() {
        val eventId = "event-id"
        val mapper = MapperProvider.eventMapper(TestUser.SELF.id)

        val result = mapper.fromEventContentDTO(eventId, EventContentDTO.User.SessionRefreshSuggestedDTO)

        val event = assertIs<Event.User.SessionRefreshSuggested>(result)
        assertEquals(eventId, event.id)
    }
}
