package com.wire.kalium.logic.data.publicuser

import com.wire.kalium.network.api.contact.search.UserSearchApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock

class UserSearchApiWrapperTest {



    class Arrangement(){

        @Mock
        private val userSearchApi : UserSearchApi = mock(classOf<UserSearchApi>())


        fun arrange() = this to UserSearchApiWrapper()
    }


}
