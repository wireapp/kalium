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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.cryptography.AcmeChallenge
import com.wire.kalium.cryptography.AcmeDirectory
import com.wire.kalium.cryptography.NewAcmeAuthz
import com.wire.kalium.cryptography.NewAcmeOrder
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.E2EClientProvider
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapE2EIRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface E2EIRepository {
    suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory>
    suspend fun getACMENonce(endpoint: String): Either<CoreFailure, String>
    suspend fun createNewAccount(prevNonce: String, createAccountEndpoint: String): Either<CoreFailure, String>
    suspend fun createNewOrder(prevNonce: String, createOrderEndpoint: String): Either<CoreFailure, Pair<NewAcmeOrder, String>>
    suspend fun createAuthz(prevNonce: String, authzEndpoint: String): Either<CoreFailure, Pair<NewAcmeAuthz, String>>
    suspend fun getWireNonce(): Either<CoreFailure, String>
    suspend fun getWireAccessTokenEndPoint(): Either<CoreFailure, String>
    suspend fun getWireAccessToken(wireNonce: String): Either<CoreFailure, AccessTokenResponse>
    suspend fun getDPoPToken(wireNonce: String): Either<CoreFailure, String>
    suspend fun validateDPoPChallenge(accessToken: String, prevNonce: String, acmeChallenge: AcmeChallenge): Either<CoreFailure, ChallengeResponse>
    suspend fun validateOIDCChallenge(idToken: String, prevNonce: String, acmeChallenge: AcmeChallenge): Either<CoreFailure, ChallengeResponse>
    suspend fun validateChallenge(challengeResponse: ChallengeResponse): Either<CoreFailure, Unit>
}

class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val acmeApi: ACMEApi,
    private val e2EClientProvider: E2EClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider
) : E2EIRepository {

    override suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory> = wrapApiRequest {
        acmeApi.getACMEDirectories()
    }.flatMap { directories ->
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            wrapE2EIRequest {
                e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
            }
        }
    }

    override suspend fun getACMENonce(endpoint: String) = wrapApiRequest {
        acmeApi.getACMENonce(endpoint)
    }

    override suspend fun createNewAccount(prevNonce: String, createAccountEndpoint: String) =
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val accountRequest = e2eiClient.getNewAccountRequest(prevNonce)

            wrapApiRequest {
                acmeApi.getNewAccount(createAccountEndpoint, accountRequest)
            }.map { apiResponse ->
                e2eiClient.setAccountResponse(apiResponse.response)
                apiResponse.nonce
            }
        }

    override suspend fun createNewOrder(prevNonce: String, createOrderEndpoint: String) =
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val orderRequest = e2eiClient.getNewOrderRequest(prevNonce)
            wrapApiRequest {
                acmeApi.getNewOrder(createOrderEndpoint, orderRequest)
            }.flatMap { apiResponse ->
                val orderRespone = e2eiClient.setOrderResponse(apiResponse.response)
                Either.Right(Pair(orderRespone, apiResponse.nonce))
            }
        }

    override suspend fun createAuthz(prevNonce: String, authzEndpoint: String) = e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
        val authzRequest = e2eiClient.getNewAuthzRequest(authzEndpoint, prevNonce)
        wrapApiRequest {
            acmeApi.getNewOrder(authzEndpoint, authzRequest)
        }.flatMap { apiResponse ->
            val authzResponse = e2eiClient.setAuthzResponse(apiResponse.response)
            Either.Right(Pair(authzResponse, apiResponse.nonce))
        }
    }

    override suspend fun getWireNonce() = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getWireNonce(clientId.value)
        }
    }

    override suspend fun getWireAccessTokenEndPoint() = currentClientIdProvider().map { clientId ->
        e2EIApi.getAccessTokenUrl(clientId.value)
    }

    override suspend fun getWireAccessToken(dpopToken: String) = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getAccessToken(clientId.value, dpopToken)
        }
    }

    override suspend fun getDPoPToken(wireNonce: String) = getWireAccessTokenEndPoint().flatMap { accessTokenEndpoint ->
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            Either.Right(e2eiClient.createDpopToken(accessTokenEndpoint, wireNonce))
        }
    }

    override suspend fun validateDPoPChallenge(accessToken: String, prevNonce: String, acmeChallenge: AcmeChallenge) =
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewDpopChallengeRequest(accessToken, prevNonce)
            wrapApiRequest {
                acmeApi.dpopChallenge(acmeChallenge.url, challengeRequest)
            }.map { apiResponse ->
                validateChallenge(apiResponse)
                apiResponse
            }
        }

    override suspend fun validateOIDCChallenge(idToken: String, prevNonce: String, acmeChallenge: AcmeChallenge) =
        e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewOidcChallengeRequest(idToken, prevNonce)
            wrapApiRequest {
                acmeApi.oidcChallenge(acmeChallenge.url, challengeRequest)
            }.map { apiResponse ->
                validateChallenge(apiResponse)
                apiResponse
            }
        }

    override suspend fun validateChallenge(challengeResponse: ChallengeResponse) = e2EClientProvider.getE2EIClient().flatMap { e2eiClient ->
        e2eiClient.setChallengeResponse(Json.encodeToString(challengeResponse).encodeToByteArray())
        Either.Right(Unit)
    }
}
