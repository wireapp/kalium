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

package com.wire.kalium.util.string

import kotlin.test.Test
import kotlin.test.assertEquals

@IgnoreIOS
@IgnoreJS
class UTF16BEByteArrayTest {

    // textBody contains unicode for emoji's, when converting back to hex function is not that clever
    // to convert it to to the hex representation.
    @Test
    fun givenTextBody_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = textBody.first.toUTF16BEByteArray()
        assertEquals(textBody.first, encodedTextBody.toStringFromUtf16BE())
    }

    @Test
    fun givenUrl_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = url.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, url.second)
        assertEquals(url.first, encodedTextBody.toStringFromUtf16BE())
    }

    @Test
    fun givenArabic_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = arabic.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, arabic.second)
        assertEquals(arabic.first, encodedTextBody.toStringFromUtf16BE())
    }

    @Test
    fun givenMarkDown_whenEncodingToUtf16AdnBack_theResultHasExpectedValues() {
        val encodedTextBody = markDown.first.toUTF16BEByteArray()
        val hexString = encodedTextBody.toHexString()

        assertEquals(hexString, markDown.second)
        assertEquals(markDown.first, encodedTextBody.toStringFromUtf16BE())
    }

    companion object TestData {
        val textBody = (
                "Hello \\uD83D\\uDC69\\u200D\\uD83D\\uDCBB\\uD83D\\uDC68\\u200D\\uD83D\\uDC69\\u200D\\uD83D\\uDC67!"
                        to "00480065006c006c006f0020d83ddc69200dd83ddcbbd83ddc68200dd83ddc69200dd83ddc670021"
                )

        val url = (
                "https://www.youtube.com/watch?v=DLzxrzFCyOs" to
                        "00680074007400700073003a002f002f007700770077002e0" +
                        "079006f00750074007500620065002e0063006f006d002f00" +
                        "770061007400630068003f0076003d0044004c007a00780072007a004600430079004f0073"
                )

        val arabic = ("بغداد" to "0628063a062f0627062f")

        val markDown = (
                "This has **markdown**" to
                        "005400680069007300200068006100730020002a002a006d00610072006b0064006f0077006e002a002a"
                )
    }
}
