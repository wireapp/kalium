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
package com.wire.kalium.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LogAttributesTest {

    @Test
    fun givenFullUserTextTag_whenGettingFromString_thenShouldBeParsedCorrectly() {
        val prefix = "prefix"
        val userId = "userId"
        val clientId = "clientId"
        val userTextTag = "$prefix[$userId|$clientId]"

        val fromTag = KaliumLogger.LogAttributes.getInfoFromTagString(userTextTag)

        assertNotNull(fromTag)
        assertEquals(userId, fromTag.userClientData?.userId)
        assertEquals(clientId, fromTag.userClientData?.clientId)
        assertEquals(prefix, fromTag.textTag)
    }

    @Test
    fun givenEmptyClientIdInTextTag_whenGettingFromString_thenShouldBeParsedCorrectly() {
        val prefix = "prefix"
        val userId = "userId"
        val clientId = ""
        val userTextTag = "$prefix[$userId|$clientId]"

        val fromTag = KaliumLogger.LogAttributes.getInfoFromTagString(userTextTag)

        assertNotNull(fromTag)
        assertEquals(userId, fromTag.userClientData?.userId)
        assertEquals(clientId, fromTag.userClientData?.clientId)
        assertEquals(prefix, fromTag.textTag)
    }

    @Test
    fun givenNoAccountOrClientInfoInTag_whenGettingFromString_thenShouldBeParsedCorrectly() {
        val onlyPrefix = "prefix"

        val fromTag = KaliumLogger.LogAttributes.getInfoFromTagString(onlyPrefix)

        assertNotNull(fromTag)
        assertNull(fromTag.userClientData)
        assertEquals(onlyPrefix, fromTag.textTag)
    }
}
