package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will update the access role configuration of a conversation.
 * So we can operate and allow or not operation based on these roles:
 * - [Conversation.isGuestAllowed]
 * - [Conversation.isServicesAllowed]
 * - [Conversation.isTeamGroup]
 * - [Conversation.isNonTeamMemberAllowed]
 *
 * @see Conversation.AccessRole
 */
class UpdateConversationAccessRoleUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        allowGuest: Boolean,
        allowServices: Boolean,
        allowNonTeamMember: Boolean
    ): Result = withContext(dispatcher.default) {
        conversationRepository.detailsById(conversationId)
            .map { conversation ->
                // TODO: handle edge case where accessRole is null
                val newAccessRoles: List<Conversation.AccessRole> = conversation.accessRole.toMutableSet().apply {
                    handleGuestsState(allowGuest)
                    handleServicesState(allowServices)
                    handleNonTeamMemberState(allowNonTeamMember)
                }.toList()

                Triple(conversation.id, newAccessRoles, conversation.access)
            }.flatMap { (conversationId, newAccessRole, access) ->
                conversationRepository.updateAccessInfo(conversationId, access, newAccessRole)
            }.fold({
                Result.Failure(it)
            }, {
                Result.Success
            })
    }

    private fun MutableSet<Conversation.AccessRole>.handleServicesState(allowServices: Boolean) {
        if (allowServices) {
            add(Conversation.AccessRole.SERVICE)
        } else {
            remove(Conversation.AccessRole.SERVICE)
        }
    }

    private fun MutableSet<Conversation.AccessRole>.handleGuestsState(allowGuest: Boolean) {
        if (allowGuest) {
            add(Conversation.AccessRole.GUEST)
        } else {
            remove(Conversation.AccessRole.GUEST)
        }
    }

    private fun MutableSet<Conversation.AccessRole>.handleNonTeamMemberState(allowNonTeamMember: Boolean) {
        if (allowNonTeamMember) {
            add(Conversation.AccessRole.NON_TEAM_MEMBER)
        } else {
            remove(Conversation.AccessRole.NON_TEAM_MEMBER)
        }
    }

    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}
