package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

actual class GlobalCallManager {

    @Suppress("LongParameterList")
    actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        clientRepository: ClientRepository,
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper,
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper : QualifiedIdMapper
    ): CallManager = CallManagerImpl()

    actual fun getFlowManager(): FlowManagerService = FlowManagerServiceImpl()
    actual fun getMediaManager(): MediaManagerService = MediaManagerServiceImpl()
}
