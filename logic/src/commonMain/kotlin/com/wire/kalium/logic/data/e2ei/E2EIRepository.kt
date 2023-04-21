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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AuthzDirectories
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface E2EIRepository {

    suspend fun enrollE2EI()
    suspend fun getACMEDirectories(): Either<NetworkFailure, AcmeDirectoriesResponse>
    suspend fun getAuthzDirectories(): Either<NetworkFailure, AuthzDirectories>
    suspend fun getNewNonce(nonceUrl: String): Either<NetworkFailure, String>

    suspend fun getNewAccount(
        requestUrl: String,
        request: ByteArray
    ): Either<NetworkFailure, AcmeResponse>

    suspend fun getNewOrder(
        requestUrl: String,
        request: ByteArray
    ): Either<NetworkFailure, AcmeResponse>
}

class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val mlsClientProvider: MLSClientProvider
) : E2EIRepository {

    override suspend fun getACMEDirectories(): Either<NetworkFailure, AcmeDirectoriesResponse> =
        wrapApiRequest {
            e2EIApi.getAcmeDirectories()
        }

    override suspend fun getAuthzDirectories(): Either<NetworkFailure, AuthzDirectories> =
        wrapApiRequest {
            e2EIApi.getAuhzDirectories()
        }

    override suspend fun enrollE2EI() {
        mlsClientProvider.getE2EIClient().flatMap { e2eiClient ->

            //<editor-fold desc="Get ACME Directories">
            //fetch directories
            val directories = getACMEDirectories().fold({
                AcmeDirectoriesResponse("", "", "", "", "")
            },
                { it }
            )
            kaliumLogger.w("Directories from API:> $directories")

            //Set directories to E2EIClient
            e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
            kaliumLogger.w("ACME Directories Passed to CC")

            //</editor-fold>

            //<editor-fold desc="Get New Nonce">
            //get nonce from nonce url
            val nonce = getNewNonce(directories.newNonce).fold({ "" }, { it })
            kaliumLogger.w("NewNonce from API:> $nonce")
            //</editor-fold>

            //<editor-fold desc="Create new account">
            val accountRequest = e2eiClient.newAccountRequest(nonce)
            kaliumLogger.w("NewAccountRequest from CC:>${toLog(accountRequest)}")

            val accountResponse =
                getNewAccount(directories.newAccount, accountRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("NewNonce from API:> ${accountResponse.nonce}")
            kaliumLogger.w("NewAccountResponse from API:>${toLog(accountResponse.response)}")

            e2eiClient.newAccountResponse(accountResponse.response)
            kaliumLogger.w("NewAccountResponse Passed to CC")

            //</editor-fold>

            //<editor-fold desc="Create New Order">
            val orderRequest = e2eiClient.newOrderRequest(accountResponse.nonce)
            kaliumLogger.w("NewOrderRequest from CC:>${toLog(orderRequest)}")

            val orderResponse =
                getNewOrder(directories.newOrder, orderRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("NewNonce from API:> ${orderResponse.nonce}")
            kaliumLogger.w("NewOrderResponse from API:>${toLog(orderResponse.response)}")

            val order = e2eiClient.newOrderResponse(orderResponse.response)

            //todo: get the location from api response
            kaliumLogger.w("OrderResponse from CC :>${order.authorizations}")
            kaliumLogger.w(toLog(order.delegate))
            //</editor-fold>

            //<editor-fold desc="Authz Request">
            val authzRequest =
                e2eiClient.newAuthzRequest(order.authorizations[0], orderResponse.nonce)
            kaliumLogger.w("NewAuthzRequest from CC:>${toLog(authzRequest)}")

            val authzResponse =
                getNewOrder(order.authorizations[0], authzRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("NewNonce from API:> ${authzResponse.nonce}")
            kaliumLogger.w("AuthzResp from API:>${toLog(authzResponse.response)}")

            val authz = e2eiClient.newAuthzResponse(authzResponse.response)
            kaliumLogger.w("AuthzResp from CC :>${authz.identifier}")
            kaliumLogger.w(authz.wireHttpChallenge!!.url)
            kaliumLogger.w(toLog(authz.wireHttpChallenge!!.delegate))
            kaliumLogger.w(authz.wireOidcChallenge!!.url)
            kaliumLogger.w(toLog(authz.wireOidcChallenge!!.delegate))
            //</editor-fold>

            //<editor-fold desc="getAuthzDirectories">
            val authzDirectories =
                getAuthzDirectories().fold(
                    { AuthzDirectories("", "", "", "", "", "") },
                    { it })
            kaliumLogger.w("AuthzDirectories from API:> $authzDirectories")
            //</editor-fold>
//Client fetches JWT DPoP access token (with wire-server)
            //<editor-fold desc="DPOP">
            val dpopToken = e2eiClient.createDpopToken(authzDirectories.tokenEndpoint, authzResponse.nonce)
            kaliumLogger.w("clientDpopToken from CC :>$dpopToken")

            //create dpop challenge
            val dpopChallengeRequest = e2eiClient.newDpopChallengeRequest(dpopToken, authzResponse.nonce)
            kaliumLogger.w("DpopChallengeReq from CC:>${toLog(dpopChallengeRequest)}")
            //</editor-fold>

//                //wire challenge
//                val wirechallenge =
//                    getNewOrder(auzhCCResp., newAuthzRequest).fold(
//                        { AcmeResponse("", byteArrayOf()) },
//                        { it })
//                kaliumLogger.w(
//                    "\n ########## authzResp -> ${
//                        authzResp.response.joinToString("") { authzResps ->
//                            authzResps.toByte().toChar().toString()
//                        }
//                    }"
//                )
//                //oidc challenge


            Either.Right("")

        }

    }

    fun toLog(value: ByteArray) = value.joinToString("") {
        it.toByte().toChar().toString()
    }

    override suspend fun getNewNonce(nonceUrl: String): Either<NetworkFailure, String> =
        wrapApiRequest {
            e2EIApi.getNewNonce(nonceUrl)
        }

    override suspend fun getNewAccount(
        requestUrl: String,
        request: ByteArray
    ): Either<NetworkFailure, AcmeResponse> =
        wrapApiRequest {
            e2EIApi.getNewAccount(requestUrl, request)
        }

    override suspend fun getNewOrder(
        requestUrl: String,
        request: ByteArray
    ): Either<NetworkFailure, AcmeResponse> =
        wrapApiRequest {
            e2EIApi.getNewOrder(requestUrl, request)
        }
}
