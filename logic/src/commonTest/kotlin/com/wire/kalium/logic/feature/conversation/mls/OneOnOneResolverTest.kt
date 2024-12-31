/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.IncrementalSyncRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneMigratorArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneMigratorArrangementImpl
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangement
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.any
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.given
import io.mockative.matchers.OneOfMatcher
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class OneOnOneResolverTest {

    @Test
    fun givenListOneOnOneUsers_whenResolveAllOneOnOneConversations_thenResolveOneOnOneForEachUser() = runTest {
        // given
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID), TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2))
        val (arrangement, resolver) = arrange {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveAllOneOnOneConversations().shouldSucceed()

        // then
        verify(arrangement.oneOnOneProtocolSelector)
            .suspendFunction(arrangement.oneOnOneProtocolSelector::getProtocolForUser)
            .with(OneOfMatcher(oneOnOneUsers.map { it.id }))
            .wasInvoked(exactly = twice)
    }

    @Test
    fun givenListOneOnOneUsersAndSynchronizeUsers_whenResolveAllOneOnOneConversations_thenShouldFetchAllUserDetailsAtOnce() = runTest {
        // given
        val oneOnOneUsers = listOf(
            TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID),
            TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2)
        )
        val (arrangement, resolver) = arrange {
            withFetchAllOtherUsersReturning(Either.Right(Unit))
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveAllOneOnOneConversations(true).shouldSucceed()

        // then
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchAllOtherUsers)
            .wasInvoked(exactly = once)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUserInfo)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenSingleOneOnOneUserIdAndSynchronizeUser_whenResolveAllOneOnOneConversations_thenShouldFetchUserDetailsOnce() = runTest {
        // given
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Right(Unit))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUser(oneOnOneUser, true).shouldSucceed()

        // then
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchAllOtherUsers)
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUserInfo)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(eq(setOf(oneOnOneUser.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSingleOneOnOneUserIdAndSynchronizeUserFails_whenResolveAllOneOnOneConversations_thenShouldNotPropagateFailure() = runTest {
        // given
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Right(Unit))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUser(oneOnOneUser, true)
        // then
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(eq(setOf(oneOnOneUser.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSingleOneOnOneUserAndSynchronizeUsers_whenResolveAllOneOnOneConversations_thenShouldFetchUserDetailsOnce() = runTest {
        // given
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withGetKnownUserReturning(flowOf(oneOnOneUser))
            withFetchUsersByIdReturning(Either.Right(Unit))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUserId(oneOnOneUser.id, true).shouldSucceed()

        // then
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchAllOtherUsers)
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUserInfo)
            .with(any())
            .wasNotInvoked()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(eq(setOf(oneOnOneUser.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSingleOneOnOneUserAndSynchronizeUserFails_whenResolveAllOneOnOneConversations_thenShouldNotPropagateFailure() = runTest {
        // given
        val oneOnOneUser = TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID)
        val (arrangement, resolver) = arrange {
            withFetchUsersByIdReturning(Either.Left(CoreFailure.Unknown(null)))
            withGetKnownUserReturning(flowOf(oneOnOneUser))
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUserId(oneOnOneUser.id, true)
        // then
            .shouldSucceed()

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::fetchUsersByIds)
            .with(eq(setOf(oneOnOneUser.id)))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenResolvingOneConversationFails_whenResolveAllOneOnOneConversations_thenTheWholeOperationFails() = runTest {
        // given
        val oneOnOneUsers = listOf(TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID), TestUser.OTHER.copy(id = TestUser.OTHER_USER_ID_2))
        val (arrangement, resolver) = arrange {
            withGetUsersWithOneOnOneConversationReturning(oneOnOneUsers)
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        given(arrangement.oneOnOneMigrator)
            .suspendFunction(arrangement.oneOnOneMigrator::migrateToMLS)
            .whenInvokedWith(eq(oneOnOneUsers.last()))
            .thenReturn(Either.Left(CoreFailure.Unknown(null)))

        // when then
        resolver.resolveAllOneOnOneConversations().shouldFail()
    }

    @Test
    fun givenProtocolResolvesToMLS_whenResolveOneOnOneConversationWithUser_thenMigrateToMLS() = runTest {
        // given
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(Either.Right(SupportedProtocol.MLS))
            withMigrateToMLSReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUser(OTHER_USER, false).shouldSucceed()

        // then
        verify(arrangement.oneOnOneMigrator)
            .suspendFunction(arrangement.oneOnOneMigrator::migrateToMLS)
            .with(eq(OTHER_USER))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProtocolResolvesToProteus_whenResolveOneOnOneConversationWithUser_thenMigrateToProteus() = runTest {
        // given
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(Either.Right(SupportedProtocol.PROTEUS))
            withMigrateToProteusReturns(Either.Right(TestConversation.ID))
        }

        // when
        resolver.resolveOneOnOneConversationWithUser(OTHER_USER, false).shouldSucceed()

        // then
        verify(arrangement.oneOnOneMigrator)
            .suspendFunction(arrangement.oneOnOneMigrator::migrateToProteus)
            .with(eq(OTHER_USER))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenProtocolResolvesToOtherNeedToUpdate_whenResolveOneOnOneConversationWithUser_thenMigrateExistingToProteus() = runTest {
        // given
        val (arrangement, resolver) = arrange {
            withGetProtocolForUser(CoreFailure.NoCommonProtocolFound.OtherNeedToUpdate.left())
            withMigrateExistingToProteusReturns(TestConversation.ID.right())
        }

        // when
        resolver.resolveOneOnOneConversationWithUser(OTHER_USER, false).shouldSucceed()

        // then
        verify(arrangement.oneOnOneMigrator)
            .suspendFunction(arrangement.oneOnOneMigrator::migrateExistingProteus)
            .with(eq(OTHER_USER))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneProtocolSelectorArrangement by OneOnOneProtocolSelectorArrangementImpl(),
        OneOnOneMigratorArrangement by OneOnOneMigratorArrangementImpl(),
        IncrementalSyncRepositoryArrangement by IncrementalSyncRepositoryArrangementImpl() {
        fun arrange() = run {
            block()
            this@Arrangement to OneOnOneResolverImpl(
                userRepository = userRepository,
                oneOnOneProtocolSelector = oneOnOneProtocolSelector,
                oneOnOneMigrator = oneOnOneMigrator,
                incrementalSyncRepository = incrementalSyncRepository
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val OTHER_USER = TestUser.OTHER
    }
}
