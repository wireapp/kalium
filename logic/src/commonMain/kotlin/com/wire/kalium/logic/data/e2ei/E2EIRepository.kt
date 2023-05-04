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
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeDirectoriesResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AcmeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.AuthzDirectories
import com.wire.kalium.network.api.base.authenticated.e2ei.ChallengeResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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

    suspend fun getAuthzChallenge(
        requestUrl: String
    ): Either<NetworkFailure, AcmeResponse>

    suspend fun getWireNonce(clientId: String): Either<NetworkFailure, String>
    suspend fun getDpopAccessToken(clientId: String, dpopToken: String): Either<NetworkFailure, AccessTokenResponse>
    suspend fun dpopChallenge(requestUrl: String, request: ByteArray): Either<NetworkFailure, ChallengeResponse>
}

class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val mlsClientProvider: MLSClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val getSelfUserUseCase: GetSelfUserUseCase
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
        val selfUser = getSelfUserUseCase().first()
        mlsClientProvider.getE2EIClient(selfUser = selfUser).flatMap { e2eiClient ->


            //<editor-fold desc="Get ACME Directories">
            // fetch directories
            val directories = getACMEDirectories().fold({
                AcmeDirectoriesResponse("", "", "", "", "")
            },
                { it }
            )
            kaliumLogger.w("\nDirectories from API:>\n$directories")

            // Set directories to E2EIClient
            e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
            kaliumLogger.w("\nACME Directories Passed to CC\n")

            //</editor-fold>

            //<editor-fold desc="Get New Nonce">
            // get nonce from nonce url
            var prevNonce = getNewNonce(directories.newNonce).fold({ "" }, { it })
            kaliumLogger.w("\nNewNonce from API:>\n$prevNonce")
            //</editor-fold>

            //<editor-fold desc="Create new account">
            val accountRequest = e2eiClient.newAccountRequest(prevNonce)
            kaliumLogger.w("\nNewAccountRequest from CC:>\n${toLog(accountRequest)}")

            val accountResponse =
                getNewAccount(directories.newAccount, accountRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("\nNewNonce from API:>\n${accountResponse.nonce}")
            kaliumLogger.w("\nNewAccountResponse from API:>\n${toLog(accountResponse.response)}")

            e2eiClient.newAccountResponse(accountResponse.response)
            kaliumLogger.w("\nNewAccountResponse Passed to CC\n")

            //</editor-fold>
            prevNonce = accountResponse.nonce
            //<editor-fold desc="Create New Order">
            val orderRequest = e2eiClient.newOrderRequest(prevNonce)
            kaliumLogger.w("\nNewOrderRequest from CC:>\n${toLog(orderRequest)}")

            val orderResponse =
                getNewOrder(directories.newOrder, orderRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("\nNewNonce from API:>\n${orderResponse.nonce}")
            kaliumLogger.w("\nNewOrderResponse from API:>\n${toLog(orderResponse.response)}")

            prevNonce = orderResponse.nonce

            val order = e2eiClient.newOrderResponse(orderResponse.response)

            // todo: get the location from api response
            kaliumLogger.w(
                "\nOrderResponse from CC :>\n${order.authorizations}" +
                        "\n${toLog(order.delegate)}"
            )
            //</editor-fold>

            //<editor-fold desc="Authz Request">
            val authzRequest =
                e2eiClient.newAuthzRequest(order.authorizations[0], prevNonce)
            kaliumLogger.w("\nNewAuthzRequest from CC:>\n${toLog(authzRequest)}")

            val authzResponse =
                getNewOrder(order.authorizations[0], authzRequest).fold(
                    { AcmeResponse("", byteArrayOf()) },
                    { it })
            kaliumLogger.w("\nNewNonce from API:>\n${authzResponse.nonce}")
            kaliumLogger.w("\nAuthzResp from API:>\n${toLog(authzResponse.response)}")

            prevNonce = authzResponse.nonce

            val authz = e2eiClient.newAuthzResponse(authzResponse.response)
            kaliumLogger.w(
                "\nAuthzResp from CC :>\n${authz.identifier}" +
                        "\n${authz.wireDpopChallenge!!.url}" +
                        "\n${toLog(authz.wireDpopChallenge!!.delegate)}" +
                        "\n${authz.wireOidcChallenge!!.url}" +
                        "\n${toLog(authz.wireOidcChallenge!!.delegate)}"
            )

            //</editor-fold>

            //<editor-fold desc="getAuthzDirectories">
            val authzDirectories =
                getAuthzDirectories().fold(
                    { AuthzDirectories("", "", "", "", "", "") },
                    { it })
            kaliumLogger.w("\nAuthzDirectories from API:>\n$authzDirectories")


            //</editor-fold>
            // Client fetches JWT DPoP access token (with wire-server)
            //<editor-fold desc="DPOP">

            currentClientIdProvider().map { x ->
                kaliumLogger.w("\nclientID :>\n${x.value}")
                val wireNonce = getWireNonce(x.value).fold({ "" }, { it })
                kaliumLogger.w("\nWireNonce:>\n$wireNonce")
                val dpopToken =
                    e2eiClient.createDpopToken("https://staging.zinfra.io/clients/${x.value}/access-token", wireNonce)
                kaliumLogger.w("\nclientDpopToken from CC :>\n$dpopToken")


                delay(3000)
                val dpopAccessToken = getDpopAccessToken(x.value, dpopToken).fold({
                    AccessTokenResponse("", "", "")
                }, {
                    it
                })
                kaliumLogger.w("\nAccessTokenFromWireServer:>\n${dpopAccessToken}")

                // create dpop challenge
                val dpopChallengeRequest =
                    e2eiClient.newDpopChallengeRequest(dpopAccessToken.token, prevNonce)
                kaliumLogger.w("\nDpopChallengeReq from CC:>\n${toLog(dpopChallengeRequest)}")

                // send to the acme server
                val dpopChallengeResponse = dpopChallenge(authz.wireDpopChallenge!!.url, dpopChallengeRequest).fold({
                    throw Exception()
                }, { it })

                kaliumLogger.w("\nDpopChallengeResponse from CC:>\n${dpopChallengeResponse}")

                // e2eiClient.newChallengeResponse(dpopChallengeResponse.token)

                //--oidc-- finish oauth

                // create dpop challenge
//                 val dpopChallengeRequest = e2eiClient.newOidcChallengeRequest(oidc token, prev nonce from acme nonce)
//                 kaliumLogger.w("\nDpopChallengeReq from CC:>\n${toLog(dpopChallengeRequest)}")
//
//                 // send to the acme server
//                 send oidc challenge to wireOidcChallenge from authz
//
//                 response -> supply to
//                 e2eiClient.newChallengeResponse()

                // order check is it valid

                // finalize

                // get the certificate


            }
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

    override suspend fun getWireNonce(clientId: String): Either<NetworkFailure, String> =
        wrapApiRequest {
            e2EIApi.getWireNonce(clientId)
        }

    override suspend fun getDpopAccessToken(clientId: String, dpopToken: String): Either<NetworkFailure, AccessTokenResponse> =
        wrapApiRequest {
            e2EIApi.getDpopAccessToken(clientId, dpopToken)
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

    override suspend fun dpopChallenge(
        requestUrl: String,
        request: ByteArray
    ): Either<NetworkFailure, ChallengeResponse> =
        wrapApiRequest {
            e2EIApi.dpopChallenge(requestUrl, request)
        }

    override suspend fun getAuthzChallenge(
        requestUrl: String
    ): Either<NetworkFailure, AcmeResponse> =
        wrapApiRequest {
            e2EIApi.getAuthzChallenge(requestUrl)
        }
}
