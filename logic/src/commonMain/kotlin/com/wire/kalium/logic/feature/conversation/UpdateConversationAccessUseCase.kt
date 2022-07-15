package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class UpdateConversationAccessUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        allowGuest: Boolean,
        allowServices: Boolean,
        allowNonTeamMember: Boolean
    ): Result {
        return conversationRepository.detailsById(conversationId)
            .map { conversation ->
                // TODO: handle edge case where accessRole is null
                val newAccessRoles: List<Conversation.AccessRole>? = conversation.accessRole?.toMutableSet()?.apply {
                    if (allowGuest) {
                        enableGuests()
                    } else {
                        disableGuests()
                    }
                    if (allowServices) {
                        enableServices()
                    } else {
                        disableServices()
                    }
                    if (allowNonTeamMember) {
                        enableNonTeamMember()
                    } else {
                        disableNonTeamMember()
                    }
                }?.toList()

                Triple(conversation.id, newAccessRoles, conversation.access)
            }.flatMap { (conversationId, newAccessRole, access) ->
                conversationRepository.updateAccessInfo(conversationId, access, newAccessRole)
            }.fold({
                Result.Failure(it)
            }, {
                Result.Success
            })
    }


    private fun MutableSet<Conversation.AccessRole>.enableGuests() {
        add(Conversation.AccessRole.GUEST)
    }

    private fun MutableSet<Conversation.AccessRole>.disableGuests() {
        remove(Conversation.AccessRole.GUEST)
    }

    private fun MutableSet<Conversation.AccessRole>.enableNonTeamMember() {
        add(Conversation.AccessRole.NON_TEAM_MEMBER)
    }

    private fun MutableSet<Conversation.AccessRole>.disableNonTeamMember() {
        remove(Conversation.AccessRole.NON_TEAM_MEMBER)
    }

    private fun MutableSet<Conversation.AccessRole>.enableServices() {
        add(Conversation.AccessRole.SERVICE)
    }

    private fun MutableSet<Conversation.AccessRole>.disableServices() {
        remove(Conversation.AccessRole.SERVICE)
    }


    sealed interface Result {
        object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }

}
