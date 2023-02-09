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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase.Result
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import kotlin.Boolean
import kotlin.Lazy

/**
 * Attempts to start a call.
 * Will wait for sync to finish or fail if it is pending,
 * and return one [Result].
 */
class StartCallUseCase internal constructor(
    private val callManager: Lazy<CallManager>,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        callType: CallType = CallType.AUDIO,
        conversationType: ConversationType,
        isAudioCbr: Boolean = false
    ) = syncManager.waitUntilLiveOrFailure().fold({
        Result.SyncFailure
    }, {
        callManager.value.startCall(
            conversationId = conversationId,
            callType = callType,
            conversationType = conversationType,
            isAudioCbr = isAudioCbr
        )
        Result.Success
    })

    sealed interface Result {
        /**
         * Call started successfully
         */
        object Success : Result

        /**
         * Failed to start a call as Sync is not yet performed
         */
        object SyncFailure : Result
    }
}
