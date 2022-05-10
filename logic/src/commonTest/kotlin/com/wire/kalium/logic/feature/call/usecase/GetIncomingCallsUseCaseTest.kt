package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetIncomingCallsUseCaseTest {
    @Mock
    val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

    @Mock
    val callManager: CallManager = mock(classOf<CallManager>())

    @Mock
    val syncManager: SyncManager = mock(classOf<SyncManager>())

    private lateinit var getIncomingCallsUseCase: GetIncomingCallsUseCase

    @BeforeTest
    fun setUp() {
        getIncomingCallsUseCase = GetIncomingCallsUseCaseImpl(callManager, syncManager, conversationRepository)
    }

    @Test
    fun givenEmptyCallList_thenEmptyNotificationList() = runTest {
        given(syncManager).suspendFunction(syncManager::waitForSlowSyncToComplete).whenInvoked().thenReturn(Unit)
        given(callManager).invocation { allCalls }.then { MutableStateFlow(listOf<Call>()) }

        getIncomingCallsUseCase().test {
            assertTrue(awaitItem().isEmpty())
        }
    }

    @Test
    fun givenCallListNotEmpty_thenNonEmptyNotificationList() = runTest {
        given(syncManager).suspendFunction(syncManager::waitForSlowSyncToComplete).whenInvoked().thenReturn(Unit)
        given(conversationRepository).suspendFunction(conversationRepository::getConversationDetailsById)
            .whenInvokedWith(anything())
            .then { id ->
                flowOf(
                    ConversationDetails.OneOne(
                        TestConversation.one_on_one(id),
                        TestUser.OTHER,
                        ConnectionState.ACCEPTED,
                        LegalHoldStatus.ENABLED,
                        UserType.INTERNAL,
                    )
                )
            }
        given(callManager).invocation { allCalls }.then {
            MutableStateFlow(
                listOf<Call>(
                    Call(TestConversation.id(0), CallStatus.INCOMING),
                    Call(TestConversation.id(1), CallStatus.INCOMING)
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
