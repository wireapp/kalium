package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender

actual class GlobalCallManager {
    @Suppress("LongParameterList")
    internal actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        currentClientIdProvider: CurrentClientIdProvider,
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper,
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper: QualifiedIdMapper,
        videoStateChecker: VideoStateChecker
    ): CallManager {
        return CallManagerImpl()
    }

    actual fun removeInMemoryCallingManagerForUser(userId: UserId) {
        TODO("Not yet implemented")
    }

    actual fun getFlowManager(): FlowManagerService {
        TODO("Not yet implemented")
    }

    actual fun getMediaManager(): MediaManagerService {
        TODO("Not yet implemented")
    }

}
