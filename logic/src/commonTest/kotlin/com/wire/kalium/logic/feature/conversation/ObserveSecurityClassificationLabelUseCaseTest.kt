package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.configuration.ClassifiedDomainsStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveSecurityClassificationLabelUseCaseTest {

    @Test
    fun givenAConversationId_WhenNoClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest {
        val (_, getConversationClassifiedType) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .withParticipantsResponseDomains(listOf("wire.com", "bella.com"))
            .arrange()

        val result = getConversationClassifiedType(TestConversation.ID)

        assertEquals(SecurityClassificationType.NONE, result.firstOrNull())
    }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndAllMembersInSameDomainAndTrusted_ThenClassificationIsClassified() =
        runTest {
            val (_, getConversationClassifiedType) = Arrangement()
                .withGettingClassifiedDomains()
                .withParticipantsResponseDomains(listOf("wire.com", "bella.com"))
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.CLASSIFIED, result.firstOrNull())
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndSomeMembersNotInSameDomain_ThenClassificationIsNotClassified() =
        runTest {
            val (_, getConversationClassifiedType) = Arrangement()
                .withGettingClassifiedDomains()
                .withParticipantsResponseDomains(listOf("anta.com", "bella.com"))
                .arrange()

            val result = getConversationClassifiedType(TestConversation.ID)

            assertEquals(SecurityClassificationType.NOT_CLASSIFIED, result.firstOrNull())
        }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val getSecurityClassificationType = ObserveSecurityClassificationLabelUseCaseImpl(
            selfUserId, conversationRepository, userConfigRepository
        )

        fun withGettingClassifiedDomainsDisabled() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(emptyFlow())
        }

        fun withGettingClassifiedDomains() = apply {
            given(userConfigRepository)
                .suspendFunction(userConfigRepository::getClassifiedDomainsStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(ClassifiedDomainsStatus(true, listOf("wire.com", "bella.com")))))
        }

        fun withParticipantsResponseDomains(domains: List<String>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(flowOf(stubUserIds(domains)))
        }

        private fun stubUserIds(domains: List<String>) =
            domains.map { domain -> Conversation.Member(UserId(uuid4().toString(), domain), Conversation.Member.Role.Member) }

        fun arrange() = this to getSecurityClassificationType

        companion object {
            val selfUserId = UserId("someValue", "wire.com")
        }
    }

}
