package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.oneOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CallRepositoryTest {

    @Mock
    private val callApi = mock(classOf<CallApi>())
    @Mock
    private val conversationRepository = mock(classOf<ConversationRepository>())
    @Mock
    private val userRepository = mock(classOf<UserRepository>())
    @Mock
    private val teamRepository = mock(classOf<TeamRepository>())
    @Mock
    private val scope = mock(classOf<CoroutineScope>())

    private lateinit var callRepository: CallRepository

    @BeforeTest
    fun setUp() {
        callRepository = CallDataSource(
            callApi = callApi,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
            teamRepository = teamRepository,
            scope = scope
        )
    }

    @Test
    fun whenRequestingCallConfig_withNoLimitParam_ThenAResultIsReturned() = runTest {
        given(callApi)
            .suspendFunction(callApi::getCallConfig)
            .whenInvokedWith(oneOf(null))
            .thenReturn(NetworkResponse.Success(CALL_CONFIG_API_RESPONSE, mapOf(), 200))

        val result = callRepository.getCallConfigResponse(limit = null)

        result.shouldSucceed {
            assertEquals(CALL_CONFIG_API_RESPONSE, it)
        }
    }

    private companion object {
        const val CALL_CONFIG_API_RESPONSE = "{'call':'success','config':'dummy_config'}"
    }
}
