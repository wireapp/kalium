package com.wire.kalium.logic.util

import com.wire.kalium.logic.feature.message.MessageContentEncoder
import com.wire.kalium.util.string.toHexString
import com.wire.kalium.util.string.toUTF16BEByteArray
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageContentEncryptorTest {
//
//     @Mock
//     private val messageRepository: MessageRepository = mock(MessageRepository::class)

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
        // when
        val result = messageContentEncryptor.encryptMessageTextBody(
            messageTimeStampInMillis = textWithEmoji.first.second,
            messageTextBody = textWithEmoji.first.first
        )

        // then
//         assertIs<Either.Right<String>>(result)
        assertEquals(result.toHexString(), textWithEmoji.second)
        println("Test")
    }

    @Test
    fun test1() = runTest {
//         val test = "https://www.youtube.com/watch?v=DLzxrzFCyOs"
//         val dupa123 = test.toUTF16BEByteArray()
//
//         val test2 = ("2018-10-22T15:09:29.000+02:00".toTimeInMillis()) / 1000
//         val jelop = test2.toString().toUTF16BEByteArray()
//         val array = dupa123 + jelop
//         println(array)
//         val gamon = "test"
//
//         val expectedArrayAsHex = hex("feff00480065006c006c006f0020d83ddc69200dd83ddcbbd83ddc68200dd83ddc69200dd83ddc670021000000005bcdcc09")
    }

    private companion object TestData {
        val textWithEmoji =
            ("Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!" to "2018-10-22T15:09:29.000+02:00".toTimeInMillis()) to "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
        val url = "https://www.youtube.com/watch?v=DLzxrzFCyOs" to "2018-10-22T15:09:29.000+02:00".toTimeInMillis()
        val arabic = "بغداد" to "(2018-10-22, 3:12:45 PM"
        val markDown = "This has **markdown**" to "(2018-10-22, 3:12:45 PM"
    }
}


