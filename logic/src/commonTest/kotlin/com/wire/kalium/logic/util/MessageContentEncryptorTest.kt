package com.wire.kalium.logic.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageContentEncryptorTest {

    private val messageContentEncryptor: MessageContentEncoder = MessageContentEncoder()

    @Test
    fun test() = runTest {
        // given
//         given(messageRepository).suspendFunction(messageRepository::getMessageById).whenInvokedWith(anything(), anything())
//             .then { _, _ ->
//                 Either.Right(
//                     Message.Regular(
//                         id = "someTestId",
//                         content = MessageContent.Text(
//                             value = TestData.textWithEmoji.first.first,
//                             mentions = listOf(),
//                             quotedMessageReference = null,
//                             quotedMessageDetails = null
//                         ),
//                         conversationId = ConversationId("someConversationValue", "someConversationDomain"),
//                         date = TestData.textWithEmoji.first.second,
//                         senderUserId = UserId("someValue", "someDomain"),
//                         status = Message.Status.READ,
//                         visibility = Message.Visibility.VISIBLE,
//                         senderClientId = PlainId(value = ""),
//                         editStatus = Message.EditStatus.NotEdited,
//                         reactions = Message.Reactions(
//                             totalReactions = mapOf(),
//                             selfUserReactions = setOf()
//                         )
//                     )
//                 )
//             }
        // given / when
        val result = messageContentEncryptor.encodeMessageTextBody(
            messageTimeStampInMillis = textWithEmoji.first.second,
            messageTextBody = textWithEmoji.first.first
        )

        // then
        assertEquals(result.asHexString, textWithEmoji.second)
    }

    @Test
    fun test1() = runTest {
        val result = messageContentEncryptor.encodeMessageTextBody(
            messageTimeStampInMillis = url.first.second,
            messageTextBody = url.first.first
        )

        // then
        assertEquals(result.asHexString, url.second)
    }

    @Test
    fun test2() = runTest {
        val result = messageContentEncryptor.encodeMessageTextBody(
            messageTimeStampInMillis = arabic.first.second,
            messageTextBody = arabic.first.first
        )

        // then
        assertEquals(result.asHexString, arabic.second)
    }

    @Test
    fun test3() = runTest {
        val result = messageContentEncryptor.encodeMessageTextBody(
            messageTimeStampInMillis = arabic.first.second,
            messageTextBody = arabic.first.first
        )

        // then
        assertEquals(result.asHexString, arabic.second)
    }

    @Test
    fun test4() = runTest {
        val result = messageContentEncryptor.encodeMessageTextBody(
            messageTimeStampInMillis = markDown.first.second,
            messageTextBody = markDown.first.first
        )

        // then
        assertEquals(result.asHexString, markDown.second)
    }

    private companion object TestData {
        val textWithEmoji =
            (
                    "Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!" to
                            "2018-10-22T15:09:29.000+02:00".toTimeInMillis()
                    ) to "feff00480065006c006c006f0020d83ddc69200dd83ddcbbd83ddc68200dd83ddc69200dd83ddc670021000000005bcdcc09"

        val url = (
                "https://www.youtube.com/watch?v=DLzxrzFCyOs" to
                        "2018-10-22T15:09:29.000+02:00".toTimeInMillis()
                ) to "feff00680074007400700073003a002f002f007700770077002e" +
                "0079006f00750074007500620065002e0063006f006d002f007700610" +
                "07400630068003f0076003d0044004c007a00780072007a0046004300" +
                "79004f0073000000005bcdcc09"

        val arabic = (
                "بغداد" to
                        "2018-10-22T15:12:45.000+02:00".toTimeInMillis()
                ) to "feff0628063a062f0627062f000000005bcdcccd"

        val markDown = (
                "This has **markdown**" to
                        "2018-10-22T15:12:45.000+02:00".toTimeInMillis()
                ) to "feff005400680069007300200068006100730020002a" +
                "002a006d00610072006b0064006f0077006e002a002a00000" +
                "0005bcdcccd"
    }
}
