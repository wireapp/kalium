/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.util.arrangement.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneMigratorArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneMigratorArrangementImpl
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangement
import com.wire.kalium.logic.util.arrangement.protocol.OneOnOneProtocolSelectorArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import io.mockative.eq
import io.mockative.given
import io.mockative.matchers.OneOfMatcher
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
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
        resolver.resolveOneOnOneConversationWithUser(OTHER_USER).shouldSucceed()

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
        resolver.resolveOneOnOneConversationWithUser(OTHER_USER).shouldSucceed()

        // then
        verify(arrangement.oneOnOneMigrator)
            .suspendFunction(arrangement.oneOnOneMigrator::migrateToProteus)
            .with(eq(OTHER_USER))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneProtocolSelectorArrangement by OneOnOneProtocolSelectorArrangementImpl(),
        OneOnOneMigratorArrangement by OneOnOneMigratorArrangementImpl()
    {
        fun arrange() = run {
            block()
            this@Arrangement to OneOnOneResolverImpl(
                userRepository = userRepository,
                oneOnOneProtocolSelector = oneOnOneProtocolSelector,
                oneOnOneMigrator = oneOnOneMigrator
            )
        }
    }

    private companion object {
        fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val OTHER_USER = TestUser.OTHER
    }
}
