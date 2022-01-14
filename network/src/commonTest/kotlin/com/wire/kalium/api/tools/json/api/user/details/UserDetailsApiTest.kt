package com.wire.kalium.api.tools.json.api.user.details

import com.wire.kalium.api.ApiTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class UserDetailsApiTest : ApiTest {

    @Test
    fun givenListOfQualifiedIds_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        TODO()
    }

    @Test
    fun givenListOfQualifiedHandles_whenGettingListOfUsers_thenBodyShouldSerializeCorrectly() = runTest {
        TODO()
    }

    @Test
    fun givenAValidRequest_whenGettingListOfUsers_thenCorrectHttpHeadersAndMethodShouldBeUsed() = runTest {
        TODO()
    }
}
