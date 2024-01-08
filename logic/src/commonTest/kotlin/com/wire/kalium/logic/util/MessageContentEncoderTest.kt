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

package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.util.string.toHexString
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MessageContentEncoderTest {

    private val messageContentEncoder: MessageContentEncoder = MessageContentEncoder()

    @Test
    fun givenAMessageBodyWithEmoji_whenEncoding_ThenResultHasExpectedHexResult() = runTest {
        // given / when
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = textWithEmoji.first.second,
            messageContent = MessageContent.Text(textWithEmoji.first.first)
        )
        // then
        assertNotNull(result)
        assertEquals(result.asHexString, textWithEmoji.second.first)
    }

    @Test
    fun givenAMessageBodyWithUrl_whenEncoding_ThenResultHasExpectedHexResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = url.first.second,
            messageContent = MessageContent.Text(url.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.asHexString, url.second.first)
    }

    @Test
    fun givenAMessageBodyWithArabic_whenEncoding_ThenResultHasExpectedHexResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = arabic.first.second,
            messageContent = MessageContent.Text(arabic.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.asHexString, arabic.second.first)
    }

    @Test
    fun givenAMessageBodyWithMarkDown_whenEncoding_ThenResultHasExpectedHexResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = markDown.first.second,
            messageContent = MessageContent.Text(markDown.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.asHexString, markDown.second.first)
    }

    @Test
    fun givenAMessageBodyWithEmoji_whenEncoding_ThenResultHasExpectedSHA256HashResult() = runTest {
        // given / when
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = textWithEmoji.first.second,
            messageContent = MessageContent.Text(textWithEmoji.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.sha256Digest.toHexString(), textWithEmoji.second.second)
    }

    @Test
    fun givenAMessageBodyWithUrl_whenEncoding_ThenResultHasExpectedSHA256HashResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = url.first.second,
            messageContent = MessageContent.Text(url.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.sha256Digest.toHexString(), url.second.second)
    }

    @Test
    fun givenAMessageBodyWithArabic_whenEncoding_ThenResultHasExpectedSHA256HashResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = arabic.first.second,
            messageContent = MessageContent.Text(arabic.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.sha256Digest.toHexString(), arabic.second.second)
    }

    @Test
    fun givenAMessageBodyWithMarkDown_whenEncoding_ThenResultHasExpectedSHA256HashResult() = runTest {
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = markDown.first.second,
            messageContent = MessageContent.Text(markDown.first.first)
        )

        // then
        assertNotNull(result)
        assertEquals(result.sha256Digest.toHexString(), markDown.second.second)
    }

    @Test
    fun givenALocationMessage_whenEncoding_ThenResultHasExpectedSHA256HashResult() = runTest {
        val (locationMessage, messageDate, expectedHash) = location
        val result = messageContentEncoder.encodeMessageContent(
            messageDate = messageDate,
            messageContent = locationMessage
        )

        // then
        assertNotNull(result)
        assertEquals(expectedHash, result.sha256Digest.toHexString())
    }

    private companion object TestData {
        val textWithEmoji =
            (
                    "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!" to
                            "2018-10-22T15:09:29.000+02:00"
                    ) to
                    (
                            "feff00480065006c006c006f0020d83ddc69200dd83ddcbbd83ddc68200dd83ddc69200dd83ddc670021000000005bcdcc09" to
                                    "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
                            )

        val url = (
                "https://www.youtube.com/watch?v=DLzxrzFCyOs" to
                        "2018-10-22T15:09:29.000+02:00"
                ) to
                ("feff00680074007400700073003a002f002f007700770077002e" +
                        "0079006f00750074007500620065002e0063006f006d002f007700610" +
                        "07400630068003f0076003d0044004c007a00780072007a0046004300" +
                        "79004f0073000000005bcdcc09" to
                        "ef39934807203191c404ebb3acba0d33ec9dce669f9acec49710d520c365b657"
                        )

        val arabic = (
                "بغداد" to
                        "2018-10-22T15:12:45.000+02:00"
                ) to
                (
                        "feff0628063a062f0627062f000000005bcdcccd" to
                                "5830012f6f14c031bf21aded5b07af6e2d02d01074f137d106d4645e4dc539ca"
                        )

        val markDown = (
                "This has **markdown**" to
                        "2018-10-22T15:12:45.000+02:00"
                ) to ("feff005400680069007300200068006100730020002a" +
                "002a006d00610072006b0064006f0077006e002a002a00000" +
                "0005bcdcccd" to
                "f25a925d55116800e66872d2a82d8292adf1d4177195703f976bc884d32b5c94"
                )

        val location = Triple(
            MessageContent.Location(52.516666f, 13.4f, "someLocation", 10),
            "2018-10-22T15:09:29.000+02:00",
            "56a5fa30081bc16688574fdfbbe96c2eee004d1fb37dc714eec6efb340192816"
        )
    }
}
