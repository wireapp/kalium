package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.network.api.contact.search.ContactSearchApi
import com.wire.kalium.network.api.user.details.UserDetailsApi
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

    // when contactSearchApi returns error serachpublic returns CoreFailure.Uknown
    @Test
    fun givenApiRequestFail_whenRequestingActivationCodeForAnEmail_thenNetworkFailureIsPropagated() = runTest {
        //given
        given(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .whenInvokedWith(any())
            .then { TestNetworkResponseError.genericError() }

        //when
        val actual = publicUserRepository.searchPublicContact("test")

        //then
        assertIs<Either.Left<NetworkFailure>>(actual)

        verify(contactSearchApi)
            .suspendFunction(contactSearchApi::search)
            .with(any())
            .wasInvoked(exactly = once)

        verify(userDetailsApi)
            .suspendFunction(userDetailsApi::getMultipleUsers)
            .with(any())
            .wasNotInvoked()

        verify(publicUserMapper)
            .function(publicUserMapper::fromUserDetailResponse)
            .with(any())
            .wasNotInvoked()
    }

    // when contactSearchApi.search returns success, but userApi.getMultipleUsers fails, serarchpubliccontact returns CoreFailure
    @Test
    fun adas() = runTest {

    }

    //when contactserach api results success, and getmultuiple user returns success then searchpubliconctact returns mapped responses
    @Test
    fun dasdadas() = runTest {

    }

}
