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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class AnalyticsRepositoryTest {

    @Test
    fun givenCachedContactsAmountAbsent_whenGettingContactsAmountCached_thenShouldPropagateError() =
        runTest {
            // given
            val (arrangement, userRepository) = Arrangement()
                .withGetMetaDataDaoValue(AnalyticsDataSource.CONTACTS_AMOUNT_KEY, null)
                .arrange()
            // when
            val result = userRepository.getContactsAmountCached()
            // then
            result.shouldFail()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.metadataDAO.valueByKey(AnalyticsDataSource.CONTACTS_AMOUNT_KEY)
            }
        }

    @Test
    fun givenCachedContactsAmount_whenGettingContactsAmountCached_thenShouldPropagateResult() =
        runTest {
            // given
            val (arrangement, userRepository) = Arrangement()
                .withGetMetaDataDaoValue(AnalyticsDataSource.CONTACTS_AMOUNT_KEY, "12")
                .arrange()
            // when
            val result = userRepository.getContactsAmountCached()
            // then
            result.shouldSucceed { assertEquals(12, it) }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.metadataDAO.valueByKey(AnalyticsDataSource.CONTACTS_AMOUNT_KEY)
            }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY)
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.metadataDAO.valueByKey(AnalyticsDataSource.TEAM_MEMBERS_AMOUNT_KEY)
        }
    }

    @Test
    fun givenCountingContactsSucceed_whenCountingContactsCalled_thenShouldPropagateResult() =
        runTest {
            // given
            val (arrangement, userRepository) = Arrangement()
                .withCountContactsAmountResult(12)
                .arrange()
            // when
            val result = userRepository.countContactsAmount()
            // then
            result.shouldSucceed { assertEquals(12, it) }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userDAO.countContactsAmount(any())
            }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.countTeamMembersAmount(any())
        }
    }

    private class Arrangement {

        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        val selfTeamIdProvider: SelfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)
        val metadataDAO: MetadataDAO = mock<MetadataDAO>(mode = MockMode.autoUnit)

        val analyticsRepository: AnalyticsRepository by lazy {
            AnalyticsDataSource(
                userDAO = userDAO,
                selfUserId = TestUser.USER_ID,
                metadataDAO = metadataDAO,
            )
        }

        suspend fun withGetMetaDataDaoValue(key: String, result: String?) = apply {
            everySuspend { metadataDAO.valueByKey(eq(key)) }.returns(result)
        }

        suspend fun withCountContactsAmountResult(result: Int) = apply {
            everySuspend { userDAO.countContactsAmount(any()) }.returns(result)
        }

        suspend fun withCountTeamMembersAmount(result: Int) = apply {
            everySuspend { userDAO.countTeamMembersAmount(any()) }.returns(result)
        }

        suspend inline fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, AnalyticsRepository> {
            everySuspend {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(TestUser.DETAILS_ENTITY))

            everySuspend {
                selfTeamIdProvider()
            }.returns(Either.Right(TestTeam.TEAM_ID))
            everySuspend { metadataDAO.insertValue(any(), any()) }.returns(Unit)
            apply(block)
            return this to analyticsRepository
        }
    }
}
