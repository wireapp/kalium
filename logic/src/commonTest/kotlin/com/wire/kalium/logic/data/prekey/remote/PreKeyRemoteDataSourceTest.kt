package com.wire.kalium.logic.data.prekey.remote

import com.wire.kalium.network.api.prekey.PreKeyApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

//TODO as there is no way to mock API class for now, unit test will be added later
class PreKeyRemoteDataSourceTest {

    @Mock
    private val preKeyListMapper = mock(classOf<PreKeyListMapper>())

    @Mock
    private val preKeyApi = mock(classOf<PreKeyApi>())

    private lateinit var preKeyRemoteDataSource: PreKeyRemoteDataSource

    @BeforeTest
    fun setup() {
        preKeyRemoteDataSource = PreKeyRemoteDataSource(preKeyApi, preKeyListMapper)
    }

    @Test
    fun given_API_failing_when_fetching_qualified_users_preKeys_then_fail_should_be_propagated() = runTest {
//        given(preKeyApi)
//            .suspendFunction(preKeyApi::getUsersPreKey)
//            .whenInvokedWith(any())
//            .then {  }

//        val result = preKeyRemoteDataSource.preKeysForMultipleQualifiedUsers(mapOf())

    }
}
