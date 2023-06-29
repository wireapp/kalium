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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ClassifiedDomainsStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSecurityClassificationLabelUseCaseTest {

    @Test
    fun givenAConversationId_WhenNoClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest {
        val (_, getConversationClassifiedType) = Arrangement()
            .withObserveConversationSuccess("wire.com")
            .withGettingClassifiedDomainsDisabled()
            .withParticipantsResponseDomains(listOf("wire.com", "bella.com"))
            .arrange()

        val result = getConversationClassifiedType(TestConversation.ID)

        assertEquals(SecurityClassificationType.NONE, result.firstOrNull())
    }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndAllMembersInSameDomainAndTrusted_ThenClassificationIsClassified() =
        runTest {
            val classifiedDomains = listOf("wire.com", "bella.com")
            val memberDomains = listOf("wire.com", "bella.com")
            val (_, getConversationClassifiedType) = Arrangement()
                .withObserveConversationSuccess("wire.com")
                .withGettingClassifiedDomains(classifiedDomains)
                .withParticipantsResponseDomains(memberDomains)
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.CLASSIFIED, result.firstOrNull())
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndSomeMembersNotInSameDomain_ThenClassificationIsNotClassified() =
        runTest {
            val classifiedDomains = listOf("wire.com", "bella.com")
            val memberDomains = listOf("anta.com", "bella.com")
            val (_, getConversationClassifiedType) = Arrangement()
                .withObserveConversationSuccess("wire.com")
                .withGettingClassifiedDomains(classifiedDomains)
                .withParticipantsResponseDomains(memberDomains)
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.NOT_CLASSIFIED, result.firstOrNull())
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndConversationIsNotClassified_ThenClassificationIsNotClassified() =
        runTest {
            val classifiedDomains = listOf("wire.com", "bella.com")
            val memberDomains = listOf("wire.com", "bella.com")
            val (_, getConversationClassifiedType) = Arrangement()
                .withObserveConversationSuccess("challa.com")
                .withGettingClassifiedDomains(classifiedDomains)
                .withParticipantsResponseDomains(memberDomains)
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.NOT_CLASSIFIED, result.firstOrNull())
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndConversationReturnsError_ThenClassificationIsNone() =
        runTest {
            val classifiedDomains = listOf("wire.com", "bella.com")
            val memberDomains = listOf("wire.com", "bella.com")
            val (_, getConversationClassifiedType) = Arrangement()
                .withObserveConversationError()
                .withGettingClassifiedDomains(classifiedDomains)
                .withParticipantsResponseDomains(memberDomains)
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.NONE, result.firstOrNull())
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndAllMembersAreTemporary_ThenClassificationIsNotClassified() =
        runTest {
            val classifiedDomains = listOf("wire.com", "bella.com")
            val memberDomains = listOf("wire.com", "bella.com")
            val (_, getConversationClassifiedType) = Arrangement()
                .withObserveConversationSuccess("wire.com")
                .withGettingClassifiedDomains(classifiedDomains)
                .withParticipantsResponseDomains(memberDomains, "2050-01-01T00:00:00.000Z".toInstant())
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.NOT_CLASSIFIED, result.firstOrNull())
        }

    private class Arrangement {
        @Mock
        val observeConversationMembersUseCase = mock(classOf<ObserveConversationMembersUseCase>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val getSecurityClassificationType = ObserveSecurityClassificationLabelUseCaseImpl(
            observeConversationMembersUseCase, conversationRepository, userConfigRepository
        )

        fun withGettingClassifiedDomainsDisabled() = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(emptyFlow())
        }

        fun withGettingClassifiedDomains(domains: List<String>) = apply {
            given(userConfigRepository)
                .function(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(ClassifiedDomainsStatus(true, domains))))
        }

        fun withParticipantsResponseDomains(domains: List<String>, expiresAt: Instant? = null) = apply {
            given(observeConversationMembersUseCase)
                .suspendFunction(observeConversationMembersUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(flowOf(stubUserIds(domains, expiresAt)))
        }

        fun withObserveConversationSuccess(conversationDomain: String) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeById)
                .whenInvokedWith(any())
                .thenReturn(
                    flowOf(
                        Either.Right(
                            TestConversation.CONVERSATION.copy(
                                TestConversation.ID.copy(
                                    domain = conversationDomain
                                )
                            )
                        )
                    )
                )
        }

        fun withObserveConversationError() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeById)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Left(StorageFailure.Generic(Throwable("error")))))
        }

        private fun stubUserIds(domains: List<String>, expiresAt: Instant?) =
            domains.map { domain ->
                MemberDetails(
                    TestUser.OTHER.copy(
                        UserId(uuid4().toString(), domain),
                        expiresAt = expiresAt
                    ), Conversation.Member.Role.Member
                )
            }

        fun arrange() = this to getSecurityClassificationType
    }

}
