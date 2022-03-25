package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.wireuser.WireUserMapper
import com.wire.kalium.logic.data.wireuser.SearchUserRepository
import com.wire.kalium.logic.data.wireuser.SearchUserRepositoryImpl
import com.wire.kalium.logic.data.wireuser.model.WireUser
import com.wire.kalium.logic.feature.wireuser.search.WireUserSearchResult
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.QualifiedID
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.contact.search.ContactDTO
import com.wire.kalium.network.api.contact.search.WireUserSearchApi
import com.wire.kalium.network.api.contact.search.WireUserSearchResponse
import com.wire.kalium.network.api.contact.search.SearchPolicyDTO
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.user.details.UserDetailsApi
import com.wire.kalium.network.api.user.details.UserDetailsResponse
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
    private val wireUserSearchApi: WireUserSearchApi = mock(classOf<WireUserSearchApi>())

    @Mock
    private val userDetailsApi: UserDetailsApi = mock(classOf<UserDetailsApi>())

    @Mock
    private val wireUserMapper: WireUserMapper = mock(classOf<WireUserMapper>())

    @Mock
    private val userDAO: UserDAO = mock(classOf<UserDAO>())

    private lateinit var searchUserRepository: SearchUserRepository

    @BeforeTest
    fun setup() {
        searchUserRepository = SearchUserRepositoryImpl(userDAO, wireUserSearchApi, userDetailsApi, wireUserMapper)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenOnlyContactSearchApiISInvoked() = runTest {
        //given
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenContactSearchApiFailure_whenSearchPublicContact_thenUserDetailsApiAndPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)
        //then
        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(wireUserMapper)
            .function(wireUserMapper::fromUserDetailResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_resultIsFailure() = runTest {
        //given
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)
    }

    @Test
    fun givenContactSearchApiSuccessButuserDetailsApiFailure_whenSearchPublicContact_ThenPublicUserMapperIsNotInvoked() = runTest {
        //given
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }
        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

        //then
        verify(wireUserMapper)
            .function(wireUserMapper::fromUserDetailResponse)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenContactSearchApiSuccessButUserDetailsApiFailure_whenSearchPublicContact_ThenContactSearchApiAndUserDetailsApiIsInvoked() =
        runTest {
            //given
            given(wireUserSearchApi)
                .suspendFunction(wireUserSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { TestNetworkResponseError.genericError() }
            //when
            val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

            //then
            verify(wireUserSearchApi)
                .suspendFunction(wireUserSearchApi::search)
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
        given(wireUserSearchApi)
            .suspendFunction(wireUserSearchApi::search)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

        given(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .whenInvokedWith(any())
            .then { NetworkResponse.Success(GET_MULTIPLE_USER_RESPONSE, mapOf(), 200) }

        given(wireUserMapper)
            .function(wireUserMapper::fromUserDetailResponses)
            .whenInvokedWith(any())
            .then { PUBLIC_USERS }

        //when
        val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

        //then
        assertIs<Either.Right<WireUserSearchResult>>(actual)
    }

    @Test
    fun givenContactSearchApiAndUserDetailsApiAndPublicUserApiReturnSuccess_WhenSearchPublicContact_ThenResultIsEqualToExpectedValue() =
        runTest {
            //given
            given(wireUserSearchApi)
                .suspendFunction(wireUserSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(GET_MULTIPLE_USER_RESPONSE, mapOf(), 200) }

            given(wireUserMapper)
                .function(wireUserMapper::fromUserDetailResponses)
                .whenInvokedWith(any())
                .then { PUBLIC_USERS }

            val expectedResult = WireUserSearchResult(
                wireUsers = PUBLIC_USERS
            )
            //when
            val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<WireUserSearchResult>>(actual)
            assertEquals(expectedResult, actual.value)
        }

    @Test
    fun givenAValidUserSearchWithEmptyResults_WhenSearchingSomeText_ThenResultIsAnEmptyList() =
        runTest {
            //given
            given(wireUserSearchApi)
                .suspendFunction(wireUserSearchApi::search)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(EMPTY_CONTACT_SEARCH_RESPONSE, mapOf(), 200) }

            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getMultipleUsers)
                .whenInvokedWith(any())
                .then { NetworkResponse.Success(emptyList(), mapOf(), 200) }

            given(wireUserMapper)
                .function(wireUserMapper::fromUserDetailResponses)
                .whenInvokedWith(any())
                .then { emptyList() }

            val expectedResult = WireUserSearchResult(
                wireUsers = emptyList()
            )
            //when
            val actual = searchUserRepository.searchWireContact(TEST_QUERY, TEST_DOMAIN)

            assertIs<Either.Right<WireUserSearchResult>>(actual)
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
                    WireUser(
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

        val CONTACT_SEARCH_RESPONSE = WireUserSearchResponse(
            documents = CONTACTS,
            found = CONTACTS.size,
            returned = 5,
            searchPolicy = SearchPolicyDTO.FULL_SEARCH,
            took = 100,
        )

        val EMPTY_CONTACT_SEARCH_RESPONSE = WireUserSearchResponse(
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
