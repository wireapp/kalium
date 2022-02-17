package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId

class MessageScope(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val userId: UserId
    // cryptography things
) {

    val sendTextMessage: SendTextMessageUseCase get() = SendTextMessageUseCase(messageRepository, userId, clientRepository)
    val getRecentMessages: GetRecentMessagesUseCase get() = GetRecentMessagesUseCase(messageRepository)
}
