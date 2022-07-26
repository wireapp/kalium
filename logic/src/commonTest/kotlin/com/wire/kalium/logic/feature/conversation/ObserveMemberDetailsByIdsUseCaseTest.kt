package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class, ConfigurationApi::class)
class ObserveMemberDetailsByIdsUseCaseTest {

    @Mock
    private val userRepository = mock(UserRepository::class)

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) {
        stubsUnitByDefault = true
    }

    private lateinit var observeMemberDetailsByIds: ObserveUserListByIdUseCase

    @BeforeTest
    fun setup() {
        observeMemberDetailsByIds = ObserveUserListByIdUseCase(
            userRepository,
            syncManager,
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
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(anything())
            .thenReturn(selfUserUpdates.asFlow())

        observeMemberDetailsByIds(userIds).test {
            assertContentEquals(listOf(firstSelfUser), awaitItem())
            assertContentEquals(listOf(secondSelfUser), awaitItem())
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
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(TestUser.SELF.id))
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(anything())
            .thenReturn(otherUserUpdates.asFlow())

        observeMemberDetailsByIds(userIds).test {
            assertContentEquals(listOf(firstOtherUser), awaitItem())
            assertContentEquals(listOf(secondOtherUser), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenUserIsNotKnown_whenObservingMembers_thenDoNotBlockTheFlowWaitingForItsData() = runTest {
        val knownUser = TestUser.OTHER
        val notKnownUserId = UserId("not-known-user-id", "domain")
        val userIds = listOf(knownUser.id, notKnownUserId)

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(knownUser.id))
            .then { flowOf(knownUser) }

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(notKnownUserId))
            .then { flowOf(null) }

        observeMemberDetailsByIds(userIds).test {
            val list = awaitItem()
            assertEquals(list.size, 1) // second one is just not returned, we don't have its data and don't want to block the flow
            assertContentEquals(listOf(knownUser), list)
            awaitComplete()
        }
    }
}
