package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.LegalHoldStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveConversationDetailsUseCaseTest {

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    @Mock
    private val syncManager: SyncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    private lateinit var observeConversationsUseCase: ObserveConversationDetailsUseCase

    @BeforeTest
    fun setup() {
        observeConversationsUseCase = ObserveConversationDetailsUseCase(conversationRepository, syncManager)
    }

    @Test
    fun givenAConversationId_whenObservingConversationUseCase_thenTheConversationRepositoryShouldBeCalledWithTheCorrectID() = runTest {
        val conversationId = TestConversation.ID

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .then { flowOf() }

        observeConversationsUseCase(conversationId)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .with(eq(conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationID_whenObservingConversationUseCase_thenSyncManagerShouldBeCalled() = runTest {
        val conversationId = TestConversation.ID

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .then { flowOf() }

        observeConversationsUseCase(conversationId)

        verify(syncManager)
            .function(syncManager::startSyncIfIdle)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenTheConversationIsUpdated_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() = runTest {
        val conversation = TestConversation.GROUP()
        val conversationDetailsValues = listOf(
            Either.Right(ConversationDetails.Group(conversation, LegalHoldStatus.DISABLED)),
            Either.Right(ConversationDetails.Group(conversation.copy(name = "New Name"), LegalHoldStatus.DISABLED))
        )

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .then { conversationDetailsValues.asFlow() }

        observeConversationsUseCase(TestConversation.ID).test {
            awaitItem().let { item ->
                assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                assertEquals(conversationDetailsValues[0].value, item.conversationDetails)
            }
            awaitItem().let { item ->
                assertIs<ObserveConversationDetailsUseCase.Result.Success>(item)
                assertEquals(conversationDetailsValues[1].value, item.conversationDetails)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenTheStorageFailure_whenObservingConversationUseCase_thenThisUpdateIsPropagatedInTheFlow() = runTest {
        val failure = StorageFailure.DataNotFound

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationDetailsById)
            .whenInvokedWith(anything())
            .then { flowOf(Either.Left(failure)) }

        observeConversationsUseCase(TestConversation.ID).test {
            awaitItem().let { item ->
                assertIs<ObserveConversationDetailsUseCase.Result.Failure>(item)
                assertEquals(failure, item.storageFailure)
            }
            awaitComplete()
        }
    }
}
