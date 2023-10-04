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
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.wire.kalium.network.api.base.model.UserId as UserIdDTO

// TODO: refactor to arrangement pattern
@OptIn(ExperimentalCoroutinesApi::class)
class SearchUserRepositoryTest {

    @Mock
    private val metadataDAO: MetadataDAO = mock(classOf<MetadataDAO>())

    @Mock
    private val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

    @Mock
    private val userSearchApiWrapper: UserSearchApiWrapper = mock(classOf<UserSearchApiWrapper>())

    @Mock
    private val publicUserMapper: PublicUserMapper = mock(classOf<PublicUserMapper>())

    @Mock
    private val userMapper: UserMapper = mock(classOf<UserMapper>())

    @Mock
    private val idMapper: IdMapper = mock(classOf<IdMapper>())

    @Mock
    private val domainUserTypeMapper: DomainUserTypeMapper = mock(classOf<DomainUserTypeMapper>())

    @Mock
    private val userDAO: UserDAO = mock(classOf<UserDAO>())

    private lateinit var searchUserRepository: SearchUserRepository

    @BeforeTest
    fun setup() {
        searchUserRepository = SearchUserRepositoryImpl(
            userDAO,
            metadataDAO,
            userDetailsApi,
            userSearchApiWrapper,
            publicUserMapper,
            userMapper,
            domainUserTypeMapper
        )

        given(domainUserTypeMapper).invocation { federated }.then { UserType.FEDERATED }

        given(domainUserTypeMapper).invocation { guest }.then { UserType.GUEST }

        given(domainUserTypeMapper).invocation { standard }.then { UserType.INTERNAL }

        given(domainUserTypeMapper).invocation { external }.then { UserType.EXTERNAL }
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Left(TestNetworkResponseError.noNetworkConnection()))

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Left(TestNetworkResponseError.noNetworkConnection()))

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        verify(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .with(anything(), anything(), anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenUserDetailsApiAndPublicUserMapperIsNotInvoked() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Left(TestNetworkResponseError.noNetworkConnection()))

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)
        // then
        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(publicUserMapper)
            .function(publicUserMapper::fromUserProfileDtoToOtherUser)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .thenReturn(TestNetworkResponseError.genericResponseError())

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_ThenPublicUserMapperIsNotInvoked() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericResponseError() }

        // when
        searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        verify(publicUserMapper)
            .function(publicUserMapper::fromUserProfileDtoToOtherUser)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            // given
            given(userSearchApiWrapper)
                .suspendFunction(userSearchApiWrapper::search)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { TestNetworkResponseError.genericResponseError() }
            // when
            searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            // then
            verify(userSearchApiWrapper)
                .suspendFunction(userSearchApiWrapper::search)
                .with(anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)

            verify(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsSuccess() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(USER_RESPONSE, mapOf(), 200) }

        given(publicUserMapper)
            .function(publicUserMapper::fromUserProfileDtoToOtherUser)
            .whenInvokedWith(any(), any())
            .then { _, _ -> PUBLIC_USER }

        given(metadataDAO)
            .suspendFunction(metadataDAO::valueByKeyFlow)
            .whenInvokedWith(any())
            .then { flowOf(JSON_QUALIFIED_ID) }

        given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
            .whenInvokedWith(any())
            .then { flowOf(USER_ENTITY) }

        given(userMapper)
            .function(userMapper::fromUserEntityToSelfUser)
            .whenInvokedWith(any())
            .then { SELF_USER }

        given(domainUserTypeMapper)
            .invocation {
                domainUserTypeMapper.fromTeamAndDomain(
                    "domain",
                    null,
                    "team",
                    "someId",
                    false
                )
            }.then { UserType.FEDERATED }

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
    }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsEqualToExpectedValue() =
        runTest {
            // given
            given(userSearchApiWrapper)
                .suspendFunction(userSearchApiWrapper::search)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(USER_RESPONSE, mapOf(), 200) }

            given(publicUserMapper)
                .function(publicUserMapper::fromUserProfileDtoToOtherUser)
                .whenInvokedWith(any(), any())
                .then { _, _ -> PUBLIC_USER }

            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(JSON_QUALIFIED_ID) }

            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(USER_ENTITY) }

            given(userMapper)
                .function(userMapper::fromUserEntityToSelfUser)
                .whenInvokedWith(any())
                .then { SELF_USER }

            given(domainUserTypeMapper)
                .invocation {
                    domainUserTypeMapper.fromTeamAndDomain(
                        "domain",
                        null,
                        "team",
                        "someId",
                        false
                    )
                }.then { UserType.FEDERATED }

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
            given(userSearchApiWrapper)
                .suspendFunction(userSearchApiWrapper::search)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE))

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(USER_RESPONSE.copy(usersFound = emptyList()), mapOf(), 200) }

            given(metadataDAO)
                .suspendFunction(metadataDAO::valueByKeyFlow)
                .whenInvokedWith(any())
                .then { flowOf(JSON_QUALIFIED_ID) }

            given(userDAO).suspendFunction(userDAO::getUserByQualifiedID)
                .whenInvokedWith(any())
                .then { flowOf(USER_ENTITY) }

            given(userMapper)
                .function(userMapper::fromUserEntityToSelfUser)
                .whenInvokedWith(any())
                .then { SELF_USER }

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
            given(userDAO)
                .suspendFunction(userDAO::getUsersNotInConversationByNameOrHandleOrEmail)
                .whenInvokedWith(anything(), anything())
                .then { _, _ -> flowOf(listOf()) }

            given(userDAO)
                .suspendFunction(userDAO::getUserByNameOrHandleOrEmailAndConnectionStates)
                .whenInvokedWith(anything(), anything())
                .then { _, _ -> flowOf(listOf()) }

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

            verify(userDAO)
                .suspendFunction(userDAO::getUserByNameOrHandleOrEmailAndConnectionStates)
                .with(anything(), anything())
                .wasNotInvoked()

            verify(userDAO)
                .suspendFunction(userDAO::getUsersNotInConversationByNameOrHandleOrEmail)
                .with(anything(), anything())
                .wasInvoked(Times(1))
        }

    @Test
    fun givenASearchWithConversationExcludedOption_WhenSearchingUsersByHandle_ThenSearchForUsersNotInTheConversation() = runTest {
        // given
        given(userDAO)
            .suspendFunction(userDAO::getUserByHandleAndConnectionStates)
            .whenInvokedWith(anything(), anything())
            .then { _, _ -> flowOf(listOf()) }

        given(userDAO)
            .suspendFunction(userDAO::getUsersNotInConversationByHandle)
            .whenInvokedWith(anything(), anything())
            .then { _, _ -> flowOf(listOf()) }

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
        verify(userDAO)
            .suspendFunction(userDAO::getUserByHandleAndConnectionStates)
            .with(anything(), anything())
            .wasNotInvoked()

        verify(userDAO)
            .suspendFunction(userDAO::getUsersNotInConversationByHandle)
            .with(anything(), anything())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiSuccessButListIsEmpty_whenSearchPublicContact_thenReturnEmptyListWithoutCallingUserDetailsApi() = runTest {
        // given
        given(userSearchApiWrapper)
            .suspendFunction(userSearchApiWrapper::search)
            .whenInvokedWith(anything(), anything(), anything(), anything())
            .thenReturn(Either.Right(CONTACT_SEARCH_RESPONSE_EMPTY))

        // when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        // then
        assertIs<Either.Right<UserSearchResult>>(actual)
        assertTrue { actual.value.result.isEmpty() }

        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()
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
            defederated = false,
            isProteusVerified = false
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

        val USER_RESPONSE = ListUsersDTO(
            usersFailed = emptyList(),
            usersFound = listOf(
                UserProfileDTO(
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
            )
        )

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
            defederated = false,
            isProteusVerified = false
        )

        val SELF_USER = SelfUser(
            id = QualifiedID("someValue", "someId"),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 0,
            teamId = null,
            connectionStatus = ConnectionState.NOT_CONNECTED,
            previewPicture = null,
            completePicture = null,
            availabilityStatus = UserAvailabilityStatus.AVAILABLE,
            expiresAt = null
        )

    }

}
