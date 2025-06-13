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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserSearchEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface SearchDAOArrangement {
        val searchDAO: SearchDAO

    suspend fun withGetKnownContacts(
        result: List<UserSearchEntity>
    )

    suspend fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = AnyMatcher(valueOf())
    )

    suspend fun withSearchList(
        result: List<UserSearchEntity>,
        query: Matcher<String> = AnyMatcher(valueOf())
    )

    suspend fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = AnyMatcher(valueOf()),
        query: Matcher<String> = AnyMatcher(valueOf())
    )

    suspend fun withSearchByHandle(
        result: List<UserSearchEntity>,
        handle: Matcher<String> = AnyMatcher(valueOf())
    )

    suspend fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity> = AnyMatcher(valueOf()),
        handle: Matcher<String> = AnyMatcher(valueOf())
    )
}

internal class SearchDAOArrangementImpl : SearchDAOArrangement {

        override val searchDAO: SearchDAO = mock(SearchDAO::class)

    override suspend fun withGetKnownContacts(
        result: List<UserSearchEntity>
    ) {
        coEvery {
            searchDAO.getKnownContacts()
        }.returns(result)
    }

    override suspend fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>
    ) {
        coEvery {
            searchDAO.getKnownContactsExcludingAConversation(matches { conversationId.matches(it) })
        }.returns(result)
    }

    override suspend fun withSearchList(result: List<UserSearchEntity>, query: Matcher<String>) {
        coEvery {
            searchDAO.searchList(matches { query.matches(it) })
        }.returns(result)
    }

    override suspend fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>,
        query: Matcher<String>
    ) {
        coEvery {
            searchDAO.searchListExcludingAConversation(
                matches { conversationId.matches(it) },
                matches { query.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withSearchByHandle(result: List<UserSearchEntity>, handle: Matcher<String>) {
        coEvery {
            searchDAO.handleSearch(
                matches { handle.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: Matcher<ConversationIDEntity>,
        handle: Matcher<String>
    ) {
        coEvery {
            searchDAO.handleSearchExcludingAConversation(
                matches { handle.matches(it) },
                matches { conversationId.matches(it) }
            )
        }.returns(result)
    }
}
