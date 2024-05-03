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

package com.wire.kalium.cryptography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionIDTest {

    @Test
    fun givenSessionID_whenCallingToString_EncodesCorrectly() {
        assertEquals(ENCODED_SESSION_ID, SESSION_ID.value)
    }

    @Test
    fun givenIncorrectlyEncodedSessionID_whenCallingFromEncodedString_ReturnsNull() {
        assertNull(CryptoSessionId.fromEncodedString(ENCODED_QUALIFIED_USER_ID))
    }

    companion object {
        private const val CLIENT_ID_RAW = "client_id"
        private const val USER_ID = "user_id"
        private const val DOMAIN = "domain"

        const val ENCODED_QUALIFIED_USER_ID = "$USER_ID@$DOMAIN"
        const val ENCODED_SESSION_ID = "$USER_ID@${DOMAIN}_$CLIENT_ID_RAW"

        private val CLIENT_ID = CryptoClientId(CLIENT_ID_RAW)
        private val QUALIFIED_USER_ID = CryptoQualifiedID(USER_ID, DOMAIN)
        val SESSION_ID = CryptoSessionId(QUALIFIED_USER_ID, CLIENT_ID)

    }
}
