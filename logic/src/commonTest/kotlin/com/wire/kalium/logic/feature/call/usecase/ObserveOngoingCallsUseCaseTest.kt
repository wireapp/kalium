package com.wire.kalium.logic.feature.call.usecase

import app.cash.turbine.test
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveOngoingCallsUseCaseTest {

    @Mock
    val syncManager: SyncManager = mock(classOf<SyncManager>())

    @Mock
    val callRepository: CallRepository = mock(classOf<CallRepository>())

    private lateinit var observeOngoingCalls: ObserveOngoingCallsUseCase

    @BeforeTest
    fun setUp() {
        observeOngoingCalls = ObserveOngoingCallsUseCaseImpl(
            callRepository = callRepository,
            syncManager = syncManager
        )
    }

//    @Test
//    fun givenAnEmptyCallList_whenInvokingObserveOngoingCallsUseCase_thenEmitsAnEmptyListOfCalls() = runTest {
//        given(callRepository)
//            .function(callRepository::ongoingCallsFlow)
//            .whenInvoked()
//            .thenReturn(flowOf(listOf()))
//
//        given(syncManager)
//            .function(syncManager::startSyncIfIdle)
//            .whenInvoked()
//            .thenReturn(Unit)
//
//        val result = observeOngoingCalls()
//
//        result.test {
//            assertEquals(listOf(), awaitItem())
//            awaitComplete()
//        }
//    }
//
//    @Test
//    fun givenAnOngoingCallList_whenInvokingObserveOngoingCallsUseCase_thenEmitsAnOngoingListOfCalls() = runTest {
//        given(callRepository)
//            .function(callRepository::ongoingCallsFlow)
//            .whenInvoked()
//            .thenReturn(flowOf(listOf(DUMMY_CALL)))
//
//        given(syncManager)
//            .function(syncManager::startSyncIfIdle)
//            .whenInvoked()
//            .thenReturn(Unit)
//
//        val result = observeOngoingCalls()
//
//        result.test {
//            assertEquals(listOf(DUMMY_CALL), awaitItem())
//            awaitComplete()
//        }
//    }

    private companion object {
        val DUMMY_CALL = Call(
            conversationId = ConversationId(
                value = "convId",
                domain = "domainId"
            ),
            status = CallStatus.STILL_ONGOING,
            isMuted = false,
            isCameraOn = false,
            callerId = "callerId",
            conversationName = null,
            conversationType = Conversation.Type.GROUP,
            callerName = null,
            callerTeamName = null
        )
    }
}
