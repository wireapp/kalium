package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetIncomingCallsUseCaseTest {

    @Mock
    val callRepository: CallRepository = mock(classOf<CallRepository>())

    @Mock
    val syncManager: SyncManager = mock(classOf<SyncManager>())

    private lateinit var getIncomingCallsUseCase: GetIncomingCallsUseCase

    @BeforeTest
    fun setUp() {
        getIncomingCallsUseCase = GetIncomingCallsUseCaseImpl(callRepository, syncManager)
    }

    @Test
    fun givenAnEmptyCallList_whenInvokingGetIncomingCallsUseCase_thenEmitsAnEmptyListOfCalls() = runTest {
        given(syncManager).suspendFunction(syncManager::waitForSlowSyncToComplete).whenInvoked().thenReturn(Unit)
        given(callRepository).invocation { incomingCallsFlow() }.then { MutableStateFlow(listOf<Call>()) }

        getIncomingCallsUseCase().test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun givenNotEmptyCallList_whenInvokingGetIncomingCallsUseCase_thenNonEmptyNotificationList() = runTest {
        given(syncManager).suspendFunction(syncManager::waitForSlowSyncToComplete).whenInvoked().thenReturn(Unit)
        val oneOnOneDetails = ConversationDetails.OneOne(
            TestConversation.ONE_ON_ONE,
            TestUser.OTHER,
            ConnectionState.ACCEPTED,
            LegalHoldStatus.ENABLED,
            UserType.INTERNAL,
        )
        given(callRepository).invocation { incomingCallsFlow() }.then {
            MutableStateFlow(
                listOf<Call>(
                    Call(
                        TestConversation.id(0),
                        CallStatus.INCOMING,
                        "client1",
                        "ONE_ON_ONE Name",
                        Conversation.Type.ONE_ON_ONE,
                        null,
                        null
                    ),
                    Call(
                        TestConversation.id(1),
                        CallStatus.INCOMING,
                        "client2",
                        "ONE_ON_ONE Name",
                        Conversation.Type.ONE_ON_ONE,
                        null,
                        null
                    )
                )
            )
        }

        getIncomingCallsUseCase().test {
            val firstItem = awaitItem()
            assertTrue(firstItem.isNotEmpty())
            assertEquals(firstItem.first().conversationId, TestConversation.id(0))
        }
    }

}
