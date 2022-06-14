package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.conversation.UserType
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserTypeMapper
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ObserveMemberDetailsByIdsUseCaseTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val userTypeMapper = mock(UserTypeMapper::class)

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) {
        stubsUnitByDefault = true
    }

    private lateinit var observeMemberDetailsByIds: ObserveMemberDetailsByIdsUseCase

    @BeforeTest
    fun setup() {
        observeMemberDetailsByIds = ObserveMemberDetailsByIdsUseCase(
            userRepository,
            syncManager,
            userTypeMapper
        )
    }

    @Test
    fun givenAUserIdList_whenObservingMembers_thenTheSyncManagerIsCalled() = runTest {
        val userIds = listOf(TestUser.SELF.id)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeMemberDetailsByIds(userIds)

        verify(syncManager)
            .function(syncManager::startSyncIfIdle)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val firstSelfUser = TestUser.SELF
        val secondSelfUser = firstSelfUser.copy(name = "Updated name")
        val selfUserUpdates = listOf(firstSelfUser, secondSelfUser)
        val userIds = listOf(firstSelfUser.id)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(selfUserUpdates.asFlow())

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeMemberDetailsByIds(userIds).test {
            assertContentEquals(listOf(MemberDetails.Self(firstSelfUser)), awaitItem())
            assertContentEquals(listOf(MemberDetails.Self(secondSelfUser)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenOtherUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val firstOtherUser = TestUser.OTHER
        val secondOtherUser = firstOtherUser.copy(name = "Updated name")
        val otherUserUpdates = listOf(firstOtherUser, secondOtherUser)
        val userIds = listOf(firstOtherUser.id)

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(otherUserUpdates.asFlow())

        given(userTypeMapper)
            .function(userTypeMapper::fromOtherUserAndSelfUser)
            .whenInvokedWith(anything(),anything())
            .thenReturn(UserType.GUEST)

        observeMemberDetailsByIds(userIds).test {
            assertContentEquals(listOf(MemberDetails.Other(firstOtherUser, UserType.GUEST)), awaitItem())
            assertContentEquals(listOf(MemberDetails.Other(secondOtherUser, UserType.GUEST)), awaitItem())
            awaitComplete()
        }
    }
}
