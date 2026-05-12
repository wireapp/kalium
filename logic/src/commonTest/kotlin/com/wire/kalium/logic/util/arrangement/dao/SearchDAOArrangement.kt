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

import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserSearchEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.mock

internal interface SearchDAOArrangement {
    val searchDAO: SearchDAO
    val appDAO: AppDAO

    suspend fun withGetKnownContacts(
        result: List<UserSearchEntity>
    )

    suspend fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean = { true }
    )

    suspend fun withSearchList(
        result: List<UserSearchEntity>,
        query: (String) -> Boolean = { true }
    )

    suspend fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean = { true },
        query: (String) -> Boolean = { true }
    )

    suspend fun withSearchByHandle(
        result: List<UserSearchEntity>,
        handle: (String) -> Boolean = { true }
    )

    suspend fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean = { true },
        handle: (String) -> Boolean = { true }
    )
}

internal class SearchDAOArrangementImpl : SearchDAOArrangement {

    override val searchDAO: SearchDAO = mock(mode = MockMode.autoUnit)
    override val appDAO: AppDAO = mock(mode = MockMode.autoUnit)

    override suspend fun withGetKnownContacts(
        result: List<UserSearchEntity>
    ) {
        everySuspend {
            searchDAO.getKnownContacts()
        } returns result
    }

    override suspend fun withGetKnownContactsExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean
    ) {
        everySuspend {
            searchDAO.getKnownContactsExcludingAConversation(matches { conversationId(it) })
        } returns result
    }

    override suspend fun withSearchList(result: List<UserSearchEntity>, query: (String) -> Boolean) {
        everySuspend {
            searchDAO.searchList(matches { query(it) })
        } returns result
    }

    override suspend fun withSearchListExcludingAConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean,
        query: (String) -> Boolean
    ) {
        everySuspend {
            searchDAO.searchListExcludingAConversation(
                matches { conversationId(it) },
                matches { query(it) }
            )
        } returns result
    }

    override suspend fun withSearchByHandle(result: List<UserSearchEntity>, handle: (String) -> Boolean) {
        everySuspend {
            searchDAO.handleSearch(
                matches { handle(it) }
            )
        } returns result
    }

    override suspend fun withSearchByHandleExcludingConversation(
        result: List<UserSearchEntity>,
        conversationId: (ConversationIDEntity) -> Boolean,
        handle: (String) -> Boolean
    ) {
        everySuspend {
            searchDAO.handleSearchExcludingAConversation(
                matches { handle(it) },
                matches { conversationId(it) }
            )
        } returns result
    }
}
