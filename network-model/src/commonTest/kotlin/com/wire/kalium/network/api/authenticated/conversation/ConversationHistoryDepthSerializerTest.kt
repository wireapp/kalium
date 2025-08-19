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
package com.wire.kalium.network.api.authenticated.conversation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ConversationHistoryDepthSerializerTest {

    @Test
    fun `should serialize and deserialize duration correctly`() {
        val original = ConversationHistorySettingsDTO.SharedWithNewMembers(92.days + 42.hours + 24.minutes + 11.seconds)
        val json = Json { }

        val string = json.encodeToString(original)
        val result = json.decodeFromString<ConversationHistorySettingsDTO.SharedWithNewMembers>(string)

        assertEquals(original, result)
    }
}
