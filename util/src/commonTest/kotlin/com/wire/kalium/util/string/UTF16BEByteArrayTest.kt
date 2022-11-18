package com.wire.kalium.util.string

import kotlin.test.Test
import kotlin.test.assertEquals

class UTF16BEByteArrayTest {

    @Test
    fun givenTextBody_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = textBody.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, textBody.second)
        assertEquals(textBody.first, encodedTextBody.toStringFromUtf16BE())
    }


    @Test
    fun givenUrl_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = textBody.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, textBody.second)
        assertEquals(textBody.first, encodedTextBody.toStringFromUtf16BE())
    }

    @Test
    fun givenArabic_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = textBody.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, textBody.second)
        assertEquals(textBody.first, encodedTextBody.toStringFromUtf16BE())
    }

    @Test
    fun givenMarkDown_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = textBody.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, textBody.second)
        assertEquals(textBody.first, encodedTextBody.toStringFromUtf16BE())
    }


    companion object TestData {
        val textBody = ("\"Hello \\uD83D\\uDC69\\u200D\\uD83D\\uDCBB\\uD83D\\uDC68\\u200D\\uD83D\\uDC69\\u200D\\uD83D\\uDC67!\""
                to "00480065006c006c006f0020d83ddc69200dd83ddcbbd83ddc68200dd83ddc69200dd83ddc670021")

        00680074007400700073003a002f002f007700770077002e0079006f00750074007500620065002e0063006f006d002f00770061007400630068003f0076003d0044004c007a00780072007a004600430079004f0073
    }
}

