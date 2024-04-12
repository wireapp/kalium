/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver

actual class GlobalCallManager {
    @Suppress("LongParameterList")
    internal actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        userRepository: UserRepository,
        currentClientIdProvider: CurrentClientIdProvider,
        selfConversationIdProvider: SelfConversationIdProvider,
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper,
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper: QualifiedIdMapper,
        videoStateChecker: VideoStateChecker,
        conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
        networkStateObserver: NetworkStateObserver,
        kaliumConfigs: KaliumConfigs
    ): CallManager {
        return CallManagerImpl()
    }

    actual suspend fun removeInMemoryCallingManagerForUser(userId: UserId) {
        TODO("Not yet implemented")
    }

    actual fun getFlowManager(): FlowManagerService {
        TODO("Not yet implemented")
    }

    actual fun getMediaManager(): MediaManagerService {
        TODO("Not yet implemented")
    }

}
