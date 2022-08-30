package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.ClassifiedDomainsStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class GetConversationClassifiedTypeUseCaseTest {

    @Test
    fun givenAConversationId_WhenNoClassifiedFeatureFlagEnabled_ThenClassificationIsNone() = runTest {
        val (_, getConversationClassifiedType) = Arrangement()
            .withGettingClassifiedDomainsDisabled()
            .arrange()

        val result: ClassifiedTypeResult = getConversationClassifiedType(TestConversation.ID)

        assertIs<ClassifiedTypeResult.Success>(result)
        assertEquals(ClassifiedType.NONE, result.classifiedType)
    }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndAllMembersInSameDomainAndTrusted_ThenClassificationIsClassified() =
        runTest {
            val (_, getConversationClassifiedType) = Arrangement()
                .withGettingClassifiedDomains()
                .withParticipantsResponseDomains(listOf("wire.com", "bella.com"))
                .arrange()

            val result: ClassifiedTypeResult = getConversationClassifiedType(TestConversation.ID)

            assertIs<ClassifiedTypeResult.Success>(result)
            assertEquals(ClassifiedType.CLASSIFIED, result.classifiedType)
        }

    @Test
    fun givenAConversationId_WhenClassifiedFeatureFlagEnabledAndSomeMembersNotInSameDomain_ThenClassificationIsNotClassified() =
        runTest {
            val (_, getConversationClassifiedType) = Arrangement()
                .withGettingClassifiedDomains()
                .withParticipantsResponseDomains(listOf("anta.com", "bella.com"))
                .arrange()

            val result: ClassifiedTypeResult = getConversationClassifiedType(TestConversation.ID)

            assertIs<ClassifiedTypeResult.Success>(result)
            assertEquals(ClassifiedType.NOT_CLASSIFIED, result.classifiedType)
        }

    @Test
    fun givenAConversationId_WhenClassifiedFlagThrowsAnError_ThenResultIsFailure() =
        runTest {
            val (_, getConversationClassifiedType) = Arrangement()
                .withGettingClassifiedDomains()
                .withParticipantsResponseFails()
                .arrange()

            val result: ClassifiedTypeResult = getConversationClassifiedType(TestConversation.ID)

            assertIs<ClassifiedTypeResult.Failure>(result)
        }


    private class Arrangement {
        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val userConfigRepository = mock(classOf<UserConfigRepository>())

        private val getConversationClassifiedType = GetConversationClassifiedTypeUseCaseImpl(
            selfUserId, conversationRepository, userConfigRepository
        )

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

        fun withParticipantsResponseDomains(domains: List<String>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(stubUserIds(domains)))
        }

        fun withParticipantsResponseFails() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationMembers)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
        }

        private fun stubUserIds(domains: List<String>) = domains.map { domain -> UserId(uuid4().toString(), domain) }

        fun arrange() = this to getConversationClassifiedType


        companion object {
            val selfUserId = UserId("someValue", "wire.com")
            val classifiedDomainsStatus = ClassifiedDomainsStatus(true, listOf("wire.com", "bella.com"))
        }
    }

}
