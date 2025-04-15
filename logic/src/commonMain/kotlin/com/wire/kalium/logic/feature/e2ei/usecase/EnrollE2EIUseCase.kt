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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
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
interface EnrollE2EIUseCase {
    suspend fun initialEnrollment(isNewClientRegistration: Boolean = false): Either<E2EIFailure, E2EIEnrollmentResult.Initialized>
    suspend fun finalizeEnrollment(
        idToken: String,
        oAuthState: String,
        initializationResult: E2EIEnrollmentResult.Initialized
    ): Either<E2EIFailure, E2EIEnrollmentResult.Finalized>
}

@Suppress("ReturnCount")
class EnrollE2EIUseCaseImpl internal constructor(
    private val e2EIRepository: E2EIRepository,
    private val userRepository: UserRepository,
    private val coroutineScope: CoroutineScope,
    private val conversationRepository: ConversationRepository,
) : EnrollE2EIUseCase {
    /**
     * Operation to initial E2EI certificate enrollment
     *
     * @return [Either] [E2EIFailure] or [E2EIEnrollmentResult]
     */
    @Suppress("LongMethod")
    override suspend fun initialEnrollment(isNewClientRegistration: Boolean): Either<E2EIFailure, E2EIEnrollmentResult.Initialized> {

        if (isNewClientRegistration) {
            coroutineScope.launch {
                userRepository.fetchSelfUser()
            }.join()
        }

        kaliumLogger.i("start E2EI Enrollment Initialization (new client registration: $isNewClientRegistration)")

        e2EIRepository.initFreshE2EIClient(isNewClient = isNewClientRegistration).getOrFail {
            kaliumLogger.e("Failure initializing fresh E2EI client during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        e2EIRepository.fetchAndSetTrustAnchors().getOrFail {
            kaliumLogger.e("Failure fetching and setting trust anchors during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        e2EIRepository.fetchFederationCertificates().getOrFail {
            kaliumLogger.e("Failure fetching federation certificates during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        val acmeDirectories = e2EIRepository.loadACMEDirectories().getOrFail {
            kaliumLogger.d("Failure loading ACMEDirectories during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        var prevNonce = e2EIRepository.getACMENonce(acmeDirectories.newNonce).getOrFail {
            kaliumLogger.d("Failure getting ACMENonce during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        prevNonce = e2EIRepository.createNewAccount(prevNonce, acmeDirectories.newAccount).getOrFail {
            kaliumLogger.d("Failure creating new account during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        val newOrderResponse = e2EIRepository.createNewOrder(prevNonce, acmeDirectories.newOrder).getOrFail {
            kaliumLogger.d("Failure creating new order during E2EI Enrolling!. Failure:$it")
            return it.left()
        }

        prevNonce = newOrderResponse.second

        val authorizations =
            e2EIRepository.getAuthorizations(prevNonce, newOrderResponse.first.authorizations).getOrFail {
                kaliumLogger.d("Failure getting authorizations during E2EI Enrolling!. Failure:$it")
                return it.left()
            }

        prevNonce = authorizations.nonce
        val oidcAuthorizations = authorizations.oidcAuthorization
        val dPopAuthorizations = authorizations.dpopAuthorization

        val oAuthState = e2EIRepository.getOAuthRefreshToken().getOrNull()

        val initializationResult = E2EIEnrollmentResult.Initialized(
            target = oidcAuthorizations.challenge.target,
            oAuthState = oAuthState,
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

        return initializationResult.right()
    }

    /**
     * Operation to finalize E2EI certificate enrollment
     *
     * @param idToken id token generated by the IdP
     * @param initializationResult e2ei initialization result
     *
     * @return [Either] [E2EIFailure] or [E2EIEnrollmentResult]
     */
    @Suppress("LongMethod")
    override suspend fun finalizeEnrollment(
        idToken: String,
        oAuthState: String,
        initializationResult: E2EIEnrollmentResult.Initialized
    ): Either<E2EIFailure, E2EIEnrollmentResult.Finalized> {

        var prevNonce = initializationResult.lastNonce
        val dPopAuthorizations = initializationResult.dPopAuthorizations
        val oidcAuthorizations = initializationResult.oidcAuthorizations
        val orderLocation = initializationResult.orderLocation
        val isNewClientRegistration = initializationResult.isNewClientRegistration

        val wireNonce = e2EIRepository.getWireNonce().getOrFail {
            return it.left()
        }

        val dpopToken = e2EIRepository.getDPoPToken(wireNonce).getOrFail {
            return it.left()
        }

        val wireAccessToken = e2EIRepository.getWireAccessToken(dpopToken).getOrFail {
            return it.left()
        }

        val dpopChallengeResponse = e2EIRepository.validateDPoPChallenge(
            wireAccessToken.token, prevNonce, dPopAuthorizations.challenge
        ).getOrFail {
            return it.left()
        }

        prevNonce = Nonce(dpopChallengeResponse.nonce)

        val oidcChallengeResponse = e2EIRepository.validateOIDCChallenge(
            idToken, oAuthState, prevNonce, oidcAuthorizations.challenge
        ).getOrFail {
            return it.left()
        }

        prevNonce = Nonce(oidcChallengeResponse.nonce)

        val orderResponse = e2EIRepository.checkOrderRequest(orderLocation, prevNonce).getOrFail {
            return it.left()
        }

        prevNonce = Nonce(orderResponse.first.nonce)

        val finalizeResponse = e2EIRepository.finalize(orderResponse.second, prevNonce).getOrFail {
            return it.left()
        }

        prevNonce = Nonce(finalizeResponse.first.nonce)

        val certificateRequest =
            e2EIRepository.certificateRequest(finalizeResponse.second, prevNonce).getOrFail {
                return it.left()
            }

        if (isNewClientRegistration) {
            e2EIRepository.initiateMLSClient(certificateRequest.response.decodeToString()).onFailure {
                return it.left()
            }
        } else {
            // TODO secure
            val groupIdList = conversationRepository.observeConversationList().first().mapNotNull {
                when (val protocol = it.protocol) {
                    is Conversation.ProtocolInfo.MLS -> protocol.groupId
                    is Conversation.ProtocolInfo.Mixed -> protocol.groupId
                    Conversation.ProtocolInfo.Proteus -> null
                }
            }

            e2EIRepository.rotateKeysAndMigrateConversations(
                certificateRequest.response.decodeToString(),
                groupIdList,
                initializationResult.isNewClientRegistration,
            ).onFailure { return it.left() }
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

        return E2EIEnrollmentResult.Finalized(e2eiCert).right()
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

    companion object {
        private const val ID_TOKEN = "id_token"
        private const val KEY_AUTH = "keyauth"
        private const val ESSENTIAL = "essential"
        private const val VALUE = "value"
        private const val ACME_AUD = "acme_aud"
        private const val CERT_END = "-----END CERTIFICATE-----"
        private const val CERT_END_LENGTH = CERT_END.length
    }
}

sealed interface E2EIEnrollmentResult {
    @Suppress("LongParameterList")
    data class Initialized(
        val target: String,
        val oAuthState: String?,
        val oAuthClaims: JsonObject,
        val lastNonce: Nonce,
        val orderLocation: String,
        val dPopAuthorizations: NewAcmeAuthz,
        val oidcAuthorizations: NewAcmeAuthz,
        val isNewClientRegistration: Boolean = false
    ) : E2EIEnrollmentResult

    class Finalized(val certificate: String) : E2EIEnrollmentResult
}
