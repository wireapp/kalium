package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.functional.flatMapFromIterable
import com.wire.kalium.logic.functional.getOrElse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface GetIncomingCallsUseCase {
    suspend operator fun invoke(): Flow<List<Call>>
}

/**
 *
 * @param callRepository CallRepository for getting all the incoming calls.
 * @param userRepository UserRepository for getting SelfUser data, to check the UserAvailabilityStatus
 * and do not show any incoming calls if User is UserAvailabilityStatus.AWAY.
 * @param conversationRepository ConversationRepository for getting ConversationsDetails, to check its MutedConversationStatus
 * and do not show incoming calls from the muted conversation.
 *
 * @return Flow<List<Call>> - Flow of Calls List that should be shown to the user.
 * That Flow emits everytime when the list is changed
 */
internal class GetIncomingCallsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : GetIncomingCallsUseCase {

    override suspend operator fun invoke(): Flow<List<Call>> {
        return observeIncomingCallsIfUserStatusAllows()
            .onlyCallsInNotMutedConversations()
            .distinctUntilChanged()
    }

    private suspend fun observeIncomingCallsIfUserStatusAllows(): Flow<List<Call>> =
        userRepository.observeSelfUser()
            .flatMapLatest {
                // if user is AWAY we don't show any IncomingCalls
                if (it.availabilityStatus == UserAvailabilityStatus.AWAY) flowOf(listOf())
                else callRepository.incomingCallsFlow().distinctUntilChanged { old, new ->
                    old.firstOrNull()?.conversationId == new.firstOrNull()?.conversationId
                }
            }

    private fun Flow<List<Call>>.onlyCallsInNotMutedConversations(): Flow<List<Call>> =
        flatMapLatest { calls ->
            calls
                .flatMapFromIterable { call ->
                    // getting ConversationDetails for each Call
                    conversationRepository.observeById(call.conversationId)
                        .map { it.getOrElse(null) }
                }
                .map { conversations ->
                    val allowedConversations = conversations
                        .filter { conversationDetails ->
                            // don't display call if ConversationDetails for it were not found
                            conversationDetails != null &&
                                    // don't display call if that Conversation is muted
                                    conversationDetails.mutedStatus != MutedConversationStatus.AllMuted
                        }
                        .map { it!!.id }

                    calls.filter { allowedConversations.contains(it.conversationId) }
                }
        }
}
