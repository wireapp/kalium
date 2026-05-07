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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.analytics.AnalyticsRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            withSelfTeamId(null)
            withContactsAmountCached(expected.contactsSize!!.right())
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.getContactsAmountCached() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.getTeamMembersAmountCached() }
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
            withSelfTeamId(null)
            withContactsAmountCached(StorageFailure.DataNotFound.left())
            withCountContactsAmount(expected.contactsSize!!.right())
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.getTeamMembersAmountCached() }
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
            withSelfTeamId(SELF_TEAM_ID)
            withTeamMembersAmountCached(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.getTeamMembersAmountCached() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
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
            withSelfTeamId(SELF_TEAM_ID)
            withTeamMembersAmountCached(StorageFailure.DataNotFound.left())
            withCountTeamMembersAmount(expected.teamSize!!.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.getContactsAmountCached() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.getTeamMembersAmountCached() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
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
            withSelfTeamId(SELF_TEAM_ID)
            withTeamMembersAmountCached(5.right())
            withConferenceCallingEnabled(expected.isEnterprise!!)
        }

        val result = useCase(currentTime)

        assertEquals(expected, result)
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.getTeamMembersAmountCached() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
    }

    @Test
    fun givenNoCacheUpdateDateAndUserInTeam_whenInvoked_thenUpdateTeamSizeCalled() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = StorageFailure.DataNotFound.left()

        val (arrangement, useCase) = Arrangement().arrange {
            withSelfTeamId(SELF_TEAM_ID)
            withLastContactsDateUpdateDate(lastUpdateDate)
            withConferenceCallingEnabled(false)
            withCountContactsAmount(112.right())
            withTeamMembersAmountCached(3.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase.invoke(currentTime)

        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setContactsAmountCached(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
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
                withSelfTeamId(SELF_TEAM_ID)
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke(currentTime)
            advanceUntilIdle()

            verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.analyticsRepository.setLastContactsDateUpdateDate(any())
            }
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
                withSelfTeamId(null)
                withCountContactsAmount(112.right())
                withCountTeamMembersAmount(12.right())
            }

            useCase.invoke(currentTime)
            coroutineScope.advanceUntilIdle()

            verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.countContactsAmount() }
            verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
            verifySuspend(VerifyMode.exactly(1)) { arrangement.analyticsRepository.setContactsAmountCached(any()) }
            verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.analyticsRepository.setLastContactsDateUpdateDate(any())
            }
        }

    @Test
    fun givenCacheUpdateDateNotLongTimeAgo_whenInvoked_thenUpdateNotCalled() = coroutineScope.runTest {
        val currentTime = Instant.parse("2021-01-01T00:00:00Z")
        val lastUpdateDate = currentTime.minus(1.days)

        val (arrangement, useCase) = Arrangement().arrange {
            withTeamMembersAmountCached(3.right())
            withLastContactsDateUpdateDate(lastUpdateDate.right())
            withConferenceCallingEnabled(true)
            withSelfTeamId(SELF_TEAM_ID)
            withCountContactsAmount(112.right())
            withCountTeamMembersAmount(12.right())
        }

        useCase(currentTime)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countContactsAmount() }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.countTeamMembersAmount(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setContactsAmountCached(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setTeamMembersAmountCached(any()) }
        verifySuspend(VerifyMode.not) { arrangement.analyticsRepository.setLastContactsDateUpdateDate(any()) }
    }

    companion object {
        val SELF_TEAM_ID = TeamId("team_id")
        lateinit var coroutineScope: TestScope
    }

    private class Arrangement {
        val userConfigRepository = mock<UserConfigRepository>(mode = MockMode.autoUnit)
        val analyticsRepository = mock<AnalyticsRepository>(mode = MockMode.autoUnit)
        val selfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)

        private val useCase: GetAnalyticsContactsDataUseCase = GetAnalyticsContactsDataUseCase(
            selfTeamIdProvider = selfTeamIdProvider,
            userConfigRepository = userConfigRepository,
            analyticsRepository = analyticsRepository,
            coroutineScope = coroutineScope

        )

        suspend fun withSelfTeamId(teamId: TeamId?) = apply {
            everySuspend {
                selfTeamIdProvider.invoke()
            } returns teamId.right()
        }

        suspend fun withContactsAmountCached(result: Either<StorageFailure, Int>) = apply {
            everySuspend {
                analyticsRepository.getContactsAmountCached()
            } returns result
        }

        suspend fun withCountContactsAmount(result: Either<StorageFailure, Int>) = apply {
            everySuspend {
                analyticsRepository.countContactsAmount()
            } returns result
        }

        suspend fun withTeamMembersAmountCached(result: Either<StorageFailure, Int>) = apply {
            everySuspend {
                analyticsRepository.getTeamMembersAmountCached()
            } returns result
        }

        suspend fun withCountTeamMembersAmount(result: Either<StorageFailure, Int>) = apply {
            everySuspend {
                analyticsRepository.countTeamMembersAmount(any())
            } returns result
        }

        suspend fun withLastContactsDateUpdateDate(result: Either<StorageFailure, Instant>) = apply {
            everySuspend {
                analyticsRepository.getLastContactsDateUpdateDate()
            } returns result
        }

        suspend fun withConferenceCallingEnabled(result: Boolean) = apply {
            everySuspend {
                userConfigRepository.isConferenceCallingEnabled()
            } returns result.right()
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, GetAnalyticsContactsDataUseCase> {
            block()
            return this to useCase
        }
    }
}
