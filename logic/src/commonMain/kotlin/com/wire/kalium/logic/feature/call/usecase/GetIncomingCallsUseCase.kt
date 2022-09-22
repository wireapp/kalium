package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CALLING
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

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

    private val logger
        get() = kaliumLogger.withFeatureId(CALLING)

    override suspend operator fun invoke(): Flow<List<Call>> {
        return observeIncomingCallsIfUserStatusAllows()
            .onlyCallsInNotMutedConversations()
            .distinctUntilChanged()
            .onEach { calls ->
                val callIds = calls.map { call -> call.conversationId }.joinToString()
                logger.d("$TAG; Emitting result calls: $callIds")
            }
    }

    private suspend fun observeIncomingCallsIfUserStatusAllows(): Flow<List<Call>> =
        userRepository.observeSelfUser()
            .flatMapLatest {

                // if user is AWAY we don't show any IncomingCalls
                if (it.availabilityStatus == UserAvailabilityStatus.AWAY) {
                    logger.d("$TAG; Ignoring possible calls based user's status")
                    flowOf(listOf())
                } else callRepository.incomingCallsFlow()
                    .onEach { calls ->
                        val callIds = calls.map { call -> call.conversationId }.joinToString()
                        logger.d("$TAG; Found calls: $callIds")
                    }
                    .distinctUntilChanged { old, new ->
                        old.firstOrNull()?.conversationId == new.firstOrNull()?.conversationId
                    }
            }

    private fun Flow<List<Call>>.onlyCallsInNotMutedConversations(): Flow<List<Call>> =
        map { calls ->
            logger.d("$TAG; Filtering not muted conversations")

            // Filter calls if ConversationDetails for it were not found
            val allowedConversations = calls.mapNotNull { call ->
                conversationRepository.detailsById(call.conversationId).nullableFold({ null }, { it })
            }.filter { conversationDetails ->
                // Don't display call if that Conversation is muted
                conversationDetails.mutedStatus != MutedConversationStatus.AllMuted
            }.map { it.id }

            calls.filter { allowedConversations.contains(it.conversationId) }
        }.onEach { calls ->
            val callIds = calls.map { call -> call.conversationId }.joinToString()
            logger.d("$TAG; Filtered calls: $callIds")
        }

    private fun List<ConversationId>.joinToString(): String = joinToString(prefix = "(", postfix = ")") {
        it.toString().obfuscateId()
    }

    private companion object {
        const val TAG = "IncomingCallsUseCase"
    }
}
