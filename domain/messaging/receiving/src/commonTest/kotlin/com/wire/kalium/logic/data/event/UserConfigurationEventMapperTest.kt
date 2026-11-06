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

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserConfigurationEventMapperTest {

    @Test
    fun givenScreenshotCensoringSetToOne_whenMapping_thenEnabledEventIsReturned() {
        val result = updateUserProperties(
            SELF_USER_ID,
            EVENT_ID,
            propertiesSet(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE, EventContentDTO.FieldKeyNumberValue(1))
        )

        assertEquals(Event.UserProperty.ScreenshotCensoringModeSet(EVENT_ID, true), result)
    }

    @Test
    fun givenScreenshotCensoringSetToZero_whenMapping_thenDisabledEventIsReturned() {
        val result = updateUserProperties(
            SELF_USER_ID,
            EVENT_ID,
            propertiesSet(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE, EventContentDTO.FieldKeyNumberValue(0))
        )

        assertEquals(Event.UserProperty.ScreenshotCensoringModeSet(EVENT_ID, false), result)
    }

    @Test
    fun givenScreenshotCensoringSetToStringOne_whenMapping_thenEnabledEventIsReturned() {
        val result = updateUserProperties(
            SELF_USER_ID,
            EVENT_ID,
            propertiesSet(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE, EventContentDTO.FieldUnknownValue("1"))
        )

        assertEquals(Event.UserProperty.ScreenshotCensoringModeSet(EVENT_ID, true), result)
    }

    @Test
    fun givenScreenshotCensoringSetToNonNumericString_whenMapping_thenUnknownEventIsReturned() {
        val result = updateUserProperties(
            SELF_USER_ID,
            EVENT_ID,
            propertiesSet(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE, EventContentDTO.FieldUnknownValue("enabled"))
        )

        assertIs<Event.Unknown>(result)
    }

    @Test
    fun givenScreenshotCensoringDeleted_whenMapping_thenDisabledEventIsReturned() {
        val result = deleteUserProperties(
            EVENT_ID,
            EventContentDTO.UserProperty.PropertiesDeleteDTO(PropertyKey.WIRE_SCREENSHOT_CENSORING_MODE.key)
        )

        assertEquals(Event.UserProperty.ScreenshotCensoringModeSet(EVENT_ID, false), result)
    }

    @Test
    fun givenReadReceiptsSetAsString_whenMapping_thenUnknownEventIsStillReturned() {
        val result = updateUserProperties(
            SELF_USER_ID,
            EVENT_ID,
            propertiesSet(PropertyKey.WIRE_RECEIPT_MODE, EventContentDTO.FieldUnknownValue("1"))
        )

        assertIs<Event.Unknown>(result)
    }

    @Test
    fun givenTypingIndicatorDeleted_whenMapping_thenEnabledEventIsReturned() {
        val result = deleteUserProperties(
            EVENT_ID,
            EventContentDTO.UserProperty.PropertiesDeleteDTO(PropertyKey.WIRE_TYPING_INDICATOR_MODE.key)
        )

        assertEquals(Event.UserProperty.TypingIndicatorModeSet(EVENT_ID, true), result)
    }

    private fun propertiesSet(key: PropertyKey, value: EventContentDTO.FieldKeyValue) =
        EventContentDTO.UserProperty.PropertiesSetDTO(key = key.key, value = value)

    private companion object {
        const val EVENT_ID = "event-id"
        val SELF_USER_ID = UserId("self", "example.com")
    }
}
