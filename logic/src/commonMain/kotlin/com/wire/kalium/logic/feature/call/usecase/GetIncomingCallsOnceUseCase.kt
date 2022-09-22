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

interface GetIncomingCallsOnceUseCase {
    suspend operator fun invoke(): List<Call>
}

/**
 *
 * @param callRepository CallRepository for getting all the incoming calls.
 * @param userRepository UserRepository for getting SelfUser data, to check the UserAvailabilityStatus
 * and do not show any incoming calls if User is UserAvailabilityStatus.AWAY.
 * @param conversationRepository ConversationRepository for getting ConversationsDetails, to check its MutedConversationStatus
 * and do not show incoming calls from the muted conversation.
 *
 * @return List<Call> - List of Calls that should be shown to the user.
 */
internal class GetIncomingCallsOnceUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : GetIncomingCallsOnceUseCase {

    private val logger
        get() = kaliumLogger.withFeatureId(CALLING)

    override suspend operator fun invoke(): List<Call> {
        val calls = getIncomingCallsIfUserStatusAllows()
            .onlyCallsInNotMutedConversations()

        val callIds = calls.map { call -> call.conversationId }.joinToString()
        logger.d("$TAG; Emitting result calls: $callIds")

        return calls
    }

    private suspend fun getIncomingCallsIfUserStatusAllows(): List<Call> {
        val selfUser = userRepository.getSelfUser()

        // if user is AWAY we don't show any IncomingCalls
        return if (selfUser?.availabilityStatus == UserAvailabilityStatus.AWAY) {
            logger.d("$TAG; Ignoring possible calls based user's status")
            listOf<Call>()
        } else {
            val calls = callRepository.getIncomingCalls()
            val callIds = calls.map { call -> call.conversationId }.joinToString()
            logger.d("$TAG; Found calls: $callIds")
            calls
        }
    }

    private suspend fun List<Call>.onlyCallsInNotMutedConversations(): List<Call> {
        logger.d("$TAG; Filtering not muted conversations")

        // Filter calls if ConversationDetails for it were not found
        val allowedConversations = this
            .mapNotNull { call ->
                // TODO place for improve: [conversationRepository.detailsById] is suspend
                // maybe make fetching each call details in parallel
                conversationRepository.detailsById(call.conversationId).nullableFold({ null }, { it })
            }
            .filter { conversationDetails ->
                // Don't display call if that Conversation is muted
                conversationDetails.mutedStatus != MutedConversationStatus.AllMuted
            }
            .map { it.id }

        return this.filter { allowedConversations.contains(it.conversationId) }
    }

    private fun List<ConversationId>.joinToString(): String = joinToString(prefix = "(", postfix = ")") {
        it.toString().obfuscateId()
    }

    private companion object {
        const val TAG = "IncomingCallsOnceUseCase"
    }
}
