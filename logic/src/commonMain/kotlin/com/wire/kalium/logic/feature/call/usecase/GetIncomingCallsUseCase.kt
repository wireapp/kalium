package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.incomingCalls
import com.wire.kalium.logic.functional.flatMapFromIterable
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface GetIncomingCallsUseCase {
    suspend operator fun invoke(): Flow<List<IncomingCallData>>
}

internal class GetIncomingCallsUseCaseImpl(
    private val callManager: CallManager,
    private val syncManager: SyncManager,
    private val conversationRepository: ConversationRepository
) : GetIncomingCallsUseCase {

    //TODO update UnitTests after fixing all the other TODOs
    override suspend operator fun invoke(): Flow<List<IncomingCallData>> {
        syncManager.waitForSlowSyncToComplete()
        return callManager.incomingCalls
            .flatMapLatest {
                it.flatMapFromIterable { call ->
                    conversationRepository.getConversationDetailsById(call.conversationId)
                        .map { conversationsDetails ->
                            val callerName: String
                            val callerTeamName: String?
                            when (conversationsDetails) {
                                is ConversationDetails.OneOne -> {
                                    callerTeamName = "" //TODO get team name by the teamId
                                    callerName = conversationsDetails.otherUser.name ?: "Someone"
                                }
                                else -> {
                                    callerTeamName = "" //TODO get team name by the teamId
                                    callerName = "Someone" //TODO get caller name for the group conversations
                                }
                            }
                            IncomingCallData(call.conversationId, call.status, conversationsDetails, callerName, callerTeamName)
                        }
                }
            }
            .distinctUntilChanged()
    }
}

data class IncomingCallData(
    val conversationId: ConversationId,
    val status: CallStatus,
    val conversationDetails: ConversationDetails,
    val callerName: String,
    val callerTeamName: String?
)
