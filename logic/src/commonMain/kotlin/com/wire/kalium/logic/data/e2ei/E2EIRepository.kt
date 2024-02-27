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
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.E2EIClientProvider
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrFail
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapE2EIRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.e2ei.AccessTokenResponse
import com.wire.kalium.network.api.base.authenticated.e2ei.E2EIApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEApi
import com.wire.kalium.network.api.base.unbound.acme.ACMEResponse
import com.wire.kalium.network.api.base.unbound.acme.ChallengeResponse
import io.ktor.http.Url
import io.ktor.http.protocolWithAuthority
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface E2EIRepository {
    suspend fun initFreshE2EIClient(clientId: ClientId? = null, isNewClient: Boolean = false): Either<CoreFailure, Unit>
    suspend fun fetchAndSetTrustAnchors(): Either<CoreFailure, Unit>
    suspend fun getWireAccessToken(wireNonce: String): Either<CoreFailure, AccessTokenResponse>
    suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory>
    suspend fun getACMENonce(endpoint: String): Either<CoreFailure, Nonce>
    suspend fun createNewAccount(prevNonce: Nonce, createAccountEndpoint: String): Either<CoreFailure, Nonce>
    suspend fun createNewOrder(prevNonce: Nonce, createOrderEndpoint: String): Either<CoreFailure, Triple<NewAcmeOrder, Nonce, String>>
    suspend fun createAuthorization(prevNonce: Nonce, endpoint: String): Either<CoreFailure, AcmeAuthorization>

    suspend fun getAuthorizations(
        prevNonce: Nonce,
        authorizationsEndpoints: List<String>
    ): Either<CoreFailure, AuthorizationResult>

    suspend fun getWireNonce(): Either<CoreFailure, Nonce>
    suspend fun getDPoPToken(wireNonce: Nonce): Either<CoreFailure, String>
    suspend fun validateDPoPChallenge(
        accessToken: String,
        prevNonce: Nonce,
        acmeChallenge: AcmeChallenge
    ): Either<CoreFailure, ChallengeResponse>

    suspend fun validateOIDCChallenge(
        idToken: String,
        refreshToken: String,
        prevNonce: Nonce,
        acmeChallenge: AcmeChallenge
    ): Either<CoreFailure, ChallengeResponse>

    suspend fun setDPoPChallengeResponse(challengeResponse: ChallengeResponse): Either<CoreFailure, Unit>
    suspend fun setOIDCChallengeResponse(challengeResponse: ChallengeResponse): Either<CoreFailure, Unit>
    suspend fun finalize(location: String, prevNonce: Nonce): Either<CoreFailure, Pair<ACMEResponse, String>>
    suspend fun checkOrderRequest(location: String, prevNonce: Nonce): Either<CoreFailure, Pair<ACMEResponse, String>>
    suspend fun certificateRequest(location: String, prevNonce: Nonce): Either<CoreFailure, ACMEResponse>
    suspend fun rotateKeysAndMigrateConversations(certificateChain: String, isNewClient: Boolean = false): Either<CoreFailure, Unit>
    suspend fun getOAuthRefreshToken(): Either<CoreFailure, String?>
    suspend fun nukeE2EIClient()
    suspend fun fetchFederationCertificates(): Either<CoreFailure, Unit>
    suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String>
    suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray>
    fun discoveryUrl(): Either<CoreFailure, String>
}

@Suppress("LongParameterList")
class E2EIRepositoryImpl(
    private val e2EIApi: E2EIApi,
    private val acmeApi: ACMEApi,
    private val e2EIClientProvider: E2EIClientProvider,
    private val mlsClientProvider: MLSClientProvider,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val mlsConversationRepository: MLSConversationRepository,
    private val userConfigRepository: UserConfigRepository,
    private val acmeMapper: AcmeMapper = MapperProvider.acmeMapper()
) : E2EIRepository {

    override suspend fun initFreshE2EIClient(clientId: ClientId?, isNewClient: Boolean): Either<CoreFailure, Unit> {
        nukeE2EIClient()
        return e2EIClientProvider.getE2EIClient(clientId, isNewClient).fold({
            kaliumLogger.w("E2EI client initialization failed: $it")
            Either.Left(it)
        }, {
            kaliumLogger.w("E2EI client initialized for enrollment")
            Either.Right(Unit)
        })
    }

    override suspend fun fetchAndSetTrustAnchors(): Either<CoreFailure, Unit> = discoveryUrl().flatMap {
        wrapApiRequest {
            acmeApi.getTrustAnchors(Url(it).protocolWithAuthority)
        }.flatMap { trustAnchors ->
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapE2EIRequest {
                    mlsClient.registerTrustAnchors(trustAnchors.decodeToString())
                }
            }
        }
    }

    override suspend fun loadACMEDirectories(): Either<CoreFailure, AcmeDirectory> = discoveryUrl().flatMap {
        wrapApiRequest {
            acmeApi.getACMEDirectories(it)
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
    }.map { Nonce(it) }

    override suspend fun createNewAccount(prevNonce: Nonce, createAccountEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val accountRequest = e2eiClient.getNewAccountRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(createAccountEndpoint, accountRequest)
            }.map { apiResponse ->
                e2eiClient.setAccountResponse(apiResponse.response)
                Nonce(apiResponse.nonce)
            }
        }

    override suspend fun createNewOrder(prevNonce: Nonce, createOrderEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val orderRequest = e2eiClient.getNewOrderRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(createOrderEndpoint, orderRequest)
            }.flatMap { apiResponse ->
                val orderResponse = e2eiClient.setOrderResponse(apiResponse.response)
                Either.Right(Triple(orderResponse, Nonce(apiResponse.nonce), apiResponse.location))
            }
        }

    override suspend fun createAuthorization(prevNonce: Nonce, endpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val request = e2eiClient.getNewAuthzRequest(endpoint, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendAuthorizationRequest(endpoint, request)
            }.flatMap { apiResponse ->
                val response = e2eiClient.setAuthzResponse(apiResponse.response)
                Either.Right(acmeMapper.fromDto(apiResponse, response))
            }
        }

    @Suppress("ReturnCount")
    override suspend fun getAuthorizations(
        prevNonce: Nonce,
        authorizationsEndpoints: List<String>
    ): Either<CoreFailure, AuthorizationResult> {
        var nonce = prevNonce
        val challenges = mutableMapOf<AuthorizationChallengeType, NewAcmeAuthz>()
        var oidcAuthorization: NewAcmeAuthz? = null
        var dpopAuthorization: NewAcmeAuthz? = null

        authorizationsEndpoints.forEach { endPoint ->
            val authorizationResponse = createAuthorization(nonce, endPoint).getOrFail {
                return Either.Left(CoreFailure.Unknown(Throwable("Failed to get required authorizations from ACME")))
            }
            nonce = authorizationResponse.nonce
            challenges[authorizationResponse.challengeType] = authorizationResponse.newAcmeAuthz
        }

        oidcAuthorization = challenges[AuthorizationChallengeType.OIDC]
        dpopAuthorization = challenges[AuthorizationChallengeType.DPoP]

        if (oidcAuthorization == null || dpopAuthorization == null)
            return Either.Left(CoreFailure.Unknown(Throwable("Missing ACME Challenges")))

        return Either.Right(AuthorizationResult(oidcAuthorization, dpopAuthorization, nonce))
    }

    override suspend fun getWireNonce() = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getWireNonce(clientId.value)
        }.flatMap {
            Either.Right(Nonce(it))
        }
    }

    override suspend fun getWireAccessToken(dpopToken: String) = currentClientIdProvider().flatMap { clientId ->
        wrapApiRequest {
            e2EIApi.getAccessToken(clientId.value, dpopToken)
        }
    }

    override suspend fun getDPoPToken(wireNonce: Nonce) = e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
        Either.Right(e2eiClient.createDpopToken(wireNonce.value))
    }

    override suspend fun validateDPoPChallenge(accessToken: String, prevNonce: Nonce, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewDpopChallengeRequest(accessToken, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendChallengeRequest(acmeChallenge.url, challengeRequest)
            }.map { apiResponse ->
                setDPoPChallengeResponse(apiResponse)
                apiResponse
            }
        }

    override suspend fun validateOIDCChallenge(idToken: String, refreshToken: String, prevNonce: Nonce, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewOidcChallengeRequest(idToken, refreshToken, prevNonce.value)
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
                wrapE2EIRequest {
                    e2eiClient.setOIDCChallengeResponse(coreCrypto, Json.encodeToString(challengeResponse).encodeToByteArray())
                }
            }
        }

    override suspend fun checkOrderRequest(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val checkOrderRequest = e2eiClient.checkOrderRequest(location, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, checkOrderRequest)
            }.map { apiResponse ->
                val finalizeOrderUrl = e2eiClient.checkOrderResponse(apiResponse.response)
                Pair(apiResponse, finalizeOrderUrl)
            }
        }

    override suspend fun finalize(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val finalizeRequest = e2eiClient.finalizeRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, finalizeRequest)
            }.map { apiResponse ->
                val certificateChain = e2eiClient.finalizeResponse(apiResponse.response)
                Pair(apiResponse, certificateChain)
            }
        }

    override suspend fun certificateRequest(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val certificateRequest = e2eiClient.certificateRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, certificateRequest)
            }.map { it }
        }

    override suspend fun rotateKeysAndMigrateConversations(certificateChain: String, isNewClient: Boolean) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            currentClientIdProvider().flatMap { clientId ->
                mlsConversationRepository.rotateKeysAndMigrateConversations(clientId, e2eiClient, certificateChain, isNewClient)
            }
        }

    override suspend fun getOAuthRefreshToken() = e2EIClientProvider.getE2EIClient().flatMap { e2EIClient ->
        Either.Right(e2EIClient.getOAuthRefreshToken())
    }

<<<<<<< HEAD
    override suspend fun fetchFederationCertificates(): Either<CoreFailure, Unit> = discoveryUrl()
        .flatMap {
            wrapApiRequest {
                acmeApi.getACMEFederation(Url(it).protocolWithAuthority)
            }.flatMap { data ->
                mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                    wrapMLSRequest {
                        mlsClient.registerIntermediateCa(data)
                    }
                }
            }
=======
    override suspend fun fetchFederationCertificates() =
        discoveryUrl().flatMap {
            wrapApiRequest {
                acmeApi.getACMEFederation(it)
            }.fold({
                E2EIFailure.IntermediateCert(it).left()
            }, { data ->
                mlsClientProvider.getMLSClient().fold({
                    E2EIFailure.MissingMLSClient(it).left()
                }, { mlsClient ->
                    wrapE2EIRequest {
                        mlsClient.registerIntermediateCa(data)
                    }
                })
            })
>>>>>>> 4d9c0d1843 (fix: wrap registerCrl and registerIntermediateCa in try catch and wrapE2EIRequest (#2549))
        }

    override fun discoveryUrl(): Either<CoreFailure, String> =
        userConfigRepository.getE2EISettings().flatMap { settings ->
            when {
                !settings.isRequired -> E2EIFailure.Disabled.left()
                settings.discoverUrl == null -> E2EIFailure.MissingDiscoveryUrl.left()
                else -> Either.Right(settings.discoverUrl)
            }
        }

    override suspend fun nukeE2EIClient() {
        e2EIClientProvider.nuke()
    }

    override suspend fun getCurrentClientCrlUrl(): Either<CoreFailure, String> =
        discoveryUrl().map { Url(it).protocolWithAuthority }

    override suspend fun getClientDomainCRL(url: String): Either<CoreFailure, ByteArray> =
        wrapApiRequest {
            acmeApi.getClientDomainCRL(url)
        }
}
