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
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.USER_PROFILE_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntity
import io.mockative.Mock
import io.mockative.Times
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchUserRepositoryTest {

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        val (_, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
            .arrange()

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
            .arrange()

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
            .withSearchResult(Either.Left(TestNetworkResponseError.noNetworkConnection()))
            .arrange()

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)
        // then
        verify(arrangement.userDetailsApi)
            .suspendFunction(arrangement.userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.userMapper)
            .function(arrangement.userMapper::fromUserProfileDtoToOtherUser)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        val (_, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
            .withGetMultipleUsersResult(TestNetworkResponseError.genericResponseError())
            .arrange()

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_ThenPublicUserMapperIsNotInvoked() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
            .withGetMultipleUsersResult(TestNetworkResponseError.genericResponseError())
            .arrange()

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        verify(arrangement.userMapper)
            .function(arrangement.userMapper::fromUserProfileDtoToOtherUser)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            // given
            val (arrangement, searchUserRepository) = Arrangement()
                .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                .withGetMultipleUsersResult(TestNetworkResponseError.genericResponseError())
                .arrange()

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
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
            .withGetMultipleUsersResult(NetworkResponse.Success(USER_RESPONSE, mapOf(), 200))
            .withFromUserProfileDtoToOtherUserResult(TestUser.OTHER)
            .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
            .withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
            .withFromUserDetailsEntityToSelfUser(TestUser.SELF.copy(teamId = null))
            .arrange()

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
    }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsEqualToExpectedValue() =
        runTest {
            // given
            val (_, searchUserRepository) = Arrangement()
                .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                .withGetMultipleUsersResult(NetworkResponse.Success(USER_RESPONSE, mapOf(), 200))
                .withFromUserProfileDtoToOtherUserResult(TestUser.OTHER)
                .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
                .withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
                .withFromUserDetailsEntityToSelfUser(TestUser.SELF.copy(teamId = null))
                .arrange()

            val expectedResult = UserSearchResult(
                result = listOf(TestUser.OTHER)
            )
            // when
            val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<UserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    @Test
    fun givenAValidUserSearchWithEmptyResults_WhenSearchingSomeText_ThenResultIsAnEmptyList() =
        runTest {
            // given
            val (_, searchUserRepository) = Arrangement()
                .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
                .withGetMultipleUsersResult(NetworkResponse.Success(USER_RESPONSE.copy(usersFound = emptyList()), mapOf(), 200))
                .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
                .withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
                .withFromUserDetailsEntityToSelfUser(TestUser.SELF)
                .arrange()

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
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE_EMPTY))
            .arrange()

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
    fun givenContactSearchApiReturnsTeamMembers_whenSearchPublicContact_thenStoreThemLocallyAndExcludeFromResult() = runTest {
        // given
        val userListResponse = ListUsersDTO(
            usersFailed = emptyList(),
            usersFound = listOf(
                USER_PROFILE_DTO.copy(id = UserId("teamUser", TestUser.SELF.id.domain), teamId = TestUser.SELF.teamId?.value),
            )
        )
        val (arrangement, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
            .withGetMultipleUsersResult(NetworkResponse.Success(userListResponse, mapOf(), 200))
            .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
            .withObserveUserDetailsByQualifiedIdResult(flowOf(TestUser.DETAILS_ENTITY))
            .withFromUserDetailsEntityToSelfUser(TestUser.SELF)
            .withUpsertUsersSuccess()
            .withFromUserProfileDtoToOtherUserResult(TestUser.OTHER)
            .withFromUserProfileDtoToUserEntityResult(TestUser.ENTITY)
            .arrange()

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertTrue { actual.value.result.isEmpty() }

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertUsers)
            .with(eq(listOf(TestUser.ENTITY)))
            .wasInvoked()
    }

    internal class Arrangement {

        @Mock
        internal val metadataDAO: MetadataDAO = mock(classOf<MetadataDAO>())

        @Mock
        internal val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        internal val userSearchApiWrapper: UserSearchApiWrapper = mock(classOf<UserSearchApiWrapper>())

        @Mock
        internal val userMapper: UserMapper = mock(classOf<UserMapper>())

        @Mock
        internal val userDAO: UserDAO = mock(classOf<UserDAO>())

        private val searchUserRepository: SearchUserRepository by lazy {
            SearchUserRepositoryImpl(
                userDAO,
                metadataDAO,
                userDetailsApi,
                userSearchApiWrapper,
                userMapper,
            )
        }

        fun arrange() = this to searchUserRepository

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

        fun withFromUserProfileDtoToOtherUserResult(result: OtherUser) = apply {
            given(userMapper)
                .function(userMapper::fromUserProfileDtoToOtherUser)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withValueByKeyFlowResult(result: Flow<String?>) = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withObserveUserDetailsByQualifiedIdResult(result: Flow<UserDetailsEntity?>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::observeUserDetailsByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFromUserDetailsEntityToSelfUser(result: SelfUser) = apply {
            given(userMapper)
                .function(userMapper::fromUserDetailsEntityToSelfUser)
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

        fun withFromUserProfileDtoToUserEntityResult(result: UserEntity) = apply {
            given(userMapper)
                .function(userMapper::fromUserProfileDtoToUserEntity)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withUpsertUsersSuccess() = apply {
            given(userDAO)
                .suspendFunction(userDAO::upsertUsers)
                .whenInvokedWith(anything())
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
                        id = "id$i",
                        name = "name$i",
                        qualifiedID = com.wire.kalium.network.api.base.model.UserId(
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

        const val JSON_QUALIFIED_ID = """{"value":"test" , "domain":"test" }"""
    }

}
