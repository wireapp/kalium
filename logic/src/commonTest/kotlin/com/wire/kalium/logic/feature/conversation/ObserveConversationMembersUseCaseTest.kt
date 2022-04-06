package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.mock
import kotlin.test.BeforeTest
import kotlin.test.Test

class ObserveConversationMembersUseCaseTest {

    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val syncManager = mock(SyncManager::class)

    private lateinit var observeConversationMembers: ObserveConversationMembersUseCase

    @BeforeTest
    fun setup() {
        observeConversationMembers = ObserveConversationMembersUseCase(
            conversationRepository,
            userRepository,
            syncManager
        )
    }

    @Test
    fun givenAConversationId_whenObservingMembers_thenTheSyncManagerIsCalled(){
        TODO()
    }

    @Test
    fun givenAConversationId_whenObservingMembers_thenConversationRepositoryIsCalledWithCorrectID(){
        TODO()
    }

    @Test
    fun givenSelfUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow(){
        TODO()
    }

    @Test
    fun givenOtherUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow(){
        TODO()
    }

    @Test
    fun givenANewMemberIsAdded_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow(){
        TODO()
    }
}
