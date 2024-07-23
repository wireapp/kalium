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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase.Result
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Attempts to start a call.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [Result].
 */
class StartCallUseCase internal constructor(
    private val callManager: Lazy<CallManager>,
    private val syncManager: SyncManager,
    private val kaliumConfigs: KaliumConfigs,
    private val callRepository: CallRepository,
    private val getCallConversationType: GetCallConversationTypeProvider,
    private val answerCall: AnswerCallUseCase,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        callType: CallType = CallType.AUDIO,
    ) = withContext(dispatchers.default) {
        syncManager.waitUntilLiveOrFailure().fold({
            return@withContext Result.SyncFailure
        }, {
            callRepository.incomingCallsFlow().first().run {
                find {
                    it.conversationId == conversationId
                }?.apply {
                    answerCall(conversationId)
                    return@withContext Result.Success
                }
            }

            val callConversationType = getCallConversationType(conversationId)

            callManager.value.startCall(
                conversationId = conversationId,
                callType = callType,
                conversationTypeCalling = callConversationType,
                isAudioCbr = kaliumConfigs.forceConstantBitrateCalls
            )
            return@withContext Result.Success
        })
    }

    sealed interface Result {
        /**
         * Call started successfully
         */
        data object Success : Result

        /**
         * Failed to start a call as Sync is not yet performed
         */
        data object SyncFailure : Result
    }
}
