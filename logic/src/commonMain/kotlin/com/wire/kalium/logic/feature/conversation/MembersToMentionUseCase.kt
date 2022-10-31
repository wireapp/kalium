package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.flow.first

class MembersToMentionUseCase internal constructor(
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val userRepository: UserRepository
) {
    suspend operator fun invoke(conversationId: ConversationId, searchQuery: String): List<MemberDetails> {
        val conversationMembers = observeConversationMembers(conversationId).first()

        // TODO apply normalization techniques that are used for other searches to the name (e.g. ö -> oe)

        val usersToSearch = conversationMembers.filter {
            it.user.id != userRepository.getSelfUser()?.id
        }
        if (searchQuery.isEmpty())
            return usersToSearch

        if (searchQuery.first().isWhitespace())
            return listOf()

        val rules: List<(MemberDetails) -> Boolean> = listOf(
            { it.user.name?.startsWith(searchQuery, true) == true },
            {
                it.user.name?.let { name ->
                    nameTokens(name).firstOrNull { nameToken -> nameToken.startsWith(searchQuery, true) }
                } != null
            },
            { it.user.handle?.startsWith(searchQuery, true) == true },
            { it.user.name?.contains(searchQuery, true) == true },
            { it.user.handle?.contains(searchQuery, true) == true }
        )

        var foundUsers: Set<MemberDetails> = emptySet()
        val usersToMention = mutableListOf<MemberDetails>()
        rules.forEach { rule ->
            val matches = usersToSearch.filter { rule(it) }
                .filter { !foundUsers.contains(it) }
                .sortedBy { it.user.name }
            foundUsers = foundUsers.union(matches)
            usersToMention += matches
        }

        return usersToMention
    }
}

/**
 * Split the name in tokens.
 * Any character that is not a alphanumeric character is a separator (e.g. (space), -, !, 🤣 and so on)
 */
val nameTokens: (String) -> List<String> = {
    it.split(NON_ALPHANUMERIC_REGEX).filter { s -> s.isNotEmpty() }
}

val NON_ALPHANUMERIC_REGEX = Regex("[^\\w]")
