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
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapE2EIRequest
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
    suspend fun initFreshE2EIClient(clientId: ClientId? = null, isNewClient: Boolean = false): Either<E2EIFailure, Unit>
    suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit>
    suspend fun loadACMEDirectories(): Either<E2EIFailure, AcmeDirectory>
    suspend fun getACMENonce(endpoint: String): Either<E2EIFailure, Nonce>
    suspend fun createNewAccount(prevNonce: Nonce, createAccountEndpoint: String): Either<E2EIFailure, Nonce>
    suspend fun createNewOrder(prevNonce: Nonce, createOrderEndpoint: String): Either<E2EIFailure, Triple<NewAcmeOrder, Nonce, String>>
    suspend fun createAuthorization(prevNonce: Nonce, endpoint: String): Either<E2EIFailure, AcmeAuthorization>

    suspend fun getAuthorizations(
        prevNonce: Nonce,
        authorizationsEndpoints: List<String>
    ): Either<E2EIFailure, AuthorizationResult>

    suspend fun getWireNonce(): Either<E2EIFailure, Nonce>
    suspend fun getWireAccessToken(dpopToken: String): Either<E2EIFailure, AccessTokenResponse>
    suspend fun getDPoPToken(wireNonce: Nonce): Either<E2EIFailure, String>
    suspend fun validateDPoPChallenge(
        accessToken: String,
        prevNonce: Nonce,
        acmeChallenge: AcmeChallenge
    ): Either<E2EIFailure, ChallengeResponse>

    suspend fun validateOIDCChallenge(
        idToken: String,
        refreshToken: String,
        prevNonce: Nonce,
        acmeChallenge: AcmeChallenge
    ): Either<E2EIFailure, ChallengeResponse>

    suspend fun setDPoPChallengeResponse(challengeResponse: ChallengeResponse): Either<E2EIFailure, Unit>
    suspend fun setOIDCChallengeResponse(challengeResponse: ChallengeResponse): Either<E2EIFailure, Unit>
    suspend fun finalize(location: String, prevNonce: Nonce): Either<E2EIFailure, Pair<ACMEResponse, String>>
    suspend fun checkOrderRequest(location: String, prevNonce: Nonce): Either<E2EIFailure, Pair<ACMEResponse, String>>
    suspend fun certificateRequest(location: String, prevNonce: Nonce): Either<E2EIFailure, ACMEResponse>
    suspend fun rotateKeysAndMigrateConversations(certificateChain: String, isNewClient: Boolean = false): Either<E2EIFailure, Unit>
    suspend fun getOAuthRefreshToken(): Either<E2EIFailure, String?>
    suspend fun nukeE2EIClient()
    suspend fun fetchFederationCertificates(): Either<E2EIFailure, Unit>
    suspend fun getCurrentClientCrlUrl(): Either<E2EIFailure, String>
    suspend fun getClientDomainCRL(url: String): Either<E2EIFailure, ByteArray>
    fun discoveryUrl(): Either<E2EIFailure, Url>
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

    override suspend fun initFreshE2EIClient(clientId: ClientId?, isNewClient: Boolean): Either<E2EIFailure, Unit> {
        nukeE2EIClient()
        return e2EIClientProvider.getE2EIClient(clientId, isNewClient).fold({ it.left() }, { Unit.right() })
    }

<<<<<<< HEAD
    override suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit> = discoveryUrl().flatMap {
        // todo: fetch only once!
        wrapApiRequest {
            acmeApi.getTrustAnchors(it)
        }.fold({
            E2EIFailure.TrustAnchors(it).left()
        }, { trustAnchors ->
            mlsClientProvider.getMLSClient().fold({
                E2EIFailure.MissingMLSClient(it).left()
            }, { mlsClient ->
                wrapE2EIRequest {
                    mlsClient.registerTrustAnchors(trustAnchors.decodeToString())
                }
=======
    override suspend fun fetchAndSetTrustAnchors(): Either<E2EIFailure, Unit> = if (userConfigRepository.getShouldFetchE2EITrustAnchor()) {
        discoveryUrl().flatMap {
            wrapApiRequest {
                acmeApi.getTrustAnchors(it)
            }.fold({
                E2EIFailure.TrustAnchors(it).left()
            }, { trustAnchors ->
                currentClientIdProvider().fold({
                    E2EIFailure.TrustAnchors(it).left()
                }, { clientId ->
                    mlsClientProvider.getCoreCrypto(clientId).fold({
                        E2EIFailure.MissingMLSClient(it).left()
                    }, { coreCrypto ->
                        wrapE2EIRequest {
                            coreCrypto.registerTrustAnchors(trustAnchors.decodeToString())
                        }.onSuccess {
                            userConfigRepository.setShouldFetchE2EITrustAnchors(shouldFetch = false)
                        }
                    })
                })
>>>>>>> e849ba47dd (fix: Fetch GetTrustAnchors only once (WPB-6808) (#2558))
            })
        }
    } else {
        Either.Right(Unit)
    }

    override suspend fun loadACMEDirectories() = discoveryUrl().flatMap {
        wrapApiRequest {
            acmeApi.getACMEDirectories(it)
        }.fold({
            E2EIFailure.AcmeDirectories(it).left()
        }, { directories ->
            e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
                wrapE2EIRequest {
                    e2eiClient.directoryResponse(Json.encodeToString(directories).encodeToByteArray())
                }
            }
        })
    }

    override suspend fun getACMENonce(endpoint: String) = wrapApiRequest {
        acmeApi.getACMENonce(endpoint)
    }.fold({
        E2EIFailure.AcmeNonce(it).left()
    }, {
        Nonce(it).right()
    })

    override suspend fun createNewAccount(prevNonce: Nonce, createAccountEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val accountRequest = e2eiClient.getNewAccountRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(createAccountEndpoint, accountRequest)
            }.fold({
                E2EIFailure.AcmeNewAccount(it).left()
            }, { apiResponse ->
                e2eiClient.setAccountResponse(apiResponse.response)
                Nonce(apiResponse.nonce).right()
            })
        }

    override suspend fun createNewOrder(prevNonce: Nonce, createOrderEndpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val orderRequest = e2eiClient.getNewOrderRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(createOrderEndpoint, orderRequest)
            }.fold({
                E2EIFailure.AcmeNewOrder(it).left()
            }, { apiResponse ->
                wrapE2EIRequest {
                    val orderResponse = e2eiClient.setOrderResponse(apiResponse.response)
                    Triple(orderResponse, Nonce(apiResponse.nonce), apiResponse.location)
                }
            })
        }

    override suspend fun createAuthorization(prevNonce: Nonce, endpoint: String) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val request = e2eiClient.getNewAuthzRequest(endpoint, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendAuthorizationRequest(endpoint, request)
            }.fold({
                E2EIFailure.AcmeNewAccount(it).left()
            }, { apiResponse ->
                val response = e2eiClient.setAuthzResponse(apiResponse.response)
                Either.Right(acmeMapper.fromDto(apiResponse, response))
            })
        }

    @Suppress("ReturnCount")
    override suspend fun getAuthorizations(
        prevNonce: Nonce,
        authorizationsEndpoints: List<String>
    ): Either<E2EIFailure, AuthorizationResult> {
        var nonce = prevNonce
        val challenges = mutableMapOf<AuthorizationChallengeType, NewAcmeAuthz>()

        authorizationsEndpoints.forEach { endPoint ->
            val authorizationResponse = createAuthorization(nonce, endPoint).getOrFail {
                return it.left()
            }
            nonce = authorizationResponse.nonce
            challenges[authorizationResponse.challengeType] = authorizationResponse.newAcmeAuthz
        }

        val oidcAuthorization: NewAcmeAuthz? = challenges[AuthorizationChallengeType.OIDC]
        val dpopAuthorization: NewAcmeAuthz? = challenges[AuthorizationChallengeType.DPoP]

        if (oidcAuthorization == null || dpopAuthorization == null)
            return E2EIFailure.AcmeAuthorizations.left()

        return AuthorizationResult(oidcAuthorization, dpopAuthorization, nonce).right()
    }

    override suspend fun getWireNonce() = currentClientIdProvider().fold({ E2EIFailure.WireNonce(it).left() }, { clientId ->
        wrapApiRequest {
            e2EIApi.getWireNonce(clientId.value)
        }.fold({ E2EIFailure.WireNonce(it).left() }, { Nonce(it).right() })
    })

    override suspend fun getWireAccessToken(dpopToken: String) =
        currentClientIdProvider().fold({ E2EIFailure.WireAccessToken(it).left() }, { clientId ->
        wrapApiRequest {
            e2EIApi.getAccessToken(clientId.value, dpopToken)
        }.fold({ E2EIFailure.WireAccessToken(it).left() }, { it.right() })
        })

    override suspend fun getDPoPToken(wireNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient -> e2eiClient.createDpopToken(wireNonce.value).right() }

    override suspend fun validateDPoPChallenge(accessToken: String, prevNonce: Nonce, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewDpopChallengeRequest(accessToken, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendChallengeRequest(acmeChallenge.url, challengeRequest)
            }.fold({
                E2EIFailure.DPoPChallenge(it).left()
            }, { apiResponse ->
                setDPoPChallengeResponse(apiResponse)
                apiResponse.right()
            })
        }

    override suspend fun validateOIDCChallenge(idToken: String, refreshToken: String, prevNonce: Nonce, acmeChallenge: AcmeChallenge) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val challengeRequest = e2eiClient.getNewOidcChallengeRequest(idToken, refreshToken, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendChallengeRequest(acmeChallenge.url, challengeRequest)
            }.fold({
                E2EIFailure.OIDCChallenge(it).left()
            }, { apiResponse ->
                setOIDCChallengeResponse(apiResponse)
                apiResponse.right()
            })
        }

    override suspend fun setDPoPChallengeResponse(challengeResponse: ChallengeResponse) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            wrapE2EIRequest {
                e2eiClient.setDPoPChallengeResponse(Json.encodeToString(challengeResponse).encodeToByteArray())
            }
        }

    override suspend fun setOIDCChallengeResponse(challengeResponse: ChallengeResponse) = mlsClientProvider.getCoreCrypto().fold({
        E2EIFailure.MissingMLSClient(it).left()
    }, { coreCrypto ->
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            wrapE2EIRequest {
                e2eiClient.setOIDCChallengeResponse(coreCrypto, Json.encodeToString(challengeResponse).encodeToByteArray())
            }
        }
    })

    override suspend fun checkOrderRequest(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val checkOrderRequest = e2eiClient.checkOrderRequest(location, prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, checkOrderRequest)
            }.fold({
                E2EIFailure.CheckOrderRequest(it).left()
            }, { apiResponse ->
                val finalizeOrderUrl = e2eiClient.checkOrderResponse(apiResponse.response)
                Pair(apiResponse, finalizeOrderUrl).right()
            })
        }

    override suspend fun finalize(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val finalizeRequest = e2eiClient.finalizeRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, finalizeRequest)
            }.fold({
                E2EIFailure.FinalizeRequest(it).left()
            }, { apiResponse ->
                val certificateChain = e2eiClient.finalizeResponse(apiResponse.response)
                Pair(apiResponse, certificateChain).right()
            })
        }

    override suspend fun certificateRequest(location: String, prevNonce: Nonce) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            val certificateRequest = e2eiClient.certificateRequest(prevNonce.value)
            wrapApiRequest {
                acmeApi.sendACMERequest(location, certificateRequest)
            }.fold({ E2EIFailure.Certificate(it).left() }, { it.right() })
        }

    override suspend fun rotateKeysAndMigrateConversations(certificateChain: String, isNewClient: Boolean) =
        e2EIClientProvider.getE2EIClient().flatMap { e2eiClient ->
            currentClientIdProvider().fold({
                E2EIFailure.RotationAndMigration(it).left()
            }, { clientId ->
                mlsConversationRepository.rotateKeysAndMigrateConversations(clientId, e2eiClient, certificateChain, isNewClient)
            })
        }

    override suspend fun getOAuthRefreshToken() = e2EIClientProvider.getE2EIClient().flatMap { e2EIClient ->
        e2EIClient.getOAuthRefreshToken().right()
    }

    override suspend fun fetchFederationCertificates() = discoveryUrl().flatMap {
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

        }

    override fun discoveryUrl() =
        userConfigRepository.getE2EISettings().fold({
            E2EIFailure.MissingTeamSettings.left()
        }, { settings ->
            when {
                !settings.isRequired -> E2EIFailure.Disabled.left()
                settings.discoverUrl == null -> E2EIFailure.MissingDiscoveryUrl.left()
                else -> Url(settings.discoverUrl).right()
            }
        })

    override suspend fun nukeE2EIClient() {
        e2EIClientProvider.nuke()
    }

    override suspend fun getCurrentClientCrlUrl(): Either<E2EIFailure, String> =
        discoveryUrl().map { it.protocolWithAuthority }

    override suspend fun getClientDomainCRL(url: String): Either<E2EIFailure, ByteArray> =
        wrapApiRequest {
            acmeApi.getClientDomainCRL(url)
        }.fold({ E2EIFailure.CRL(it).left() }, { it.right() })
}
