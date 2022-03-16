package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.publicuser.model.PublicUser
import com.wire.kalium.logic.data.publicuser.model.PublicUserSearchResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.contact.search.ContactSearchResponse
import com.wire.kalium.network.api.contact.search.SearchPolicyDTO
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsResponse
import com.wire.kalium.network.utils.NetworkResponse
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


class PublicUserRepositoryTest {

    @Mock
    private val contactSearchApi: ContactSearchApi = mock(classOf<ContactSearchApi>())

    @Mock
    private val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

    @Mock
    private val publicUserMapper: PublicUserMapper = mock(classOf<PublicUserMapper>())

    private lateinit var publicUserRepository: PublicUserRepository

    @BeforeTest
    fun setup() {
        publicUserRepository = PublicUserRepositoryImpl(contactSearchApi, userDetailsApi, publicUserMapper)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenUserDetailsApiAndPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)
        //then
        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponses)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_ThenPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponses)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            //given
            given(contactSearchApi)
                .suspendFunction(contactSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { TestNetworkResponseError.genericError() }
            //when
            val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

            //then
            verify(contactSearchApi)
                .suspendFunction(contactSearchApi::search)
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
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
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
        val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Right<PublicUserSearchResult>>(actual)
    }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsEqualToExpectedValue() =
        runTest {
            //given
            given(contactSearchApi)
                .suspendFunction(contactSearchApi::search)
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

            val expectedResult = PublicUserSearchResult(
                totalFound = PUBLIC_USERS.size,
                publicUsers = PUBLIC_USERS
            )
            //when
            val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<PublicUserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccessWithNoResult_WhenSearchPublicContact_ThenResultIsEqualToEmptyList() =
        runTest {
            //given
            given(contactSearchApi)
                .suspendFunction(contactSearchApi::search)
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

            val expectedResult = PublicUserSearchResult(
                totalFound = 0,
                publicUsers = emptyList()
            )
            //when
            val actual = publicUserRepository.searchPublicContact(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<PublicUserSearchResult>>(actual)
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

        val CONTACT_SEARCH_RESPONSE = ContactSearchResponse(
            documents = CONTACTS,
            found = CONTACTS.size,
            returned = 5,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 100,
        )

        val EMPTY_CONTACT_SEARCH_RESPONSE = ContactSearchResponse(
            documents = emptyList(),
            found = 0,
            returned = 0,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 100,
        )

        val GET_MULTIPLE_USER_RESPONSE = buildList {
            for (i in 1..5) {
                add(
                    UserDetailsResponse(
                        accentId = i,
                        handle = "handle$i",
                        id = QualifiedID(value = "value$i", domain = "domain$i"),
                        name = "name$i",
                        legalHoldStatus = LegalHoldStatusResponse.ENABLED,
                        team = "team$i",
                        assets = emptyList()
                    )
                )
            }
        }
    }

}
