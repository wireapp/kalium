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
        // Sending a simple text message
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
        // Sending a text message with mention
        val text = "Hello, @John"
        val johnMention = MessageMention(
            start = 8, // The index of the @ in the text above
            length = 5, // The length of the mention (including the @)
            userId = johnUserId // ID of the user being mentioned
        )
        sendTextMessageUseCase.invoke(
            conversationId = conversationId,
            text = text,
            mentions = listOf(johnMention)
        )
    }
}
