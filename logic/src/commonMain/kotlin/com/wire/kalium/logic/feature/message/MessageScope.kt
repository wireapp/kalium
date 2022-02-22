package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager

class MessageScope(
    private val messageRepository: MessageRepository,
    private val clientRepository: ClientRepository,
    private val userRepository: UserRepository,
    private val syncManager: SyncManager
) {

    val sendTextMessage: SendTextMessageUseCase
        get() = SendTextMessageUseCase(
            messageRepository,
            userRepository,
            clientRepository,
            syncManager
        )
    val getRecentMessages: GetRecentMessagesUseCase get() = GetRecentMessagesUseCase(messageRepository)
}
