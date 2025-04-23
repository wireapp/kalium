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

package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ClassifiedDomainsStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveOtherUserSecurityClassificationLabelUseCaseTest {

    @Test
    fun givenAOtherUserId_WhenNoClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest(dispatcher.io) {
        val (_, observeOtherUserSecurityClassificationLabel) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .arrange()

        observeOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID).test {
            assertEquals(SecurityClassificationType.NONE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenASelfUserId_WhenClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest(dispatcher.io) {
        val (_, observeOtherUserSecurityClassificationLabel) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .arrange()

        observeOtherUserSecurityClassificationLabel(TestUser.SELF.id).test {
            assertEquals(SecurityClassificationType.NONE, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAOtherUserId_WhenClassifiedFeatureFlagEnabledAndOtherUserInSameDomainAndTrusted_ThenClassificationIsClassified() =
        runTest(dispatcher.io) {
            val (_, observeOtherUserSecurityClassificationLabel) = Arrangement()
                .withGettingClassifiedDomains()
                .arrange()

            observeOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID.copy(domain = "bella.com")).test {
                assertEquals(SecurityClassificationType.CLASSIFIED, awaitItem())
                awaitComplete()
            }
        }

    @Test
    fun givenAOtherUserId_WhenClassifiedFeatureFlagEnabledAndOtherUserNotInSameDomain_ThenClassificationIsNotClassified() =
        runTest(dispatcher.io) {
            val (_, observeOtherUserSecurityClassificationLabel) = Arrangement()
                .withGettingClassifiedDomains()
                .arrange()

            observeOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID.copy(domain = "not-trusted.com")).test {
                assertEquals(SecurityClassificationType.NOT_CLASSIFIED, awaitItem())
                awaitComplete()
            }

        }

    private class Arrangement {
                val userConfigRepository = mock(UserConfigRepository::class)

        fun withGettingClassifiedDomainsDisabled() = apply {
            every {
                userConfigRepository.getClassifiedDomainsStatus()
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withGettingClassifiedDomains() = apply {
            every {
                userConfigRepository.getClassifiedDomainsStatus()
            }.returns(flowOf(Either.Right(ClassifiedDomainsStatus(true, listOf("wire.com", "bella.com")))))
        }

        fun arrange() = this to ObserveOtherUserSecurityClassificationLabelUseCaseImpl(
            userConfigRepository,
            TestUser.SELF.id,
            dispatcher
        )
    }

    companion object {
        val dispatcher = TestKaliumDispatcher
    }
}
