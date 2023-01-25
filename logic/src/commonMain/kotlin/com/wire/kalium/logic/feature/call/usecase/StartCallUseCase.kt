package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase.Result
import com.wire.kalium.logic.feature.conversation.JoinSubconversationUseCase
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
    private val syncManager: SyncManager,
    private val joinSubconversationUseCase: JoinSubconversationUseCase
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

        if (conversationType == ConversationType.ConferenceMls) {
            // TODO update AVS on current epoch after joining the sub conversation
            joinSubconversationUseCase(conversationId, "conference").fold(
                {
                    Result.SyncFailure
                },
                {
                    Result.Success
                }
            )
        } else {
            Result.Success
        }
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
