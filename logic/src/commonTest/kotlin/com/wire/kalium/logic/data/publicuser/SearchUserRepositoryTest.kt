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

package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.publicuser.model.UserSearchDetails
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.USER_PROFILE_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.arrangement.dao.SearchDAOArrangement
import com.wire.kalium.logic.util.arrangement.dao.SearchDAOArrangementImpl
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserSearchEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
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
        val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

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
        searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

        // then
        coVerify {
            arrangement.userSearchApiWrapper.search(any(), any(), any(), any())
        }.wasInvoked(exactly = once)
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
        searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasNotInvoked()
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
        val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

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
            searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

            // then
            coVerify {
                arrangement.userSearchApiWrapper.search(any(), any(), any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.userDetailsApi.getMultipleUsers(any())
            }.wasInvoked(exactly = once)
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
        val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

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
            val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

            assertIs<Either.Right<UserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
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
        val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, TEST_DOMAIN, null, SearchUsersOptions.Default)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertTrue { actual.value.result.isEmpty() }

        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasNotInvoked()
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
        val actual = searchUserRepository.searchUserRemoteDirectory(TEST_QUERY, selfUser.id.domain, null, SearchUsersOptions.Default)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertEquals(expected, actual.value)

        coVerify {
            arrangement.userDAO.updateUser(any<List<PartialUserEntity>>())
        }.wasInvoked()
    }

    @Test
    fun givenNotExcludedConversation_whenCallingGetKnownContacts_thenTheCorrectDAOFunctionIsCalled() = runTest {
        // given
        val searchResult = listOf(
            UserSearchEntity(
                id = UserIDEntity("id", "domain"),
                name = "name",
                completeAssetId = null,
                previewAssetId = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                type = UserTypeEntity.STANDARD,
                handle = "handle"
            )
        )

        val expected = searchResult.map {
            UserSearchDetails(
                id = it.id.toModel(),
                name = it.name,
                completeAssetId = it.completeAssetId?.toModel(),
                previewAssetId = it.previewAssetId?.toModel(),
                type = UserType.INTERNAL,
                connectionStatus = ConnectionState.ACCEPTED,
                handle = "handle"
            )
        }
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withGetKnownContacts(searchResult)
            }

        // when
        searchUserRepository.getKnownContacts(null).shouldSucceed {
            assertEquals(expected, it)
        }

        // then
        coVerify {
            arrangement.searchDAO.getKnownContacts()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenExcludedConversation_whenCallingGetKnownContacts_thenTheCorrectDAOFunctionIsCalled() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "domain")
        val searchResult = listOf(
            UserSearchEntity(
                id = UserIDEntity("id", "domain"),
                name = "name",
                completeAssetId = null,
                previewAssetId = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                type = UserTypeEntity.STANDARD,
                handle = "handle"
            )
        )

        val expected = searchResult.map {
            UserSearchDetails(
                id = it.id.toModel(),
                name = it.name,
                completeAssetId = it.completeAssetId?.toModel(),
                previewAssetId = it.previewAssetId?.toModel(),
                type = UserType.INTERNAL,
                connectionStatus = ConnectionState.ACCEPTED,
                handle = "handle"
            )
        }

        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withGetKnownContactsExcludingAConversation(searchResult)
            }

        // when
        searchUserRepository.getKnownContacts(conversationId).shouldSucceed {
            assertEquals(expected, it)
        }

        // then
        coVerify {
            arrangement.searchDAO.getKnownContactsExcludingAConversation(eq(conversationId.toDao()))
        }.wasInvoked(exactly = once)
    }

    //     -------
    @Test
    fun givenNotExcludedConversation_whenCallingSearchLocalByName_thenTheCorrectDAOFunctionIsCalled() = runTest {
        // given
        val searchResult = listOf(
            UserSearchEntity(
                id = UserIDEntity("id", "domain"),
                name = "name",
                completeAssetId = null,
                previewAssetId = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                type = UserTypeEntity.STANDARD,
                handle = "handle"
            )
        )

        val expected = searchResult.map {
            UserSearchDetails(
                id = it.id.toModel(),
                name = it.name,
                completeAssetId = it.completeAssetId?.toModel(),
                previewAssetId = it.previewAssetId?.toModel(),
                type = UserType.INTERNAL,
                connectionStatus = ConnectionState.ACCEPTED,
                handle = "handle"
            )
        }
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withSearchList(searchResult)
            }

        // when
        searchUserRepository.searchLocalByName("name", null).shouldSucceed {
            assertEquals(expected, it)
        }

        // then
        coVerify {
            arrangement.searchDAO.searchList(eq("name"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenExcludedConversation_whenCallingSearchLocalByName_thenTheCorrectDAOFunctionIsCalled() = runTest {
        // given
        val conversationId = ConversationId("conversationId", "domain")
        val searchResult = listOf(
            UserSearchEntity(
                id = UserIDEntity("id", "domain"),
                name = "name",
                completeAssetId = null,
                previewAssetId = null,
                connectionStatus = ConnectionEntity.State.ACCEPTED,
                type = UserTypeEntity.STANDARD,
                handle = "handle"
            )
        )

        val expected = searchResult.map {
            UserSearchDetails(
                id = it.id.toModel(),
                name = it.name,
                completeAssetId = it.completeAssetId?.toModel(),
                previewAssetId = it.previewAssetId?.toModel(),
                type = UserType.INTERNAL,
                connectionStatus = ConnectionState.ACCEPTED,
                handle = "handle"
            )
        }

        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withTeamId(Either.Right(TestUser.SELF.teamId))
                withSearchListExcludingAConversation(searchResult)
            }

        // when
        searchUserRepository.searchLocalByName("name", conversationId).shouldSucceed {
            assertEquals(expected, it)
        }

        // then
        coVerify {
            arrangement.searchDAO.searchListExcludingAConversation(eq(conversationId.toDao()), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchQueryAndNullForConversation_thenSearchingByHandle_thenCorrectDaoFunctionIsCalled() = runTest {
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withSearchByHandle(emptyList())
            }

        searchUserRepository.searchLocalByHandle("handle", null).shouldSucceed()

        coVerify {
            arrangement.searchDAO.handleSearch(eq("handle"))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSearchQueryAndConversation_thenSearchingByHandle_thenCorrectDaoFunctionIsCalled() = runTest {
        val conversationId = ConversationId("conversationId", "domain")
        val (arrangement, searchUserRepository) = Arrangement()
            .arrange {
                withSearchByHandleExcludingConversation(emptyList())
            }

        searchUserRepository.searchLocalByHandle("handle", conversationId).shouldSucceed()

        coVerify {
            arrangement.searchDAO.handleSearchExcludingAConversation(eq("handle"), eq(conversationId.toDao()))
        }.wasInvoked(exactly = once)
    }

    internal class Arrangement : SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl(),
        SearchDAOArrangement by SearchDAOArrangementImpl() {

        @Mock
        internal val userDetailsApi: UserDetailsApi = mock(UserDetailsApi::class)

        @Mock
        internal val userSearchApiWrapper: UserSearchApiWrapper = mock(UserSearchApiWrapper::class)

        @Mock
        internal val userDAO: UserDAO = mock(UserDAO::class)

        private val searchUserRepository: SearchUserRepository by lazy {
            SearchUserRepositoryImpl(
                userDAO,
                searchDAO,
                userDetailsApi,
                userSearchApiWrapper,
                selfUserId = TestUser.SELF.id,
                selfTeamIdProvider
            )
        }

        inline fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).run {
            this to searchUserRepository
        }

        suspend fun withSearchResult(result: Either<NetworkFailure, UserSearchResponse>) = apply {
            coEvery {
                userSearchApiWrapper.search(any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withGetMultipleUsersResult(result: NetworkResponse<ListUsersDTO>) = apply {
            coEvery {
                userDetailsApi.getMultipleUsers(any())
            }.returns(result)
        }

        suspend fun withObserveUserDetailsByQualifiedIdResult(result: Flow<UserDetailsEntity?>) = apply {
            coEvery {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(result)
        }

        suspend fun withGetUsersDetailsNotInConversationByNameOrHandleOrEmailResult(result: Flow<List<UserDetailsEntity>>) = apply {
            coEvery {
                userDAO.getUsersDetailsNotInConversationByNameOrHandleOrEmail(any(), any())
            }.returns(result)
        }

        suspend fun withGetUserDetailsByNameOrHandleOrEmailAndConnectionStatesResult(result: Flow<List<UserDetailsEntity>>) = apply {
            coEvery {
                userDAO.getUserDetailsByNameOrHandleOrEmailAndConnectionStates(any(), any())
            }.returns(result)
        }

        suspend fun withGetUserDetailsByHandleAndConnectionStatesResult(result: Flow<List<UserDetailsEntity>>) = apply {
            coEvery {
                userDAO.getUserDetailsByHandleAndConnectionStates(any(), any())
            }.returns(result)
        }

        suspend fun withGetUsersDetailsNotInConversationByHandleResult(result: Flow<List<UserDetailsEntity>>) = apply {
            coEvery {
                userDAO.getUsersDetailsNotInConversationByHandle(any(), any())
            }.returns(result)
        }

        suspend fun withUpsertUsersSuccess() = apply {
            coEvery {
                userDAO.upsertUsers(any())
            }.returns(Unit)
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
