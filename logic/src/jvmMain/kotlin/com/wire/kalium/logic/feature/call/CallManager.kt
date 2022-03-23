package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.Flow

actual class CallManager actual constructor(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val callRepository: CallRepository
) {

    actual suspend fun activeCallsIDs(): Flow<List<String>> {
        TODO("Not yet implemented")
    }

    actual suspend fun start() {
        TODO("Not yet implemented")
    }

    actual suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) {
        TODO("Not yet implemented")
    }

}
