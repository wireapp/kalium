/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.analytics

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.util.arrangement.repository.AnalyticsRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AnalyticsRepositoryArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.time.Duration.Companion.days

class UpdateContactsAmountsCacheUseCaseTest {

    @Test
    fun givenNoCacheUpdateDateAndUserInTeam_whenInvoked_thenUpdateTeamSizeCalled() = runTest {
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withLastContactsDateUpdateDate(StorageFailure.DataNotFound.left())
            withCountContactsAmount(112.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase.invoke()

        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.setContactsAmountCachingDate(any()) }
            .wasInvoked(exactly = 1)
    }

    @Test
    fun givenCacheUpdateDateLongTimeAgoAndUseInTeam_whenInvoked_thenUpdateTeamSizeCalled() =
        runTest {
            val (arrangement, useCase) = Arrangement().arrange {
                coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
                withLastContactsDateUpdateDate(Clock.System.now().minus(10.days).right())
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke()

            coVerify { arrangement.analyticsRepository.countContactsAmount() }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setContactsAmountCachingDate(any()) }
                .wasInvoked(exactly = 1)
        }

    @Test
    fun givenCacheUpdateDateLongTimeAgoAnsUserNotInTeam_whenInvoke_thenUpdateContactsCalled() =
        runTest {
            val (arrangement, useCase) = Arrangement().arrange {
                coEvery { selfTeamIdProvider.invoke() }.returns(null.right())
                withLastContactsDateUpdateDate(Clock.System.now().minus(10.days).right())
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke()

            coVerify { arrangement.analyticsRepository.countContactsAmount() }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setContactsAmountCachingDate(any()) }
                .wasInvoked(exactly = 1)
        }

    @Test
    fun givenCacheUpdateDateNotLongTimeAgo_whenInvoked_thenUpdateNotCalled() = runTest {
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withLastContactsDateUpdateDate(Clock.System.now().minus(1.days).right())
            withCountContactsAmount(112.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase.invoke()

        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setContactsAmountCachingDate(any()) }
            .wasNotInvoked()
    }

    private companion object {
        val SELF_TEAM_ID = TeamId("team_id")
    }

    private class Arrangement :
        AnalyticsRepositoryArrangement by AnalyticsRepositoryArrangementImpl() {

        @Mock
        val slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        init {
            every { slowSyncRepository.slowSyncStatus }
                .returns(MutableStateFlow(SlowSyncStatus.Complete).asStateFlow())
        }

        private val useCase: UpdateContactsAmountsCacheUseCase =
            UpdateContactsAmountsCacheUseCaseImpl(
                selfTeamIdProvider = selfTeamIdProvider,
                slowSyncRepository = slowSyncRepository,
                analyticsRepository = analyticsRepository,
            )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, UpdateContactsAmountsCacheUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
