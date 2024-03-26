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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.SearchUserRepository
import com.wire.kalium.logic.data.publicuser.SearchUsersOptions
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock

internal interface SearchRepositoryArrangement {
    @Mock
    val searchUserRepository: SearchUserRepository

    fun withSearchUserRemoteDirectory(
        result: Either<CoreFailure, UserSearchResult>,
        searchQuery: Matcher<String> = any(),
        domain: Matcher<String> = any(),
        maxResultSize: Matcher<Int?> = any(),
        searchUsersOptions: Matcher<SearchUsersOptions> = any()
    )

    fun withGetKnownContacts(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        excludeConversation: Matcher<ConversationId?> = any()
    )

    fun withSearchLocalByName(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String> = any(),
        excludeConversation: Matcher<ConversationId?> = anything()
    )

    fun withSearchByHandle(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String> = any(),
        excludeConversation: Matcher<ConversationId?> = anything()
    )
}

internal class SearchRepositoryArrangementImpl : SearchRepositoryArrangement {
    @Mock
    override val searchUserRepository: SearchUserRepository = mock(SearchUserRepository::class)

    override fun withSearchUserRemoteDirectory(
        result: Either<CoreFailure, UserSearchResult>,
        searchQuery: Matcher<String>,
        domain: Matcher<String>,
        maxResultSize: Matcher<Int?>,
        searchUsersOptions: Matcher<SearchUsersOptions>
    ) {
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchUserRemoteDirectory)
            .whenInvokedWith(searchQuery, domain, maxResultSize, searchUsersOptions)
            .thenReturn(result)
    }

    override fun withGetKnownContacts(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        excludeConversation: Matcher<ConversationId?>
    ) {
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::getKnownContacts)
            .whenInvokedWith(excludeConversation)
            .thenReturn(result)
    }

    override fun withSearchLocalByName(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String>,
        excludeConversation: Matcher<ConversationId?>
    ) {
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchLocalByName)
            .whenInvokedWith(searchQuery, excludeConversation)
            .thenReturn(result)
    }

    override fun withSearchByHandle(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String>,
        excludeConversation: Matcher<ConversationId?>
    ) {
        given(searchUserRepository)
            .suspendFunction(searchUserRepository::searchLocalByHandle)
            .whenInvokedWith(searchQuery, excludeConversation)
            .thenReturn(result)
    }
}
