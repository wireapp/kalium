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

import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.data.e2ei.Nonce
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Issue an E2EI certificate and re-initiate the MLSClient
 */
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
    /**
     * Operation to initial E2EI certificate enrollment
     *
     * @return [InitialEnrollmentResult]
     */
    @Suppress("LongMethod")
    override suspend fun initialEnrollment(isNewClientRegistration: Boolean): InitialEnrollmentResult {

        if (isNewClientRegistration) {
            coroutineScope.launch {
                userRepository.fetchSelfUser()
            }.join()
        }

        kaliumLogger.i("start E2EI Enrollment Initialization (new client registration: $isNewClientRegistration)")

        e2EIRepository.initFreshE2EIClient(isNewClient = isNewClientRegistration).getOrFail {
            kaliumLogger.e("Failure initializing fresh E2EI client during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        e2EIRepository.fetchAndSetTrustAnchors().getOrFail {
            kaliumLogger.e("Failure fetching and setting trust anchors during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        e2EIRepository.fetchFederationCertificates().getOrFail {
            kaliumLogger.e("Failure fetching federation certificates during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        val acmeDirectories = e2EIRepository.loadACMEDirectories().getOrFail {
            kaliumLogger.d("Failure loading ACMEDirectories during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        var prevNonce = e2EIRepository.getACMENonce(acmeDirectories.newNonce).getOrFail {
            kaliumLogger.d("Failure getting ACMENonce during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        prevNonce = e2EIRepository.createNewAccount(prevNonce, acmeDirectories.newAccount).getOrFail {
            kaliumLogger.d("Failure creating new account during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        val newOrderResponse = e2EIRepository.createNewOrder(prevNonce, acmeDirectories.newOrder).getOrFail {
            kaliumLogger.d("Failure creating new order during E2EI Enrolling!. Failure:$it")
            return it.toInitialEnrollmentFailure()
        }

        prevNonce = newOrderResponse.second

        val authorizations =
            e2EIRepository.getAuthorizations(prevNonce, newOrderResponse.first.authorizations).getOrFail {
                kaliumLogger.d("Failure getting authorizations during E2EI Enrolling!. Failure:$it")
                return it.toInitialEnrollmentFailure()
            }

        prevNonce = authorizations.nonce
        val oidcAuthorizations = authorizations.oidcAuthorization
        val dPopAuthorizations = authorizations.dpopAuthorization

        val initializationResult = E2EIEnrollmentResult.Initialized(
            target = oidcAuthorizations.challenge.target,
            oAuthClaims = getOAuthClaims(
                oidcAuthorizations.keyAuth.toString(),
                oidcAuthorizations.challenge.url
            ),
            dPopAuthorizations = dPopAuthorizations,
            oidcAuthorizations = oidcAuthorizations,
            lastNonce = prevNonce,
            orderLocation = newOrderResponse.third,
            isNewClientRegistration = isNewClientRegistration
        )

        kaliumLogger.i("E2EI Enrollment Initialization Result: $initializationResult")

        return InitialEnrollmentResult.Success(initializationResult)
    }

    private fun E2EIFailure.toInitialEnrollmentFailure(): InitialEnrollmentResult.Failure = when (this) {
        is E2EIFailure.Disabled -> InitialEnrollmentResult.Failure.E2EIDisabled
        is E2EIFailure.MissingTeamSettings -> InitialEnrollmentResult.Failure.MissingTeamSettings
        else -> InitialEnrollmentResult.Failure.Generic(this)
    }

    /**
     * Operation to finalize E2EI certificate enrollment
     *
     * @param idToken id token generated by the IdP
     * @param initializationResult e2ei initialization result
     *
     * @return [FinalizeEnrollmentResult]
     */
    @Suppress("LongMethod")
    override suspend fun finalizeEnrollment(
        idToken: String,
        oAuthState: String,
        initializationResult: E2EIEnrollmentResult.Initialized
    ): FinalizeEnrollmentResult {

        var prevNonce = initializationResult.lastNonce
        val dPopAuthorizations = initializationResult.dPopAuthorizations
        val oidcAuthorizations = initializationResult.oidcAuthorizations
        val orderLocation = initializationResult.orderLocation
        val isNewClientRegistration = initializationResult.isNewClientRegistration

        val wireNonce = e2EIRepository.getWireNonce().getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        val dpopToken = e2EIRepository.getDPoPToken(wireNonce).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        val wireAccessToken = e2EIRepository.getWireAccessToken(dpopToken).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        val dpopChallengeResponse = e2EIRepository.validateDPoPChallenge(
            wireAccessToken.token,
            prevNonce,
            dPopAuthorizations.challenge
        ).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        prevNonce = Nonce(dpopChallengeResponse.nonce)

        val oidcChallengeResponse = e2EIRepository.validateOIDCChallenge(
            idToken,
            oAuthState,
            prevNonce,
            oidcAuthorizations.challenge
        ).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        prevNonce = Nonce(oidcChallengeResponse.nonce)

        val orderResponse = e2EIRepository.checkOrderRequest(orderLocation, prevNonce).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        prevNonce = Nonce(orderResponse.first.nonce)

        val finalizeResponse = e2EIRepository.finalize(orderResponse.second, prevNonce).getOrFail {
            return it.toFinalizeEnrollmentFailure()
        }

        prevNonce = Nonce(finalizeResponse.first.nonce)

        val certificateRequest =
            e2EIRepository.certificateRequest(finalizeResponse.second, prevNonce).getOrFail {
                return it.toFinalizeEnrollmentFailure()
            }

        if (isNewClientRegistration) {
            e2EIRepository.initiateMLSClient(certificateRequest.response.decodeToString()).onFailure {
                return it.toFinalizeEnrollmentFailure()
            }
        } else {
            val groupIdList = conversationRepository.observeConversationList().first().mapNotNull {
                when (val protocol = it.protocol) {
                    is Conversation.ProtocolInfo.MLS -> protocol.groupId
                    is Conversation.ProtocolInfo.Mixed -> protocol.groupId
                    Conversation.ProtocolInfo.Proteus -> null
                }
            }

            transactionProvider.mlsTransaction("E2EIEnrollment") { mlsContext ->
                e2EIRepository.rotateKeysAndMigrateConversations(
                    mlsContext,
                    certificateRequest.response.decodeToString(),
                    groupIdList,
                    initializationResult.isNewClientRegistration,
                )
            }
                .onFailure {
                    return if (it is E2EIFailure) {
                        it.toFinalizeEnrollmentFailure()
                    } else {
                        FinalizeEnrollmentResult.Failure.Generic(E2EIFailure.RotationAndMigration(it))
                    }
                }
        }

        @Suppress("TooGenericExceptionCaught")
        val e2eiCert = certificateRequest.response.decodeToString().let { theDoubleCert ->
            try {
                val firstCertEndIndex = theDoubleCert.indexOf(CERT_END) + CERT_END_LENGTH
                theDoubleCert.substring(0, firstCertEndIndex)
            } catch (e: IndexOutOfBoundsException) {
                theDoubleCert
            }
        }

        return FinalizeEnrollmentResult.Success(e2eiCert)
    }

    private fun E2EIFailure.toFinalizeEnrollmentFailure(): FinalizeEnrollmentResult.Failure = when (this) {
        is E2EIFailure.OAuth -> FinalizeEnrollmentResult.Failure.OAuthError(this.reason)
        is E2EIFailure.InvalidChallenge -> FinalizeEnrollmentResult.Failure.InvalidChallenge
        else -> FinalizeEnrollmentResult.Failure.Generic(this)
    }

    private fun getOAuthClaims(keyAuth: String, acmeAud: String) = JsonObject(
        mapOf(
            ID_TOKEN to JsonObject(
                mapOf(
                    KEY_AUTH to JsonObject(
                        mapOf(ESSENTIAL to JsonPrimitive(true), VALUE to JsonPrimitive(keyAuth))
                    ),
                    ACME_AUD to JsonObject(
                        mapOf(
                            ESSENTIAL to JsonPrimitive(true),
                            VALUE to JsonPrimitive(acmeAud)
                        )
                    )
                )
            )
        )
    )

    internal companion object {
        private const val ID_TOKEN = "id_token"
        private const val KEY_AUTH = "keyauth"
        private const val ESSENTIAL = "essential"
        private const val VALUE = "value"
        private const val ACME_AUD = "acme_aud"
        private const val CERT_END = "-----END CERTIFICATE-----"
        private const val CERT_END_LENGTH = CERT_END.length
    }
}

public sealed interface E2EIEnrollmentResult {
    @Suppress("LongParameterList")
    public data class Initialized(
        val target: String,
        val oAuthClaims: JsonObject,
        val lastNonce: Nonce,
        val orderLocation: String,
        val dPopAuthorizations: NewAcmeAuthz,
        val oidcAuthorizations: NewAcmeAuthz,
        val isNewClientRegistration: Boolean = false
    ) : E2EIEnrollmentResult

    public class Finalized(public val certificate: String) : E2EIEnrollmentResult
}
