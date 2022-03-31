package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.publicuser.model.UserSearchResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.network.api.contact.search.SearchPolicyDTO
import com.wire.kalium.network.api.contact.search.UserSearchApi
import com.wire.kalium.network.api.contact.search.UserSearchResponse
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.UserDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SearchUserRepositoryTest {

    @Mock
    private val userSearchApi: UserSearchApi = mock(classOf<UserSearchApi>())

    @Mock
    private val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

    @Mock
    private val publicUserMapper: PublicUserMapper = mock(classOf<PublicUserMapper>())

    @Mock
    private val userDAO: UserDAO = mock(classOf<UserDAO>())

    private lateinit var searchUserRepository: SearchUserRepository

    @BeforeTest
    fun setup() {
        searchUserRepository = SearchUserRepositoryImpl(userDAO, userSearchApi, userDetailsApi, publicUserMapper)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenUserDetailsApiAndPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)
        //then
        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_ThenPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            //given
            given(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { TestNetworkResponseError.genericError() }
            //when
            val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            //then
            verify(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .with(any())
                .wasInvoked(exactly = once)

            verify(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .with(any())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsSuccess() = runTest {
        //given
        given(userSearchApi)
            .suspendFunction(userSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(GET_MULTIPLE_USER_RESPONSE, mapOf(), 200) }

        given(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponses)
            .whenInvokedWith(any())
            .then { PUBLIC_USERS }

        //when
        val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Right<UserSearchResult>>(actual)
    }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsEqualToExpectedValue() =
        runTest {
            //given
            given(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(GET_MULTIPLE_USER_RESPONSE, mapOf(), 200) }

            given(publicUserMapper)
                .function(publicUserMapper::fromUserDetailResponses)
                .whenInvokedWith(any())
                .then { PUBLIC_USERS }

            val expectedResult = UserSearchResult(
                result = PUBLIC_USERS
            )
            //when
            val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<UserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    @Test
    fun givenAValidUserSearchWithEmptyResults_WhenSearchingSomeText_ThenResultIsAnEmptyList() =
        runTest {
            //given
            given(userSearchApi)
                .suspendFunction(userSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(EMPTY_CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(emptyList(), mapOf(), 200) }

            given(publicUserMapper)
                .function(publicUserMapper::fromUserDetailResponses)
                .whenInvokedWith(any())
                .then { emptyList() }

            val expectedResult = UserSearchResult(
                result = emptyList()
            )
            //when
            val actual = searchUserRepository.searchUserDirectory(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<UserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    private companion object {
        val TEST_QUERY = "testQuery"
        val TEST_DOMAIN = "testDomain"


        val CONTACTS = buildList {
            for (i in 1..5) {
                add(
                    ContactDTO(
                        accentId = i,
                        handle = "handle$i",
                        id = "id$i",
                        name = "name$i",
                        qualifiedID = UserId(value = "value$i", domain = "domain$i"),
                        team = "team$i"
                    )
                )
            }
        }

        val PUBLIC_USERS = buildList {
            for (i in 1..5) {
                add(
                    PublicUser(
                        id = com.wire.kalium.logic.data.user.UserId(value = "value$i", domain = "domain$i"),
                        name = "name$i",
                        handle = "handle$i",
                        email = "email$i",
                        phone = "phone$i",
                        accentId = i,
                        team = "team$i",
                        previewPicture = null,
                        completePicture = null
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

        val EMPTY_CONTACT_SEARCH_RESPONSE = UserSearchResponse(
            documents = emptyList(),
            found = 0,
            returned = 0,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 100,
        )

        val GET_MULTIPLE_USER_RESPONSE = buildList {
            for (i in 1..5) {
                add(
                    UserProfileDTO(
                        accentId = i,
                        handle = "handle$i",
                        id = QualifiedID(value = "value$i", domain = "domain$i"),
                        name = "name$i",
                        legalHoldStatus = LegalHoldStatusResponse.ENABLED,
                        teamId = "team$i",
                        assets = emptyList(),
                        deleted = null,
                        email = null,
                        expiresAt = null,
                        nonQualifiedId = "value$i",
                        service = null
                    )
                )
            }
        }
    }

}
