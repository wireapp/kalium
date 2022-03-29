package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.user.UserRepository

expect class GlobalCallManager {

    fun getCallManagerForClient(
        userId: String,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository
    ): CallManager
}
