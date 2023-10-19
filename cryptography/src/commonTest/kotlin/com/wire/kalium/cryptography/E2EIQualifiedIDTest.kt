/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import io.ktor.util.encodeBase64
import kotlin.test.Test
import kotlin.test.assertEquals

class E2EIQualifiedIDTest {

    @Test
    fun givenE2EIQualifiedClientID_whenCallingToString_EncodesCorrectly() {
        assertEquals(ENCODED_E2EI_QUALIFIED_CLIENT_ID, E2EI_QUALIFIED_CLIENT_ID.toString())
    }

    companion object {
        private const val CLIENT_ID = "client_id"
        private const val USER_ID = "41d2b365-f4a9-4ba1-bddf-5afb8aca6786"
        private const val DOMAIN = "domain"
        private const val ENCODED_USER_ID = "QdKzZfSpS6G931r7ispnhg"

        const val ENCODED_E2EI_QUALIFIED_CLIENT_ID = "${ENCODED_USER_ID}:$CLIENT_ID@$DOMAIN"

        val E2EI_QUALIFIED_CLIENT_ID = E2EIQualifiedClientId(CLIENT_ID,  CryptoQualifiedID(USER_ID, DOMAIN))
    }
}
