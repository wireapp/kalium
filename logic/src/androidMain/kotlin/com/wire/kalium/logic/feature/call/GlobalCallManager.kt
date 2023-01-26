/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.call

import android.content.Context
import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import io.ktor.util.collections.ConcurrentMap
import kotlinx.coroutines.Dispatchers

actual class GlobalCallManager(
    appContext: Context
) {

    private val callManagerHolder: ConcurrentMap<QualifiedID, CallManager> by lazy {
        ConcurrentMap()
    }

    private val calling by lazy {
        Calling.INSTANCE.apply {
            wcall_init(env = ENVIRONMENT_DEFAULT)
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
        conversationRepository: ConversationRepository,
        messageSender: MessageSender,
        callMapper: CallMapper,
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper: QualifiedIdMapper,
        videoStateChecker: VideoStateChecker
    ): CallManager {
        return callManagerHolder[userId] ?: CallManagerImpl(
            calling = calling,
            callRepository = callRepository,
            userRepository = userRepository,
            currentClientIdProvider = currentClientIdProvider,
            callMapper = callMapper,
            messageSender = messageSender,
            conversationRepository = conversationRepository,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker
        ).also {
            callManagerHolder[userId] = it
        }
    }

    actual fun removeInMemoryCallingManagerForUser(userId: UserId) {
        callManagerHolder.remove(userId)
    }

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val flowManager = FlowManagerServiceImpl(appContext, Dispatchers.Default)

    actual fun getFlowManager(): FlowManagerService = flowManager

    // Initialize it eagerly, so it's already initialized when `calling` is initialized
    private val mediaManager = MediaManagerServiceImpl(appContext)
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
