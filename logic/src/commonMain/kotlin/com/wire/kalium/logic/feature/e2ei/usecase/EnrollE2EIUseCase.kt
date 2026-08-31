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
package com.wire.kalium.logic.feature.e2ei.usecase

import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.e2ei.E2EIAuthenticationRequest
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Issue an E2EI certificate and update the MLS client's X.509 credential. */
public interface EnrollE2EIUseCase {
    /**
     * Runs credential acquisition, authentication, installation, key rotation, and conversation
     * migration as one continuous operation.
     */
    public suspend operator fun invoke(
        isNewClientRegistration: Boolean = false,
        authenticate: suspend (E2EIAuthenticationRequest) -> String
    ): EnrollE2EIResult
}

public sealed class EnrollE2EIResult {
    public data class Success(val certificate: String) : EnrollE2EIResult()

    public sealed class Failure : EnrollE2EIResult() {
        public data object E2EIDisabled : Failure()
        public data object MissingTeamSettings : Failure()
        public data class Generic(val e2EIFailure: E2EIFailure) : Failure()
    }
}

@Suppress("ReturnCount")
internal class EnrollE2EIUseCaseImpl internal constructor(
    private val e2EIRepository: E2EIRepository,
    private val userRepository: UserRepository,
    private val coroutineScope: CoroutineScope,
    private val conversationRepository: ConversationRepository,
    private val transactionProvider: CryptoTransactionProvider
) : EnrollE2EIUseCase {

    override suspend fun invoke(
        isNewClientRegistration: Boolean,
        authenticate: suspend (E2EIAuthenticationRequest) -> String
    ): EnrollE2EIResult {
        if (isNewClientRegistration) {
            coroutineScope.launch { userRepository.fetchSelfUser() }.join()
        }

        kaliumLogger.i("Starting continuous Core Crypto X.509 enrollment (new client: $isNewClientRegistration)")
        val checkpoint = e2EIRepository.acquireCredential(
            authenticate = authenticate,
            groupIdListProvider = ::currentMlsGroupIds,
            isNewClient = isNewClientRegistration
        ).getOrFail {
            kaliumLogger.e("Core Crypto X.509 credential acquisition failed: $it")
            return it.toEnrollmentFailure()
        }
        val certificateChain = checkpoint.certificateChain ?: return EnrollE2EIResult.Failure.Generic(
            E2EIFailure.Generic(IllegalStateException("The acquired X.509 certificate checkpoint is missing"))
        )

        e2EIRepository.rotateKeysAndMigrateConversations(
            transactionProvider = transactionProvider,
            checkpoint = checkpoint
        ).getOrFail {
            return it.toEnrollmentFailure()
        }

        return EnrollE2EIResult.Success(certificateChain.leafCertificate())
    }

    private suspend fun currentMlsGroupIds() = conversationRepository.observeConversationList().first().mapNotNull {
        when (val protocol = it.protocol) {
            is Conversation.ProtocolInfo.MLS -> protocol.groupId
            is Conversation.ProtocolInfo.Mixed -> protocol.groupId
            Conversation.ProtocolInfo.Proteus -> null
        }
    }

    private fun E2EIFailure.toEnrollmentFailure(): EnrollE2EIResult.Failure = when (this) {
        is E2EIFailure.Disabled -> EnrollE2EIResult.Failure.E2EIDisabled
        is E2EIFailure.MissingTeamSettings -> EnrollE2EIResult.Failure.MissingTeamSettings
        else -> EnrollE2EIResult.Failure.Generic(this)
    }

    private fun String.leafCertificate(): String {
        val endIndex = indexOf(CERT_END)
        return if (endIndex < 0) this else substring(0, endIndex + CERT_END.length)
    }

    private companion object {
        const val CERT_END = "-----END CERTIFICATE-----"
    }
}
