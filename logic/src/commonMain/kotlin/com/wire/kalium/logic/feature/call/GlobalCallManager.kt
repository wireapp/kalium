package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.MessageSender

expect class GlobalCallManager {

    @Suppress("LongParameterList")
    fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        messageSender: MessageSender,
        callMapper: CallMapper = MapperProvider.callMapper()
    ): CallManager

    fun getFlowManager(): FlowManagerService
}
