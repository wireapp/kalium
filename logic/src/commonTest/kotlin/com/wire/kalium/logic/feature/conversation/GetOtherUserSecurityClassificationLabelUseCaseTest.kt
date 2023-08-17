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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.configuration.ClassifiedDomainsStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class GetOtherUserSecurityClassificationLabelUseCaseTest {

    @Test
    fun givenAOtherUserId_WhenNoClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest(dispatcher.io) {
        val (_, getOtherUserSecurityClassificationLabel) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .arrange()

        val result = getOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID)

        assertEquals(SecurityClassificationType.NONE, result)
    }

    @Test
    fun givenASelfUserId_WhenClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest(dispatcher.io) {
        val (_, getOtherUserSecurityClassificationLabel) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .arrange()

        val result = getOtherUserSecurityClassificationLabel(TestUser.SELF.id)

        assertEquals(SecurityClassificationType.NONE, result)
    }

    @Test
    fun givenAOtherUserId_WhenClassifiedFeatureFlagEnabledAndOtherUserInSameDomainAndTrusted_ThenClassificationIsClassified() =
        runTest(dispatcher.io) {
            val (_, getOtherUserSecurityClassificationLabel) = Arrangement()
                .withGettingClassifiedDomains()
                .arrange()

            val result = getOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID.copy(domain = "bella.com"))

            assertEquals(SecurityClassificationType.CLASSIFIED, result)
        }

    @Test
    fun givenAOtherUserId_WhenClassifiedFeatureFlagEnabledAndOtherUserNotInSameDomain_ThenClassificationIsNotClassified() =
        runTest(dispatcher.io) {
            val (_, getOtherUserSecurityClassificationLabel) = Arrangement()
                .withGettingClassifiedDomains()
                .arrange()

            val result = getOtherUserSecurityClassificationLabel(TestUser.OTHER_USER_ID.copy(domain = "not-trusted.com"))

            assertEquals(SecurityClassificationType.NOT_CLASSIFIED, result)
        }

    private class Arrangement {
        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        fun withGettingClassifiedDomainsDisabled() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(emptyFlow())
        }

        fun withGettingClassifiedDomains() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(ClassifiedDomainsStatus(true, listOf("wire.com", "bella.com")))))
        }

        fun arrange() = this to GetOtherUserSecurityClassificationLabelUseCaseImpl(
            userConfigRepository,
            TestUser.SELF.id,
            dispatcher
        )
    }

    companion object {
        val dispatcher = TestKaliumDispatcher
    }
}
