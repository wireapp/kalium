/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network.api.authenticated.notification

import com.wire.kalium.network.api.authenticated.notification.user.UserUpdateEventData
import com.wire.kalium.network.api.authenticated.self.UserUpdateRequest
import com.wire.kalium.network.tools.KtxSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class UserTextStatusSerializationTest {
    private val json = KtxSerializer.json

    @Test
    fun unrelatedProfileUpdatesOmitTextStatus() {
        val encoded = json.encodeToString(UserUpdateRequest(name = "New name", assets = null, accentId = null))
        assertFalse("text_status" in json.parseToJsonElement(encoded).jsonObject)
    }

    @Test
    fun statusUpdatesSerializeTheWireFieldIncludingTheClearSentinel() {
        for (status in listOf("🎧 In a meeting", " ")) {
            val encoded = json.encodeToString(UserUpdateRequest(name = null, assets = null, accentId = null, textStatus = status))
            assertEquals(status, json.parseToJsonElement(encoded).jsonObject.getValue("text_status").jsonPrimitive.content)
        }
    }

    @Test
    fun oldEventsWithoutStatusRemainDecodableAndClearEventsKeepTheirValue() {
        assertNull(json.decodeFromString<UserUpdateEventData>("""{"id":"user","name":"New name"}""").textStatus)
        assertEquals(
            " ",
            json.decodeFromString<UserUpdateEventData>("""{"id":"user","text_status":" "}""").textStatus,
        )
    }
}
