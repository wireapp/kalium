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
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Issue an E2EI certificate and update the MLS client's X.509 credential. */
public interface EnrollE2EIUseCase {
    public suspend fun initialEnrollment(isNewClientRegistration: Boolean = false): InitialEnrollmentResult

    public suspend fun finalizeEnrollment(
        idToken: String,
        oAuthState: String,
        initializationResult: E2EIEnrollmentResult.Initialized
    ): FinalizeEnrollmentResult
}

public sealed class InitialEnrollmentResult {
    public data class Success(val initializationResult: E2EIEnrollmentResult.Initialized) : InitialEnrollmentResult()

    public sealed class Failure : InitialEnrollmentResult() {
        public data object E2EIDisabled : Failure()
        public data object MissingTeamSettings : Failure()
        public data class Generic(val e2EIFailure: E2EIFailure) : Failure()
    }
}

public sealed class FinalizeEnrollmentResult {
    public data class Success(val certificate: String) : FinalizeEnrollmentResult()

    public sealed class Failure : FinalizeEnrollmentResult() {
        public data class OAuthError(val reason: String) : Failure()
        public data object InvalidChallenge : Failure()
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

    override suspend fun initialEnrollment(isNewClientRegistration: Boolean): InitialEnrollmentResult {
        if (isNewClientRegistration) {
            coroutineScope.launch { userRepository.fetchSelfUser() }.join()
        }

        kaliumLogger.i("Starting Core Crypto X.509 credential acquisition (new client: $isNewClientRegistration)")
        val authenticationRequest = e2EIRepository.startCredentialAcquisition(isNewClientRegistration).getOrFail {
            kaliumLogger.e("Starting Core Crypto X.509 credential acquisition failed: $it")
            return it.toInitialEnrollmentFailure()
        }

        return InitialEnrollmentResult.Success(
            E2EIEnrollmentResult.Initialized(
                target = authenticationRequest.target,
                oAuthClaims = getOAuthClaims(
                    keyAuth = authenticationRequest.keyAuth,
                    acmeAudience = authenticationRequest.acmeAudience
                ),
                isNewClientRegistration = isNewClientRegistration
            )
        )
    }

    /**
     * Resume the acquisition snapshot persisted by Core Crypto's authentication hook.
     *
     * [oAuthState] is retained for source compatibility. Core Crypto v10 consumes the IdP
     * token directly and no longer accepts the old refresh-token/state value.
     */
    override suspend fun finalizeEnrollment(
        idToken: String,
        @Suppress("UNUSED_PARAMETER") oAuthState: String,
        initializationResult: E2EIEnrollmentResult.Initialized
    ): FinalizeEnrollmentResult {
        val groupIdList = conversationRepository.observeConversationList().first().mapNotNull {
            when (val protocol = it.protocol) {
                is Conversation.ProtocolInfo.MLS -> protocol.groupId
                is Conversation.ProtocolInfo.Mixed -> protocol.groupId
                Conversation.ProtocolInfo.Proteus -> null
            }
        }
        val checkpoint = e2EIRepository.resumeCredentialAcquisition(
            idToken = idToken,
            groupIdList = groupIdList,
            isNewClient = initializationResult.isNewClientRegistration
        ).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }
        val certificateChain = checkpoint.certificateChain ?: return FinalizeEnrollmentResult.Failure.Generic(
            E2EIFailure.Generic(IllegalStateException("The acquired X.509 certificate checkpoint is missing"))
        )

        e2EIRepository.rotateKeysAndMigrateConversations(
            transactionProvider = transactionProvider,
            checkpoint = checkpoint
        ).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        return FinalizeEnrollmentResult.Success(certificateChain.leafCertificate())
    }

    private fun E2EIFailure.toInitialEnrollmentFailure(): InitialEnrollmentResult.Failure = when (this) {
        is E2EIFailure.Disabled -> InitialEnrollmentResult.Failure.E2EIDisabled
        is E2EIFailure.MissingTeamSettings -> InitialEnrollmentResult.Failure.MissingTeamSettings
        else -> InitialEnrollmentResult.Failure.Generic(this)
    }

    private fun E2EIFailure.toFinalizeEnrollmentFailure(): FinalizeEnrollmentResult.Failure = when (this) {
        is E2EIFailure.OAuth -> FinalizeEnrollmentResult.Failure.OAuthError(this.reason)
        is E2EIFailure.InvalidChallenge -> FinalizeEnrollmentResult.Failure.InvalidChallenge
        else -> FinalizeEnrollmentResult.Failure.Generic(this)
    }

    private fun getOAuthClaims(keyAuth: String, acmeAudience: String) = JsonObject(
        mapOf(
            ID_TOKEN to JsonObject(
                mapOf(
                    KEY_AUTH to JsonObject(
                        mapOf(ESSENTIAL to JsonPrimitive(true), VALUE to JsonPrimitive(keyAuth))
                    ),
                    ACME_AUD to JsonObject(
                        mapOf(ESSENTIAL to JsonPrimitive(true), VALUE to JsonPrimitive(acmeAudience))
                    )
                )
            )
        )
    )

    private fun String.leafCertificate(): String {
        val endIndex = indexOf(CERT_END)
        return if (endIndex < 0) this else substring(0, endIndex + CERT_END.length)
    }

    private companion object {
        const val ID_TOKEN = "id_token"
        const val KEY_AUTH = "keyauth"
        const val ESSENTIAL = "essential"
        const val VALUE = "value"
        const val ACME_AUD = "acme_aud"
        const val CERT_END = "-----END CERTIFICATE-----"
    }
}

public sealed interface E2EIEnrollmentResult {
    public data class Initialized(
        val target: String,
        val oAuthClaims: JsonObject,
        val isNewClientRegistration: Boolean = false
    ) : E2EIEnrollmentResult

    public class Finalized(public val certificate: String) : E2EIEnrollmentResult
}
