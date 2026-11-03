/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UserConfigurationEventMapperTest {

    private val selfUserId = UserId("self", "domain")

    private fun setEvent(value: Int) = updateUserProperties(
        selfUserId,
        "event-id",
        EventContentDTO.UserProperty.PropertiesSetDTO(PropertyKey.WIRE_SHOW_NOTES.key, EventContentDTO.FieldKeyNumberValue(value))
    )

    @Test
    fun givenShowNotesSetToOne_whenMapping_thenShowNotesIsEnabled() {
        val event = assertIs<Event.UserProperty.ShowNotesModeSet>(setEvent(1))

        assertEquals(true, event.value)
    }

    @Test
    fun givenShowNotesSetToZero_whenMapping_thenShowNotesIsDisabled() {
        val event = assertIs<Event.UserProperty.ShowNotesModeSet>(setEvent(0))

        assertEquals(false, event.value)
    }

    @Test
    fun givenShowNotesDeleted_whenMapping_thenShowNotesIsDisabled() {
        val event = deleteUserProperties(
            "event-id",
            EventContentDTO.UserProperty.PropertiesDeleteDTO(PropertyKey.WIRE_SHOW_NOTES.key)
        )

        assertEquals(false, assertIs<Event.UserProperty.ShowNotesModeSet>(event).value)
    }

    @Test
    fun givenUnknownKey_whenMapping_thenEventIsUnknown() {
        val event = updateUserProperties(
            selfUserId,
            "event-id",
            EventContentDTO.UserProperty.PropertiesSetDTO("SOME_UNKNOWN_KEY", EventContentDTO.FieldKeyNumberValue(1))
        )

        assertIs<Event.Unknown>(event)
    }
}
