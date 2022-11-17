package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageContentEncryptor
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageContentEncryptorTest {

    @Mock
    private val messageRepository: MessageRepository = mock(MessageRepository::class)

    private val messageContentEncryptor: MessageContentEncryptor = MessageContentEncryptor(messageRepository)

    @Test
    fun test() = runTest {
        // given
        given(messageRepository).suspendFunction(messageRepository::getMessageById).whenInvokedWith(anything(), anything())
            .then { _, _ ->
                Either.Right(
                    Message.Regular(
                        id = "someTestId",
                        content = MessageContent.Text(
                            value = TestData.textWithEmoji.first.first,
                            mentions = listOf(),
                            quotedMessageReference = null,
                            quotedMessageDetails = null
                        ),
                        conversationId = ConversationId("someConversationValue", "someConversationDomain"),
                        date = TestData.textWithEmoji.first.second,
                        senderUserId = UserId("someValue", "someDomain"),
                        status = Message.Status.READ,
                        visibility = Message.Visibility.VISIBLE,
                        senderClientId = PlainId(value = ""),
                        editStatus = Message.EditStatus.NotEdited,
                        reactions = Message.Reactions(
                            totalReactions = mapOf(),
                            selfUserReactions = setOf()
                        )
                    )
                )
            }
        // when
        val result =
            messageContentEncryptor.encryptMessageContent(
                conversationId = ConversationId("someConversationValue", "someConversationDomain"),
                messageId = "https://www.youtube.com/watch?v=DLzxrzFCyOs"
            )
        // then
//         assertIs<Either.Right<String>>(result)
//         assertEquals(result.value, TestData.textWithEmoji.second)
    }

}


private object TestData {
    val textWithEmoji =
        ("Hello \uD83D\uDC69\u200D\uD83D\uDCBB\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67!" to "2018-10-22T15:09:29.000+02:00") to "4f8ee55a8b71a7eb7447301d1bd0c8429971583b15a91594b45dee16f208afd5"
    val url = "https://www.youtube.com/watch?v=DLzxrzFCyOs" to "2018-10-22, 3:09:29 PM"
    val arabic = "بغداد" to "(2018-10-22, 3:12:45 PM"
    val markDown = "This has **markdown**" to "(2018-10-22, 3:12:45 PM"
}
