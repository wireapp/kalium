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
