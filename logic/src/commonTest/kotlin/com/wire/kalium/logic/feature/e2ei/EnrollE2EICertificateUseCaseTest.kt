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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.e2ei.E2EIAuthenticationRequest
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.E2EIRotationCheckpoint
import com.wire.kalium.logic.data.e2ei.E2EIRotationPhase
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIResult
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCase
import com.wire.kalium.logic.feature.e2ei.usecase.EnrollE2EIUseCaseImpl
import com.wire.kalium.logic.framework.TestConversation
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class EnrollE2EICertificateUseCaseTest {

    @Test
    fun givenAuthenticationCallback_whenEnrolling_thenCompletesAcquisitionAndRotationInOneInvocation() = runTest {
        val mixedGroup = GroupID("mixed-group")
        val conversations = listOf(
            TestConversation.MLS_CONVERSATION,
            TestConversation.MIXED_CONVERSATION.copy(
                protocol = TestConversation.MIXED_PROTOCOL_INFO.copy(groupId = mixedGroup)
            ),
            TestConversation.CONVERSATION
        )
        val (arrangement, useCase) = Arrangement(this).arrange {
            every { conversationRepository.observeConversationList() } returns flowOf(conversations)
        }
        var authenticationRequest: E2EIAuthenticationRequest? = null

        val result = useCase { request ->
            authenticationRequest = request
            ID_TOKEN
        }

        assertEquals(EnrollE2EIResult.Success(LEAF_CERTIFICATE), result)
        assertEquals(AUTHENTICATION_REQUEST, authenticationRequest)
        assertEquals(ID_TOKEN, arrangement.returnedIdToken)
        assertEquals(listOf(TestConversation.GROUP_ID, mixedGroup), arrangement.providedGroupIds)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.rotateKeysAndMigrateConversations(
                eq(arrangement.transactionProvider),
                eq(arrangement.checkpoint)
            )
        }
    }

    @Test
    fun givenNewClientEnrollment_whenInvoked_thenRefreshesSelfUserAndUsesRegistrationMode() = runTest {
        val (arrangement, useCase) = Arrangement(this).arrange()

        val result = useCase(isNewClientRegistration = true) { ID_TOKEN }

        assertIs<EnrollE2EIResult.Success>(result)
        assertEquals(true, arrangement.isNewClient)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.userRepository.fetchSelfUser() }
    }

    @Test
    fun givenE2EIIsDisabled_whenEnrolling_thenReturnsDisabled() = runTest {
        val (_, useCase) = Arrangement(this).arrange {
            everySuspend {
                e2eiRepository.acquireCredential(any(), any(), any())
            } returns E2EIFailure.Disabled.left()
        }

        val result = useCase { ID_TOKEN }

        assertIs<EnrollE2EIResult.Failure.E2EIDisabled>(result)
    }

    @Test
    fun givenRotationFails_whenEnrolling_thenReturnsFailureAndLeavesCheckpointForRetry() = runTest {
        val rotationFailure = E2EIFailure.Generic(IllegalStateException("rotation failed"))
        val (arrangement, useCase) = Arrangement(this).arrange {
            everySuspend {
                e2eiRepository.rotateKeysAndMigrateConversations(any(), any())
            } returns rotationFailure.left()
        }

        val result = useCase { ID_TOKEN }

        assertIs<EnrollE2EIResult.Failure.Generic>(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.e2eiRepository.acquireCredential(any(), any(), eq(false))
        }
    }

    private class Arrangement(private val testScope: TestScope) {
        val e2eiRepository = mock<E2EIRepository>(mode = MockMode.autoUnit)
        val userRepository = mock<UserRepository>()
        val conversationRepository = mock<ConversationRepository>()
        val transactionProvider = mock<CryptoTransactionProvider>()
        val checkpoint = CHECKPOINT

        var returnedIdToken: String? = null
        var providedGroupIds: List<GroupID>? = null
        var isNewClient: Boolean? = null

        suspend fun arrange(configure: suspend Arrangement.() -> Unit = {}): Pair<Arrangement, EnrollE2EIUseCase> {
            everySuspend { userRepository.fetchSelfUser() } returns Unit.right()
            every { conversationRepository.observeConversationList() } returns flowOf(emptyList())
            everySuspend {
                e2eiRepository.acquireCredential(any(), any(), any())
            } calls { invocation ->
                @Suppress("UNCHECKED_CAST")
                val authenticate = invocation.args[0] as suspend (E2EIAuthenticationRequest) -> String
                @Suppress("UNCHECKED_CAST")
                val groupIdListProvider = invocation.args[1] as suspend () -> List<GroupID>
                returnedIdToken = authenticate(AUTHENTICATION_REQUEST)
                providedGroupIds = groupIdListProvider()
                isNewClient = invocation.args[2] as Boolean
                checkpoint.right()
            }
            everySuspend {
                e2eiRepository.rotateKeysAndMigrateConversations(any(), any())
            } returns Unit.right()
            configure()

            return this to EnrollE2EIUseCaseImpl(
                e2EIRepository = e2eiRepository,
                userRepository = userRepository,
                coroutineScope = testScope,
                conversationRepository = conversationRepository,
                transactionProvider = transactionProvider
            )
        }
    }

    private companion object {
        const val IDP_URL = "https://idp.example.test/authorize"
        const val KEY_AUTH = "key-authorization"
        const val ACME_AUDIENCE = "wire-acme"
        const val ID_TOKEN = "signed-id-token"
        const val LEAF_CERTIFICATE = "-----BEGIN CERTIFICATE-----\nleaf\n-----END CERTIFICATE-----"
        const val CERTIFICATE_CHAIN =
            "$LEAF_CERTIFICATE\n-----BEGIN CERTIFICATE-----\nissuer\n-----END CERTIFICATE-----"
        val CHECKPOINT = E2EIRotationCheckpoint(
            certificateChain = CERTIFICATE_CHAIN,
            preExistingCredentialIds = listOf("previous"),
            newCredentialId = "new",
            groupIds = emptyList(),
            isNewClient = false,
            phase = E2EIRotationPhase.CREDENTIAL_INSTALLED
        )
        val AUTHENTICATION_REQUEST = E2EIAuthenticationRequest(IDP_URL, KEY_AUTH, ACME_AUDIENCE)
    }
}
