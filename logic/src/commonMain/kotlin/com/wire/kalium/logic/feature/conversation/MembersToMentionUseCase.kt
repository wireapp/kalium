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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * This usecase returns a list of members to mention for a given conversation based on a search done with searchQuery
 * The result should ordered respecting this order of priorities:
 * 1. Full name starts with the query
 * 2. Any of the tokens in the name starts with the query
 * 3. The handle starts with the query
 * 4. The full name contains the query
 * 5. The handle contains the query
 *
 * @param observeConversationMembers Returns members of a given conversation.
 * @param userRepository Retrieves the self user
 * @constructor Creates an instance of the usecase
 */
@Suppress("ReturnCount")
class MembersToMentionUseCase internal constructor(
    private val observeConversationMembers: ObserveConversationMembersUseCase,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * search for members to mention in a conversation
     * @param conversationId conversation to search in
     * @param searchQuery string used to search for members
     * @return a List of [MemberDetails] of a conversation for the given string
     */
    suspend operator fun invoke(conversationId: ConversationId, searchQuery: String): List<MemberDetails> = withContext(dispatcher.io) {
        val conversationMembers = observeConversationMembers(conversationId).first()

        // TODO apply normalization techniques that are used for other searches to the name (e.g. ö -> oe)

        val usersToSearch = conversationMembers.filter {
            it.user.id != selfUserId
        }
        if (searchQuery.isEmpty())
            return@withContext usersToSearch

        if (searchQuery.first().isWhitespace())
            return@withContext listOf()

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

        return@withContext usersToMention
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
