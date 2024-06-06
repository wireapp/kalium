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
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock

internal interface SearchRepositoryArrangement {
    @Mock
    val searchUserRepository: SearchUserRepository

    suspend fun withSearchUserRemoteDirectory(
        result: Either<CoreFailure, UserSearchResult>,
        searchQuery: Matcher<String> = AnyMatcher(valueOf()),
        domain: Matcher<String> = AnyMatcher(valueOf()),
        maxResultSize: Matcher<Int?> = AnyMatcher(valueOf()),
        searchUsersOptions: Matcher<SearchUsersOptions> = AnyMatcher(valueOf())
    )

    suspend fun withGetKnownContacts(
        result: Either<StorageFailure, List<UserSearchDetails>>,
    )

    suspend fun withSearchLocalByName(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String> = AnyMatcher(valueOf()),
    )

    suspend fun withSearchByHandle(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String> = AnyMatcher(valueOf()),
    )
}

internal class SearchRepositoryArrangementImpl : SearchRepositoryArrangement {
    @Mock
    override val searchUserRepository: SearchUserRepository = mock(SearchUserRepository::class)

    override suspend fun withSearchUserRemoteDirectory(
        result: Either<CoreFailure, UserSearchResult>,
        searchQuery: Matcher<String>,
        domain: Matcher<String>,
        maxResultSize: Matcher<Int?>,
        searchUsersOptions: Matcher<SearchUsersOptions>
    ) {
        coEvery {
            searchUserRepository.searchUserRemoteDirectory(
                matches { searchQuery.matches(it) },
                matches { domain.matches(it) },
                matches { maxResultSize.matches(it) },
                matches { searchUsersOptions.matches(it) }
            )
        }.returns(result)
    }

    override suspend fun withGetKnownContacts(
        result: Either<StorageFailure, List<UserSearchDetails>>,
    ) {
        coEvery {
            searchUserRepository.getKnownContacts(any())
        }.returns(result)
    }

    override suspend fun withSearchLocalByName(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String>,
    ) {
        coEvery {
            searchUserRepository.searchLocalByName(
                matches { searchQuery.matches(it) },
                any()
            )
        }.returns(result)
    }

    override suspend fun withSearchByHandle(
        result: Either<StorageFailure, List<UserSearchDetails>>,
        searchQuery: Matcher<String>,
    ) {
        coEvery {
            searchUserRepository.searchLocalByHandle(
                matches { searchQuery.matches(it) },
                any()
            )
        }.returns(result)
    }
}
