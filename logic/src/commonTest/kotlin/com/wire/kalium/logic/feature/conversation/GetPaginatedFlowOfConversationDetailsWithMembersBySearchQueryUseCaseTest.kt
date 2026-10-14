/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithMembers
import com.wire.kalium.logic.data.conversation.ConversationMembersQueryConfig
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationRepositoryExtensions
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.SelfTeamIdProviderArrangementImpl
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame

class GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCaseTest {
    @Test
    fun restrictedSearchPassesSelfTeamAndDomainToRepository() = runTest {
        val page = PagingData.empty<ConversationDetailsWithMembers>()
        val pagingConfig = PagingConfig(20)
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).arrange {
            withTeamId(Either.Right(TeamId("team")))
            withSearchResults(page)
        }

        val result = useCase(
            ConversationMembersQueryConfig(searchQuery = "Alpha"), pagingConfig, onlySelfTeamAndDomain = true,
        ).first()
        assertSame(page, result)

        verifySuspend {
            arrangement.extensions.getPaginatedConversationDetailsWithMembersBySearchQuery(
                eq(ConversationMembersQueryConfig(searchQuery = "Alpha", onlyTeamId = "team", onlyDomain = TestUser.USER_ID.domain)),
                eq(pagingConfig), eq(0L), eq(true),
            )
        }
    }

    @Test
    fun unrestrictedSearchDoesNotResolveSelfTeamAndPreservesExplicitFilters() = runTest {
        val page = PagingData.empty<ConversationDetailsWithMembers>()
        val pagingConfig = PagingConfig(20)
        val config = ConversationMembersQueryConfig(onlyDomain = "domain")
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).arrange {
            withSearchResults(page)
        }

        assertSame(page, useCase(config, pagingConfig).first())

        verifySuspend {
            arrangement.extensions.getPaginatedConversationDetailsWithMembersBySearchQuery(
                eq(config), eq(pagingConfig), eq(0L), eq(true),
            )
        }
        verifySuspend(VerifyMode.not) { arrangement.selfTeamIdProvider() }
    }

    @Test
    fun restrictedSearchWithoutSelfTeamDoesNotRunAnUnrestrictedQuery() = runTest {
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher()).arrange {
            withTeamId(Either.Right(null))
        }

        useCase(ConversationMembersQueryConfig(), PagingConfig(20), onlySelfTeamAndDomain = true).first()

        verify(VerifyMode.not) { arrangement.repository.extensions }
    }

    private class Arrangement(private val dispatcher: KaliumDispatcher) :
        SelfTeamIdProviderArrangement by SelfTeamIdProviderArrangementImpl() {
        val repository = mock<ConversationRepository>()
        val extensions = mock<ConversationRepositoryExtensions>()

        init {
            every { repository.extensions } returns extensions
        }

        suspend fun withSearchResults(page: PagingData<ConversationDetailsWithMembers>) = apply {
            everySuspend {
                extensions.getPaginatedConversationDetailsWithMembersBySearchQuery(any(), any(), any(), any())
            } returns flowOf(page)
        }

        suspend fun arrange(
            block: suspend Arrangement.() -> Unit,
        ): Pair<Arrangement, GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase> {
            block()
            return this to GetPaginatedFlowOfConversationDetailsWithMembersBySearchQueryUseCase(
                dispatcher = dispatcher,
                conversationRepository = repository,
                selfTeamIdProvider = selfTeamIdProvider,
                selfUserId = TestUser.USER_ID,
            )
        }
    }
}
