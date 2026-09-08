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
        result: List<UserSearchEntity>,
        excludeConversationId: (ConversationIDEntity?) -> Boolean = { true },
        onlyTeamId: (String?) -> Boolean = { true },
        onlyDomain: (String?) -> Boolean = { true }
    )

    suspend fun withSearchByName(
        result: List<UserSearchEntity>,
        query: (String) -> Boolean = { true },
        excludeConversationId: (ConversationIDEntity?) -> Boolean = { true },
        onlyTeamId: (String?) -> Boolean = { true },
        onlyDomain: (String?) -> Boolean = { true }
    )

    suspend fun withSearchByHandle(
        result: List<UserSearchEntity>,
        handle: (String) -> Boolean = { true },
        excludeConversationId: (ConversationIDEntity?) -> Boolean = { true },
        onlyTeamId: (String?) -> Boolean = { true },
        onlyDomain: (String?) -> Boolean = { true }
    )
}

internal class SearchDAOArrangementImpl : SearchDAOArrangement {

    override val searchDAO: SearchDAO = mock(mode = MockMode.autoUnit)
    override val appDAO: AppDAO = mock(mode = MockMode.autoUnit)

    override suspend fun withGetKnownContacts(
        result: List<UserSearchEntity>,
        excludeConversationId: (ConversationIDEntity?) -> Boolean,
        onlyTeamId: (String?) -> Boolean,
        onlyDomain: (String?) -> Boolean
    ) {
        everySuspend {
            searchDAO.getKnownContacts(
                excludeConversationId = matches { excludeConversationId(it) },
                onlyTeamId = matches { onlyTeamId(it) },
                onlyDomain = matches { onlyDomain(it) }
            )
        } returns result
    }

    override suspend fun withSearchByName(
        result: List<UserSearchEntity>,
        query: (String) -> Boolean,
        excludeConversationId: (ConversationIDEntity?) -> Boolean,
        onlyTeamId: (String?) -> Boolean,
        onlyDomain: (String?) -> Boolean
    ) {
        everySuspend {
            searchDAO.searchByName(
                searchQuery = matches { query(it) },
                excludeConversationId = matches { excludeConversationId(it) },
                onlyTeamId = matches { onlyTeamId(it) },
                onlyDomain = matches { onlyDomain(it) }
            )
        } returns result
    }

    override suspend fun withSearchByHandle(
        result: List<UserSearchEntity>,
        handle: (String) -> Boolean,
        excludeConversationId: (ConversationIDEntity?) -> Boolean,
        onlyTeamId: (String?) -> Boolean,
        onlyDomain: (String?) -> Boolean
    ) {
        everySuspend {
            searchDAO.searchByHandle(
                searchQuery = matches { handle(it) },
                excludeConversationId = matches { excludeConversationId(it) },
                onlyTeamId = matches { onlyTeamId(it) },
                onlyDomain = matches { onlyDomain(it) }
            )
        } returns result
    }
}
