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

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.CurrentPlatform
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.PlatformType
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

actual class GlobalCallManager(
    appContext: PlatformContext,
    scope: CoroutineScope
) {
    private val callManagerHolder: ConcurrentMap<QualifiedID, CallManager> by lazy {
        ConcurrentHashMap()
    }

    private val calling by lazy {
        Calling.INSTANCE.apply {
            if (CurrentPlatform().type == PlatformType.ANDROID)
                wcall_init(env = ENVIRONMENT_DEFAULT)
            else {
                wcall_setup()
                wcall_run()
            }
            wcall_set_log_handler(
                logHandler = LogHandlerImpl,
                arg = null
            )
            callingLogger.i("GlobalCallManager -> wcall_init")
        }
    }

    /**
     * Get a [CallManager] for a session, shouldn't be instantiated more than one CallManager for a single session.
     */
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
        getCallConversationType: GetCallConversationTypeProvider,
        networkStateObserver: NetworkStateObserver,
        kaliumConfigs: KaliumConfigs
    ): CallManager {
        return callManagerHolder.computeIfAbsent(userId) {
            CallManagerImpl(
                calling = calling,
                callRepository = callRepository,
                userRepository = userRepository,
                currentClientIdProvider = currentClientIdProvider,
                selfConversationIdProvider = selfConversationIdProvider,
                callMapper = callMapper,
                messageSender = messageSender,
                conversationRepository = conversationRepository,
                federatedIdMapper = federatedIdMapper,
                qualifiedIdMapper = qualifiedIdMapper,
                videoStateChecker = videoStateChecker,
                conversationClientsInCallUpdater = conversationClientsInCallUpdater,
                getCallConversationType = getCallConversationType,
                networkStateObserver = networkStateObserver,
                mediaManagerService = mediaManager,
                flowManagerService = flowManager,
                kaliumConfigs = kaliumConfigs
            )
        }
    }

    actual suspend fun removeInMemoryCallingManagerForUser(userId: UserId) {
        callManagerHolder[userId]?.cancelJobs()
        callManagerHolder.remove(userId)
    }

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val flowManager by lazy { FlowManagerServiceImpl(appContext, scope) }

    actual fun getFlowManager(): FlowManagerService = flowManager

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val mediaManager by lazy { MediaManagerServiceImpl(appContext, scope) }

    actual fun getMediaManager(): MediaManagerService = mediaManager
}

object LogHandlerImpl : LogHandler {
    override fun onLog(level: Int, message: String, arg: Pointer?) {
        when (level) {
            0 -> callingLogger.d(message)
            1 -> callingLogger.i(message)
            2 -> callingLogger.w(message)
            3 -> callingLogger.e(message)
        }
    }
}
