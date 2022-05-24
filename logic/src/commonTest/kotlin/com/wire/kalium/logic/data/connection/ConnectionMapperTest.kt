package com.wire.kalium.logic.data.connection

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.network.api.user.connection.ConnectionStateDTO
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionMapperTest {
    //TODO write tests
    @Mock
    val idMapper = mock(classOf<IdMapper>())

    @Mock
    val statusMapper = mock(classOf<ConnectionStatusMapper>())

    @Mock
    val userMapper = mock(classOf<UserMapper>())

    private val mapper = ConnectionMapperImpl(idMapper, statusMapper, userMapper);

}
