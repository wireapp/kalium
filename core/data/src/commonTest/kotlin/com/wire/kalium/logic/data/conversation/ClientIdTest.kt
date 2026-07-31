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

package com.wire.kalium.logic.data.conversation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientIdTest {

    @Test
    fun givenClientId_whenSerializingAndDeserializing_thenUsesStringRepresentation() {
        val encoded = Json.encodeToString(ClientId(CLIENT_ID))

        assertEquals("\"$CLIENT_ID\"", encoded)
        assertEquals(ClientId(CLIENT_ID), Json.decodeFromString<ClientId>(encoded))
    }

    private companion object {
        const val CLIENT_ID = "client-id"
    }
}
