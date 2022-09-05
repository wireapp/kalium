package com.wire.kalium.cryptography

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QualifiedIDTest {

    @IgnoreIOS
    @Test
    fun givenQualifiedUserID_whenCallingToString_EncodesCorrectly() {
        assertEquals(ENCODED_QUALIFIED_USER_ID, QUALIFIED_USER_ID.toString())
    }

    @Test
    fun givenQualifiedClientID_whenCallingToString_EncodesCorrectly() {
        assertEquals(ENCODED_QUALIFIED_CLIENT_ID, QUALIFIED_CLIENT_ID.toString())
    }

    @Test
    fun givenEncodedQualifiedClientID_whenCallingFromEncodedString_DecodesCorrectly() {
        assertEquals(QUALIFIED_CLIENT_ID, CryptoQualifiedClientId.fromEncodedString(ENCODED_QUALIFIED_CLIENT_ID))
    }

    @Test
    fun givenIncorrectlyEncodedQualifiedClientID_whenCallingFromEncodedString_ReturnsNull() {
        assertNull(CryptoQualifiedClientId.fromEncodedString(ENCODED_QUALIFIED_USER_ID))
    }

    companion object {
        private const val CLIENT_ID = "client_id"
        private const val USER_ID = "user_id"
        private const val DOMAIN = "domain"

        const val ENCODED_QUALIFIED_USER_ID = "$USER_ID@$DOMAIN"
        const val ENCODED_QUALIFIED_CLIENT_ID = "$USER_ID:$CLIENT_ID@$DOMAIN"

        val QUALIFIED_CLIENT_ID = CryptoQualifiedClientId(CLIENT_ID, CryptoQualifiedID(USER_ID, DOMAIN))
        val QUALIFIED_USER_ID = CryptoQualifiedID(USER_ID, DOMAIN)
    }
}
