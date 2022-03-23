package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

expect class CallManager(
    userRepository: UserRepository,
    clientRepository: ClientRepository
) {

    suspend fun activeCallsIDs(): Flow<List<String>>
    suspend fun start()
    suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling)
}
