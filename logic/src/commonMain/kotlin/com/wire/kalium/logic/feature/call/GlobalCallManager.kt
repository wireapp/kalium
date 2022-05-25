package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender

expect class GlobalCallManager {

    fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        callMapper: CallMapper,
        messageSender: MessageSender
    ): CallManager

    fun getFlowManager() : FlowManagerService
}
