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
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.base.authenticated.search.ContactDTO
import com.wire.kalium.network.api.base.authenticated.search.SearchPolicyDTO
import com.wire.kalium.network.api.base.authenticated.search.UserSearchResponse
import com.wire.kalium.network.api.base.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.model.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
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
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

// TODO: refactor to arrangement pattern
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

        verify(arrangement.publicUserMapper)
            .function(arrangement.publicUserMapper::fromUserProfileDtoToOtherUser)
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
        verify(arrangement.publicUserMapper)
            .function(arrangement.publicUserMapper::fromUserProfileDtoToOtherUser)
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
            .withFromUserProfileDtoToOtherUserResult(PUBLIC_USER)
            .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
            .withGetUserByQualifiedIdResult(flowOf(USER_ENTITY))
            .withFromUserEntityToSelfUser(SELF_USER)
            .withFromTeamAndDomain(UserType.FEDERATED)
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
                .withFromUserProfileDtoToOtherUserResult(PUBLIC_USER)
                .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
                .withGetUserByQualifiedIdResult(flowOf(USER_ENTITY))
                .withFromUserEntityToSelfUser(SELF_USER)
                .withFromTeamAndDomain(UserType.FEDERATED)
                .arrange()

            val expectedResult = UserSearchResult(
                result = listOf(PUBLIC_USER)
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
                .withGetUserByQualifiedIdResult(flowOf(USER_ENTITY))
                .withFromUserEntityToSelfUser(SELF_USER)
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
                .withGetUsersNotInConversationByNameOrHandleOrEmailResult(flowOf(listOf()))
                .withGetUserByNameOrHandleOrEmailAndConnectionStatesResult(flowOf(listOf()))
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

            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUserByNameOrHandleOrEmailAndConnectionStates)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(arrangement.userDAO)
                .suspendFunction(arrangement.userDAO::getUsersNotInConversationByNameOrHandleOrEmail)
                .with(anything(), anything())
                .wasInvoked(Times(1))
        }

    @Test
    fun givenASearchWithConversationExcludedOption_WhenSearchingUsersByHandle_ThenSearchForUsersNotInTheConversation() = runTest {
        // given
        val (arrangement, searchUserRepository) = Arrangement()
            .withGetUserByHandleAndConnectionStatesResult(flowOf(listOf()))
            .withGetUsersNotInConversationByHandleResult(flowOf(listOf()))
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
            .suspendFunction(arrangement.userDAO::getUserByHandleAndConnectionStates)
            .with(anything(), anything())
            .wasNotInvoked()

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::getUsersNotInConversationByHandle)
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
            usersFound = listOf(USER_PROFILE_DTO.copy(id = UserId("teamUser", SELF_USER.id.domain), teamId = SELF_USER.teamId?.value),)
        )
        val (arrangement, searchUserRepository) = Arrangement()
            .withSearchResult(Either.Right(CONTACT_SEARCH_RESPONSE))
            .withGetMultipleUsersResult(NetworkResponse.Success(userListResponse, mapOf(), 200))
            .withValueByKeyFlowResult(flowOf(JSON_QUALIFIED_ID))
            .withGetUserByQualifiedIdResult(flowOf(USER_ENTITY))
            .withFromUserEntityToSelfUser(SELF_USER)
            .withUpsertTeamMembersSuccess()
            .withFromUserProfileDtoToOtherUserResult(PUBLIC_USER)
            .withFromUserProfileDtoToUserEntityResult(USER_ENTITY)
            .withFromTeamAndDomain(UserType.FEDERATED)
            .arrange()

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertTrue { actual.value.result.isEmpty() }

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertTeamMembers)
            .with(eq(listOf(USER_ENTITY)))
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
        internal val publicUserMapper: PublicUserMapper = mock(classOf<PublicUserMapper>())

        @Mock
        internal val userMapper: UserMapper = mock(classOf<UserMapper>())

        @Mock
        internal val domainUserTypeMapper: DomainUserTypeMapper = mock(classOf<DomainUserTypeMapper>())

        @Mock
        internal val userDAO: UserDAO = mock(classOf<UserDAO>())

        private val searchUserRepository: SearchUserRepository by lazy {
            SearchUserRepositoryImpl(
                userDAO,
                metadataDAO,
                userDetailsApi,
                userSearchApiWrapper,
                publicUserMapper,
                userMapper,
                domainUserTypeMapper
            )
        }

        fun arrange() = this to searchUserRepository

        init {
            given(domainUserTypeMapper).invocation { federated }.then { UserType.FEDERATED }
            given(domainUserTypeMapper).invocation { guest }.then { UserType.GUEST }
            given(domainUserTypeMapper).invocation { standard }.then { UserType.INTERNAL }
            given(domainUserTypeMapper).invocation { external }.then { UserType.EXTERNAL }
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

        fun withFromUserProfileDtoToOtherUserResult(result: OtherUser) = apply {
            given(publicUserMapper)
                .function(publicUserMapper::fromUserProfileDtoToOtherUser)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withValueByKeyFlowResult(result: Flow<String?>) = apply {
            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withGetUserByQualifiedIdResult(result: Flow<UserEntity?>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFromUserEntityToSelfUser(result: SelfUser) = apply {
            given(userMapper)
                .function(userMapper::fromUserEntityToSelfUser)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFromTeamAndDomain(result: UserType) = apply {
            given(domainUserTypeMapper)
                .function(domainUserTypeMapper::fromTeamAndDomain)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(result)
        }

        fun withGetUsersNotInConversationByNameOrHandleOrEmailResult(result: Flow<List<UserEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersNotInConversationByNameOrHandleOrEmail)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUserByNameOrHandleOrEmailAndConnectionStatesResult(result: Flow<List<UserEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserByNameOrHandleOrEmailAndConnectionStates)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUserByHandleAndConnectionStatesResult(result: Flow<List<UserEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUserByHandleAndConnectionStates)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withGetUsersNotInConversationByHandleResult(result: Flow<List<UserEntity>>) = apply {
            given(userDAO)
                .suspendFunction(userDAO::getUsersNotInConversationByHandle)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withFromUserProfileDtoToUserEntityResult(result: UserEntity) = apply {
            given(userMapper)
                .function(userMapper::fromUserProfileDtoToUserEntity)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withUpsertTeamMembersSuccess() = apply {
            given(userDAO)
                .suspendFunction(userDAO::upsertTeamMembers)
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

        val PUBLIC_USER = OtherUser(
            id = com.wire.kalium.logic.data.user.UserId(value = "value", domain = "domain"),
            name = "name",
            handle = "handle",
            email = "email",
            phone = "phone",
            accentId = 1,
            teamId = TeamId("team"),
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.NONE,
            userType = UserType.FEDERATED,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false
        )

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

        val USER_PROFILE_DTO = UserProfileDTO(
            accentId = 1,
            handle = "handle",
            id = UserIdDTO(value = "value", domain = "domain"),
            name = "name",
            legalHoldStatus = LegalHoldStatusResponse.ENABLED,
            teamId = "team",
            assets = emptyList(),
            deleted = null,
            email = null,
            expiresAt = null,
            nonQualifiedId = "value",
            service = null
        )

        val USER_RESPONSE = ListUsersDTO(usersFailed = emptyList(), usersFound = listOf(USER_PROFILE_DTO))

        const val JSON_QUALIFIED_ID = """{"value":"test" , "domain":"test" }"""

        val USER_ENTITY = UserEntity(
            id = QualifiedIDEntity("value", "domain"),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            team = null,
            connectionStatus = ConnectionEntity.State.NOT_CONNECTED,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
            userType = UserTypeEntity.EXTERNAL,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false
        )

        val SELF_USER = SelfUser(
            id = QualifiedID("someValue", "someId"),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = TeamId("someTeamId"),
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null
        )

    }

}
