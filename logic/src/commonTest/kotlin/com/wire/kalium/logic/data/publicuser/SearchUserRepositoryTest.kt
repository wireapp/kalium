/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.USER_PROFILE_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.SearchDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import io.mockative.KFunction1
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

class SearchUserRepositoryTest {

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        val (_, searchUserRepository) = Arrangement()
            .arrange {
                withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
                withTeamId(Either.Right(TestUser.SELF.teamId))
            }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
            }

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        verify(arrangement.userSearchApiWrapper)
            .suspendFunction(arrangement.userSearchApiWrapper::search)
            .with(anything(), anything(), anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenUserDetailsApiAndPublicUserMapperIsNotInvoked() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
                withTeamId(Either.Right(TestUser.SELF.teamId))
            }

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        val (_, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                withGetMultipleUsersResult(TestNetworkResponseError.genericResponseError())
            }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            // given
            val (arrangement, searchUserRepository) = Arrangement()
                .arrange {
                    withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                    withGetMultipleUsersResult(TestNetworkResponseError.genericResponseError())
                    withTeamId(Either.Right(TestUser.SELF.teamId))
                }

            // when
            searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            // then
            verify(arrangement.userSearchApiWrapper)
                .suspendFunction(arrangement.userSearchApiWrapper::search)
                .with(anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)

            verify(arrangement.userDetailsApi)
                .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsSuccess() = runTest {
        // given
        val (_, searchUserRepository) = Arrangement()
            .arrange {
                withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                withGetMultipleUsersResult(NetworkResponse.Success(USER_RESPONSE, mapOf(), 200))
                withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
                withTeamId(Either.Right(TestUser.SELF.teamId))
            }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
    }

    @Test
    fun givenAValidUserSearchWithEmptyResults_WhenSearchingSomeText_ThenResultIsAnEmptyList() =
        runTest {
            // given
            val (_, searchUserRepository) = Arrangement()
                .arrange {
                    withTeamId(Either.Right(TestUser.SELF.teamId))
                    withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                    withGetMultipleUsersResult(NetworkResponse.Success(USER_RESPONSE.copy(usersFound = emptyList()), mapOf(), 200))
                    withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
                }

            val expectedResult = UserSearchResult(
                result = emptyList()
            )
            // when
            val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<UserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    @Test
    fun givenASearchWithConversationExcludedOption_WhenSearchingUsersByNameOrHandleOrEmail_ThenSearchForUsersNotInTheConversation() =
        runTest {
            // given
            val (arrangement, searchUserRepository) = Arrangement()
                .withGetUsersDetailsNotInConversationByNameOrHandleOrEmailResult(flowOf(listOf()))
                .withGetUserDetailsByNameOrHandleOrEmailAndConnectionStatesResult(flowOf(listOf()))
                .arrange()

            // when
            searchUserRepository.searchKnownUsersByNameOrHandleOrEmail(
                searchQuery = "someQuery",
                searchUsersOptions = SearchUsersOptions(
                    conversationExcluded = ConversationMemberExcludedOptions.ConversationExcluded(
                        ConversationId("someValue", "someDomain"),
                    ),
                    selfUserIncluded = true
                )
            )

            // then
            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUserDetailsByNameOrHandleOrEmailAndConnectionStates)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUsersDetailsNotInConversationByNameOrHandleOrEmail)
                .with(anything(), anything())
                .wasInvoked(Times(1))
        }

    @Test
    fun givenASearchWithConversationExcludedOption_WhenSearchingUsersByHandle_ThenSearchForUsersNotInTheConversation() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .withGetUserDetailsByHandleAndConnectionStatesResult(flowOf(listOf()))
            .withGetUsersDetailsNotInConversationByHandleResult(flowOf(listOf()))
            .arrange()

        // when
        searchUserRepository.searchKnownUsersByHandle(
            handle = "someQuery",
            searchUsersOptions = SearchUsersOptions(
                conversationExcluded = ConversationMemberExcludedOptions.ConversationExcluded(
                    ConversationId("someValue", "someDomain")
                ),
                selfUserIncluded = true
            )
        )

        // then
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUserDetailsByHandleAndConnectionStates)
            .with(anything(), anything())
            .wasNotInvoked()

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUsersDetailsNotInConversationByHandle)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiSuccessButListIsEmpty_whenSearchPublicContact_thenReturnEmptyListWithoutCallingUserDetailsApi() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE_EMPTY))
            }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertTrue { actual.value.result.isEmpty() }

        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiReturnsTeamMembers_whenSearchPublicContact_thenStoreThemLocally() = runTest {
        // given
        val selfUser = TestUser.SELF

        val userListResponse = ListUsersDTO(
            usersFailed = emptyList(),
            usersFound = listOf(
                USER_PROFILE_DTO.copy(id = UserIdDTO("teamUser", selfUser.id.domain), teamId = selfUser.teamId?.value),
            )
        )
        val userSearchResponse = userListResponse.usersFound.map {
            ContactDTO(
                accentId = 1,
                handle = it.handle,
                name = it.name,
                qualifiedID = it.id,
                team = it.teamId
            )
        }.let {
            UserSearchResponse(
                documents = it,
                found = 1,
                returned = 1,
                searchPolicy = SearchPolicyDTO.FULL_SEARCH,
                took = 100,
            )
        }

        val userMapper = MapperProvider.userMapper()

        val expected: UserSearchResult = userListResponse.usersFound.map { searchEntity ->
            userMapper.fromUserProfileDtoToOtherUser(searchEntity, selfUser.id, selfUser.teamId)
        }.let {
            UserSearchResult(it)
        }

        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(selfUser.teamId))
                withSearchResult(Either.Right(userSearchResponse))
                withGetMultipleUsersResult(NetworkResponse.Success(userListResponse, mapOf(), 200))
                withUpsertUsersSuccess()
            }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, selfUser.id.domain)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertEquals(expected, actual.value)

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::updateUser, KFunction1<List<PartialUserEntity>>())
            .with(any())
            .wasInvoked()
    }

    internal class Arrangement : SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl() {

        @Mock
        internal val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        internal val userSearchApiWrapper: UserSearchApiWrapper = mock(classOf<UserSearchApiWrapper>())

        @Mock
        internal val searchDAO: SearchDAO = mock(classOf<SearchDAO>())

        @Mock
        internal val userDAO: UserDAO = mock(classOf<UserDAO>())

        private val searchUserRepository: SearchUserRepository by lazy {
            SearchUserRepositoryImpl(
                userDAO,
                searchDAO,
                userDetailsApi,
                userSearchApiWrapper,
                selfUserid = TestUser.SELF.id,
                selfTeamIdProvider
            )
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).run {
            this to searchUserRepository
        }

        fun withSearchResult(result: Either<NetworkFailure, UserSearchResponse>) = apply {
            given(userSearchApiWrapper)
                .suspendFunction(userSearchApiWrapper::search)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withGetMultipleUsersResult(result: NetworkResponse<ListUsersDTO>) = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withObserveUserDetailsByQualifiedIdResult(result: Flow<UserDetailsEntity?>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withGetUsersDetailsNotInConversationByNameOrHandleOrEmailResult(result: Flow<List<UserDetailsEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersDetailsNotInConversationByNameOrHandleOrEmail)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUserDetailsByNameOrHandleOrEmailAndConnectionStatesResult(result: Flow<List<UserDetailsEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserDetailsByNameOrHandleOrEmailAndConnectionStates)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUserDetailsByHandleAndConnectionStatesResult(result: Flow<List<UserDetailsEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserDetailsByHandleAndConnectionStates)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUsersDetailsNotInConversationByHandleResult(result: Flow<List<UserDetailsEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersDetailsNotInConversationByHandle)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withUpsertUsersSuccess() = apply {
            given(userDAO)
                .suspendFunction(userDAO::upsertUsers)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }
    }

    private companion object {
        const val TEST_QUERY = "testQuery"
        const val TEST_DOMAIN = "testDomain"
        val CONTACTS = buildList {
            for (i in 1..5) {
                add(
                    ContactDTO(
                        accentId = i,
                        handle = "handle$i",
                        name = "name$i",
                        qualifiedID = UserIdDTO(
                            value = "value$i",
                            domain = "domain$i"
                        ),
                        team = "team$i"
                    )
                )
            }
        }

        val CONTACT_SEARCH_RESPONSE = UserSearchResponse(
            documents = CONTACTS,
            found = CONTACTS.size,
            returned = 5,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 100,
        )

        val CONTACT_SEARCH_RESPONSE_EMPTY = UserSearchResponse(
            documents = emptyList(),
            found = 0,
            returned = 0,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 0,
        )

        val USER_RESPONSE = ListUsersDTO(usersFailed = emptyList(), usersFound = listOf(USER_PROFILE_DTO))
    }

}
