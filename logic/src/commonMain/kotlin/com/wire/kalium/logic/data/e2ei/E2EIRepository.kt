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
@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.AcmeDirectory
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.cryptography.NewAcmeOrder
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapE2EIRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface E2EIRepository {
    suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory>
    suspend fun getACMENonce(endpoint: String): Either<CoreFailure, String>
    suspend fun createNewAccount(prevNonce: String, createAccountEndpoint: String): Either<CoreFailure, String>
    suspend fun createNewOrder(prevNonce: String, createOrderEndpoint: String): Either<CoreFailure, Triple<NewAcmeOrder, String, String>>
    suspend fun createAuthz(prevNonce: String, authzEndpoint: String): Either<CoreFailure, Triple<NewAcmeAuthz, String, String>>
    suspend fun getWireNonce(): Either<CoreFailure, String>
    suspend fun getWireAccessToken(wireNonce: String): Either<CoreFailure, AccessTokenResponse>
    suspend fun getDPoPToken(wireNonce: String): Either<CoreFailure, String>
    suspend fun validateDPoPChallenge(
        accessToken: String,
        prevNonce: String,
        acmeChallenge: AcmeChallenge
    ): Either<CoreFailure, ChallengeResponse>

    suspend fun validateOIDCChallenge(
        idToken: String,
        refreshToken: String,
        prevNonce: String,
        acmeChallenge: AcmeChallenge
    ): Either<CoreFailure, ChallengeResponse>

    suspend fun setDPoPChallengeResponse(challengeResponse: ChallengeResponse): Either<CoreFailure, Unit>
    suspend fun setOIDCChallengeResponse(challengeResponse: ChallengeResponse): Either<CoreFailure, Unit>
    suspend fun finalize(location: String, prevNonce: String): Either<CoreFailure, Pair<ACMEResponse, String>>
    suspend fun checkOrderRequest(location: String, prevNonce: String): Either<CoreFailure, Pair<ACMEResponse, String>>
    suspend fun certificateRequest(location: String, prevNonce: String): Either<CoreFailure, ACMEResponse>
    suspend fun rotateKeysAndMigrateConversations(certificateChain: String): Either<CoreFailure, Unit>
    suspend fun getOAuthRefreshToken(): Either<CoreFailure, String?>
    suspend fun nukeE2EIClient()
    suspend fun fetchFederationCertificates(): Either<CoreFailure, Unit>
    suspend fun getCurrentClientDomainCRL(): Either<CoreFailure, ByteArray>
}

@Suppress("LongParameterList")
class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val acmeApi: ACMEApi,
    private val e2EIClientProvider: E2EIClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userConfigRepository: UserConfigRepository
) : E2EIRepository {

    override suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory> = userConfigRepository.getE2EISettings().flatMap {
        wrapApiRequest {
            acmeApi.getACMEDirectories(it.discoverUrl)
        }.flatMap { directories ->
            e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
                wrapE2EIRequest {
                    e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
                }
            }
        }
    }

    override suspend fun getACMENonce(endpoint: String) = wrapApiRequest {
        acmeApi.getACMENonce(endpoint)
    }

    override suspend fun createNewAccount(prevNonce: String, createAccountEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val accountRequest = e2eiClient.getNewAccountRequest(prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(createAccountEndpoint, accountRequest)
            }.map { apiResponse ->
                e2eiClient.setAccountResponse(apiResponse.response)
                apiResponse.nonce
            }
        }

    override suspend fun createNewOrder(prevNonce: String, createOrderEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val orderRequest = e2eiClient.getNewOrderRequest(prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(createOrderEndpoint, orderRequest)
            }.flatMap { apiResponse ->
                val orderResponse = e2eiClient.setOrderResponse(apiResponse.response)
                Either.Right(Triple(orderResponse, apiResponse.nonce, apiResponse.location))
            }
        }

    override suspend fun createAuthz(prevNonce: String, authzEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val authzRequest = e2eiClient.getNewAuthzRequest(authzEndpoint, prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(authzEndpoint, authzRequest)
            }.flatMap { apiResponse ->
                val authzResponse = e2eiClient.setAuthzResponse(apiResponse.response)
                Either.Right(Triple(authzResponse, apiResponse.nonce, apiResponse.location))
            }
        }

    override suspend fun getWireNonce() = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getWireNonce(clientId.value)
        }
    }

    override suspend fun getWireAccessToken(dpopToken: String) = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getAccessToken(clientId.value, dpopToken)
        }
    }

    override suspend fun getDPoPToken(wireNonce: String) = e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
        Either.Right(e2eiClient.createDpopToken(wireNonce))
    }

    override suspend fun validateDPoPChallenge(accessToken: String, prevNonce: String, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewDpopChallengeRequest(accessToken, prevNonce)
            wrapApiRequest {
                acmeApi.sendChallengeRequest(acmeChallenge.url, challengeRequest)
            }.map { apiResponse ->
                setDPoPChallengeResponse(apiResponse)
                apiResponse
            }
        }

    override suspend fun validateOIDCChallenge(idToken: String, refreshToken: String, prevNonce: String, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewOidcChallengeRequest(idToken, refreshToken, prevNonce)
            wrapApiRequest {
                acmeApi.sendChallengeRequest(acmeChallenge.url, challengeRequest)
            }.map { apiResponse ->
                setOIDCChallengeResponse(apiResponse)
                apiResponse
            }
        }

    override suspend fun setDPoPChallengeResponse(challengeResponse: ChallengeResponse) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            e2eiClient.setDPoPChallengeResponse(Json.encodeToString(challengeResponse).encodeToByteArray())
            Either.Right(Unit)
        }

    override suspend fun setOIDCChallengeResponse(challengeResponse: ChallengeResponse) =
        mlsClientProvider.getCoreCrypto().flatMap { coreCrypto ->
            e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
                e2eiClient.setOIDCChallengeResponse(coreCrypto, Json.encodeToString(challengeResponse).encodeToByteArray())
                Either.Right(Unit)
            }
        }

    override suspend fun checkOrderRequest(location: String, prevNonce: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val checkOrderRequest = e2eiClient.checkOrderRequest(location, prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, checkOrderRequest)
            }.map { apiResponse ->
                val finalizeOrderUrl = e2eiClient.checkOrderResponse(apiResponse.response)
                Pair(apiResponse, finalizeOrderUrl)
            }
        }

    override suspend fun finalize(location: String, prevNonce: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val finalizeRequest = e2eiClient.finalizeRequest(prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, finalizeRequest)
            }.map { apiResponse ->
                val certificateChain = e2eiClient.finalizeResponse(apiResponse.response)
                Pair(apiResponse, certificateChain)
            }
        }

    override suspend fun certificateRequest(location: String, prevNonce: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val certificateRequest = e2eiClient.certificateRequest(prevNonce)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, certificateRequest)
            }.map { it }
        }

    override suspend fun rotateKeysAndMigrateConversations(certificateChain: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            currentClientIdProvider().flatMap { clientId ->
                mlsConversationRepository.rotateKeysAndMigrateConversations(clientId, e2eiClient, certificateChain)
            }
        }

    override suspend fun getOAuthRefreshToken() = e2EIClientProvider.getE2EIClient().flatMap { e2EIClient ->
        Either.Right(e2EIClient.getOAuthRefreshToken())
    }

    override suspend fun fetchFederationCertificates(): Either<CoreFailure, Unit> = userConfigRepository.getE2EISettings().flatMap {
        wrapApiRequest {
            acmeApi.getACMEFederation(it.discoverUrl)
        }.flatMap { data ->
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapMLSRequest {
                    mlsClient.registerIntermediateCa(data.value)
                    Unit
                }
            }
        }
    }

    override suspend fun nukeE2EIClient() {
        e2EIClientProvider.nuke()
    }

    override suspend fun getCurrentClientDomainCRL(): Either<CoreFailure, ByteArray> =
        userConfigRepository.getE2EISettings().flatMap {
            wrapApiRequest {
                acmeApi.getCurrentClientDomainCRL(it.discoverUrl)
            }
        }
}
