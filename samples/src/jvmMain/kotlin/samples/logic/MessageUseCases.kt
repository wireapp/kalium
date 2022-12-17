package samples.logic

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.SendTextMessageUseCase

object MessageUseCases {

    suspend fun sendingBasicTextMessage(
        sendTextMessageUseCase: SendTextMessageUseCase,
        conversationId: ConversationId
    ) {
        sendTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = "Hello"
        )
    }

    suspend fun sendingTextMessageWithMentions(
        sendTextMessageUseCase: SendTextMessageUseCase,
        conversationId: ConversationId,
        johnUserId: UserId
    ) {
        val johnMention = MessageMention(8, 5, johnUserId)
        sendTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = "Hello, @John",
            mentions = listOf(johnMention)
        )
    }
}
