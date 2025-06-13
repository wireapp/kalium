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
import com.wire.kalium.logic.util.arrangement.repository.AnalyticsRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.AnalyticsRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class GetAnalyticsContactsDataUseCaseTest {

    @BeforeTest
    fun setup() {
        coroutineScope = TestScope()
    }

    @Test
    fun givenNoTeamId_whenInvoke_thenOnlyContactsReturned() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)
        val contactsCachedCount = 3
        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = 3,
            isEnterprise = null,
            isTeamMember = false
        )
        val (arrangement, useCase) = Arrangement().arrange {
            withContactsAmountCached(contactsCachedCount.right())
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            coEvery { selfTeamIdProvider.invoke() }.returns((null as TeamId?).right())
            withContactsAmountCached(expected.contactsSize!!.right())
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.getContactsAmountCached() }.wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }.wasNotInvoked()
    }

    @Test
    fun givenNoTeamIdAndNoCachedContactsAmount_whenInvoke_thenOnlyContactsReturned() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = 3,
            isEnterprise = null,
            isTeamMember = false
        )
        val (arrangement, useCase) = Arrangement().arrange {
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            coEvery { selfTeamIdProvider.invoke() }.returns((null as TeamId?).right())
            withContactsAmountCached(StorageFailure.DataNotFound.left())
            withCountContactsAmount(expected.contactsSize!!.right())
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }.wasNotInvoked()
    }

    @Test
    fun givenTeamIdAndTeamIsBig_whenInvoke_thenTeamDataReturned() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val expected = AnalyticsContactsData(
            teamId = SELF_TEAM_ID.value,
            teamSize = 12,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )
        val (arrangement, useCase) = Arrangement().arrange {
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
    }

    @Test
    fun givenTeamIdAndAndNoTeamSizeCached_whenInvoke_thenTeamDataReturned() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val expected = AnalyticsContactsData(
            teamId = SELF_TEAM_ID.value,
            teamSize = 12,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )
        val (arrangement, useCase) = Arrangement().arrange {
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(StorageFailure.DataNotFound.left())
            withCountTeamMembersAmount(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getContactsAmountCached() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasInvoked(exactly = 1)
    }

    @Test
    fun givenTeamIdAndTeamIsSmall_whenInvoke_thenNoTeamDataReturned() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )

        val (arrangement, useCase) = Arrangement().arrange {
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(5.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasNotInvoked()
    }

    @Test
    fun givenNoCacheUpdateDateAndUserInTeam_whenInvoked_thenUpdateTeamSizeCalled() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = StorageFailure.DataNotFound.left()

        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withLastContactsDateUpdateDate(lastUpdateDate)
            withConferenceCallingEnabled(false)
            withCountContactsAmount(112.right())
            withTeamMembersAmountCached(3.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase.invoke(currentTime)

        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
            .wasInvoked(exactly = 1)
    }

    @Test
    fun givenCacheUpdateDateLongTimeAgoAndUseInTeam_whenInvoked_thenUpdateTeamSizeCalled() =
        coroutineScope.runTest {
            val currentTime = Instant.parse("2021-01-01T00:00:00Z")
            val lastUpdateDate = currentTime.minus(8.days)

            val (arrangement, useCase) = Arrangement().arrange {
                withTeamMembersAmountCached(3.right())
                withConferenceCallingEnabled(false)
                withLastContactsDateUpdateDate(lastUpdateDate.right())
                coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke(currentTime)
            advanceUntilIdle()

            coVerify { arrangement.analyticsRepository.countContactsAmount() }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
                .wasInvoked(exactly = 1)
        }

    @Test
    fun givenCacheUpdateDateLongTimeAgoAnsUserNotInTeam_whenInvoke_thenUpdateContactsCalled() =
        coroutineScope.runTest {
            val currentTime = Instant.parse("2021-01-10T00:00:00Z")
            val lastUpdateDate = Instant.parse("2021-01-01T00:00:00Z")
            val contactsCachedCount = 3

            val (arrangement, useCase) = Arrangement().arrange {
                withContactsAmountCached(contactsCachedCount.right())
                withLastContactsDateUpdateDate(lastUpdateDate.right())
                coEvery { selfTeamIdProvider.invoke() }.returns(null.right())
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke(currentTime)
            coroutineScope.advanceUntilIdle()

            coVerify { arrangement.analyticsRepository.countContactsAmount() }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
                .wasInvoked(exactly = 1)
            coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
                .wasNotInvoked()
            coVerify { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
                .wasInvoked(exactly = 1)
        }

    @Test
    fun givenCacheUpdateDateNotLongTimeAgo_whenInvoked_thenUpdateNotCalled() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val (arrangement, useCase) = Arrangement().arrange {
            withTeamMembersAmountCached(3.right())
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            withConferenceCallingEnabled(true)
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withCountContactsAmount(112.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase(currentTime)
        advanceUntilIdle()

        coVerify { arrangement.analyticsRepository.countContactsAmount() }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            .wasNotInvoked()
        coVerify { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
            .wasNotInvoked()
    }

    companion object {
        val SELF_TEAM_ID = TeamId("team_id")
        lateinit var coroutineScope: TestScope
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        AnalyticsRepositoryArrangement by AnalyticsRepositoryArrangementImpl() {

        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        private val useCase: GetAnalyticsContactsDataUseCase = GetAnalyticsContactsDataUseCase(
            selfTeamIdProvider = selfTeamIdProvider,
            userConfigRepository = userConfigRepository,
            analyticsRepository = analyticsRepository,
            coroutineScope = coroutineScope

        )

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetAnalyticsContactsDataUseCase> {
            block()
            return this to useCase
        }
    }
}
