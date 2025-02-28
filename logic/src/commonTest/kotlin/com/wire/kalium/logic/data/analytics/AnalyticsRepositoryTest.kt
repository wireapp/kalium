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
package com.wire.kalium.logic.data.analytics

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsRepositoryTest {


    @Test
    fun givenCachedContactsAmountAbsent_whenGettingContactsAmountCached_thenShouldPropagateError() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withGetMetaDataDaoValue(AnalyticsDataSource.CONTACTS_AMOUNT_KEY, null)
            .arrange()
        // when
        val result = userRepository.getContactsAmountCached()
        // then
        result.shouldFail()
        coVerify {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.CONTACTS_AMOUNT_KEY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCachedContactsAmount_whenGettingContactsAmountCached_thenShouldPropagateResult() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withGetMetaDataDaoValue(AnalyticsDataSource.CONTACTS_AMOUNT_KEY, "12")
            .arrange()
        // when
        val result = userRepository.getContactsAmountCached()
        // then
        result.shouldSucceed { assertEquals(12, it) }
        coVerify {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.CONTACTS_AMOUNT_KEY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCachedTeamSizeAbsent_whenGettingTeamSizeCached_thenShouldPropagateError() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withGetMetaDataDaoValue(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY, null)
            .arrange()
        // when
        val result = userRepository.getTeamMembersAmountCached()
        // then
        result.shouldFail()
        coVerify {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCachedTeamSize_whenGettingTeamSizeCached_thenShouldPropagateResult() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withGetMetaDataDaoValue(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY, "12")
            .arrange()
        // when
        val result = userRepository.getTeamMembersAmountCached()
        // then
        result.shouldSucceed { assertEquals(12, it) }
        coVerify {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCountingContactsSucceed_whenCountingContactsCalled_thenShouldPropagateResult() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withCountContactsAmountResult(12)
            .arrange()
        // when
        val result = userRepository.countContactsAmount()
        // then
        result.shouldSucceed { assertEquals(12, it) }
        coVerify {
            arrangement.userDAO.countContactsAmount(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenCountingTeamSizeSucceed_whenCountingTeamSize_thenShouldPropagateResult() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withCountTeamMembersAmount(12)
            .arrange()
        // when
        val result = userRepository.countTeamMembersAmount(TeamId("teamId"))
        // then
        result.shouldSucceed { assertEquals(12, it) }
        coVerify {
            arrangement.userDAO.countTeamMembersAmount(any(), any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userDAO = mock(UserDAO::class)

        @Mock
        val clientDAO = mock(ClientDAO::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val metadataDAO: MetadataDAO = mock(MetadataDAO::class)

        val analyticsRepository: AnalyticsRepository by lazy {
            AnalyticsDataSource(
                userDAO = userDAO,
                selfTeamIdProvider = selfTeamIdProvider,
                selfUserId = TestUser.USER_ID,
                metadataDAO = metadataDAO,
            )
        }

        suspend fun withGetMetaDataDaoValue(key: String, result: String?) = apply {
            coEvery { metadataDAO.valueByKey(eq(key)) }.returns(result)
        }

        suspend fun withCountContactsAmountResult(result: Int) = apply {
            coEvery { userDAO.countContactsAmount(any()) }.returns(result)
        }

        suspend fun withCountTeamMembersAmount(result: Int) = apply {
            coEvery { userDAO.countTeamMembersAmount(any(), any()) }.returns(result)
        }

        suspend inline fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, AnalyticsRepository> {
            coEvery {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(TestUser.DETAILS_ENTITY))

            coEvery {
                selfTeamIdProvider()
            }.returns(Either.Right(TestTeam.TEAM_ID))
            coEvery { metadataDAO.insertValue(any(), any()) }.returns(Unit)
            apply(block)
            return this to analyticsRepository
        }

    }
}
