package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.MessageSender

expect class GlobalCallManager {

    @Suppress("LongParameterList")
    internal fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper = MapperProvider.callMapper(),
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper: QualifiedIdMapper,
        videoStateChecker: VideoStateChecker
    ): CallManager

    fun removeInMemoryCallingManagerForUser(userId: UserId)
    fun getFlowManager(): FlowManagerService
    fun getMediaManager(): MediaManagerService
}
