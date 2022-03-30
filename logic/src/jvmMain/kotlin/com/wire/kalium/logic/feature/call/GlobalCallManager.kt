package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.network.api.NonQualifiedUserId

actual class GlobalCallManager {

    actual fun getCallManagerForClient(
        userId: NonQualifiedUserId,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository
    ): CallManager = CallManager()
}
