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
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserConfigRepositoryArrangementImpl
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
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAnalyticsContactsDataUseCaseTest {

    @Test
    fun givenNoTeamId_whenInvoke_thenOnlyContactsReturned() = runTest {
        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = 3,
            isEnterprise = null,
            isTeamMember = false
        )
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns((null as TeamId?).right())
            withContactsAmountCached(expected.contactsSize!!.right())
        }

        val result = useCase()

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }.wasNotInvoked()
    }

    @Test
    fun givenNoTeamIdAndNoCachedContactsAmount_whenInvoke_thenOnlyContactsReturned() = runTest {
        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = 3,
            isEnterprise = null,
            isTeamMember = false
        )
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns((null as TeamId?).right())
            withContactsAmountCached(StorageFailure.DataNotFound.left())
            withCountContactsAmount(expected.contactsSize!!.right())
        }

        val result = useCase()

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }.wasNotInvoked()
    }

    @Test
    fun givenTeamIdAndTeamIsBig_whenInvoke_thenTeamDataReturned() = runTest {
        val expected = AnalyticsContactsData(
            teamId = SELF_TEAM_ID.value,
            teamSize = 12,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase()

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }.wasNotInvoked()
    }

    @Test
    fun givenTeamIdAndAndNoTeamSizeCached_whenInvoke_thenTeamDataReturned() = runTest {
        val expected = AnalyticsContactsData(
            teamId = SELF_TEAM_ID.value,
            teamSize = 12,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(StorageFailure.DataNotFound.left())
            withCountTeamMembersAmount(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase()

        assertEquals(expected, result)
        coVerify { arrangement.analyticsRepository.countContactsAmount() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getContactsAmountCached() }.wasNotInvoked()
        coVerify { arrangement.analyticsRepository.getTeamMembersAmountCached() }
            .wasInvoked(exactly = 1)
        coVerify { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            .wasInvoked(exactly = 1)
    }

    @Test
    fun givenTeamIdAndTeamIsSmall_whenInvoke_thenNoTeamDataReturned() = runTest {
        val expected = AnalyticsContactsData(
            teamId = null,
            teamSize = null,
            contactsSize = null,
            isEnterprise = true,
            isTeamMember = true
        )
        val (arrangement, useCase) = Arrangement().arrange {
            coEvery { selfTeamIdProvider.invoke() }.returns(SELF_TEAM_ID.right())
            withTeamMembersAmountCached(5.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase()

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

    private companion object {
        val SELF_TEAM_ID = TeamId("team_id")
    }

    private class Arrangement : UserConfigRepositoryArrangement by UserConfigRepositoryArrangementImpl(),
        AnalyticsRepositoryArrangement by AnalyticsRepositoryArrangementImpl() {

        @Mock
        val slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        init {
            every { slowSyncRepository.slowSyncStatus }
                .returns(MutableStateFlow(SlowSyncStatus.Complete).asStateFlow())
        }

        private val useCase: GetAnalyticsContactsDataUseCase = GetAnalyticsContactsDataUseCaseImpl(
            selfTeamIdProvider = selfTeamIdProvider,
            userConfigRepository = userConfigRepository,
            slowSyncRepository = slowSyncRepository,
            analyticsRepository = analyticsRepository,
        )

        fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetAnalyticsContactsDataUseCase> {
            runBlocking { block() }
            return this to useCase
        }
    }
}
