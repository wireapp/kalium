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
package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.swift.AcmeDirectoryWrapper
import com.wire.kalium.cryptography.swift.E2eiEnrollmentWrapper
import com.wire.kalium.cryptography.swift.NewAcmeAuthzWrapper
import com.wire.kalium.cryptography.swift.NewAcmeOrderWrapper
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class)
class E2EIClientImpl(
    internal val enrollmentWrapper: E2eiEnrollmentWrapper
) : E2EIClient {

    private val defaultDPoPTokenExpiry: UInt = 30U

    override suspend fun directoryResponse(directory: JsonRawData): AcmeDirectory =
        suspendCoroutine { continuation ->
            enrollmentWrapper.directoryResponse(directory.toNSData()) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toAcmeDirectory())
                }
            }
        }

    override suspend fun getNewAccountRequest(previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newAccountRequest(previousNonce) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun setAccountResponse(account: JsonRawData) =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newAccountResponse(account.toNSData()) { error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun getNewOrderRequest(previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newOrderRequest(previousNonce) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun setOrderResponse(order: JsonRawData): NewAcmeOrder =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newOrderResponse(order.toNSData()) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toNewAcmeOrder())
                }
            }
        }

    override suspend fun getNewAuthzRequest(url: String, previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newAuthzRequestWithUrl(url = url, previousNonce = previousNonce) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun setAuthzResponse(authz: JsonRawData): NewAcmeAuthz =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newAuthzResponse(authz.toNSData()) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toNewAcmeAuthz())
                }
            }
        }

    override suspend fun createDpopToken(backendNonce: String): DpopToken =
        suspendCoroutine { continuation ->
            enrollmentWrapper.createDpopTokenWithExpirySecs(
                expirySecs = defaultDPoPTokenExpiry,
                backendNonce = backendNonce
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!)
                }
            }
        }

    override suspend fun getNewDpopChallengeRequest(accessToken: String, previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newDpopChallengeRequestWithAccessToken(
                accessToken = accessToken,
                previousNonce = previousNonce
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun getNewOidcChallengeRequest(idToken: String, previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newOidcChallengeRequestWithIdToken(
                idToken = idToken,
                previousNonce = previousNonce
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun setOIDCChallengeResponse(coreCrypto: CoreCryptoCentral, challenge: JsonRawData) =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newOidcChallengeResponse(challenge.toNSData()) { error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun setDPoPChallengeResponse(challenge: JsonRawData) =
        suspendCoroutine { continuation ->
            enrollmentWrapper.newDpopChallengeResponse(challenge.toNSData()) { error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(Unit)
                }
            }
        }

    override suspend fun checkOrderRequest(orderUrl: String, previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.checkOrderRequestWithOrderUrl(
                orderUrl = orderUrl,
                previousNonce = previousNonce
            ) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun checkOrderResponse(order: JsonRawData): String =
        suspendCoroutine { continuation ->
            enrollmentWrapper.checkOrderResponse(order.toNSData()) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!)
                }
            }
        }

    override suspend fun finalizeRequest(previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.finalizeRequest(previousNonce) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    override suspend fun finalizeResponse(finalize: JsonRawData): String =
        suspendCoroutine { continuation ->
            enrollmentWrapper.finalizeResponse(finalize.toNSData()) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!)
                }
            }
        }

    override suspend fun certificateRequest(previousNonce: String): JsonRawData =
        suspendCoroutine { continuation ->
            enrollmentWrapper.certificateRequest(previousNonce) { result, error ->
                if (error != null) {
                    continuation.resumeWithException(error.toKotlinException())
                } else {
                    continuation.resume(result!!.toByteArray())
                }
            }
        }

    companion object {
        @OptIn(ExperimentalForeignApi::class)
        fun AcmeDirectoryWrapper.toAcmeDirectory() = AcmeDirectory(
            newNonce = this.newNonce(),
            newAccount = this.newAccount(),
            newOrder = this.newOrder()
        )

        @OptIn(ExperimentalForeignApi::class)
        fun NewAcmeOrderWrapper.toNewAcmeOrder() = NewAcmeOrder(
            delegate = this.delegate().toByteArray(),
            authorizations = this.authorizations().map { it as String }
        )

        @OptIn(ExperimentalForeignApi::class)
        fun NewAcmeAuthzWrapper.toNewAcmeAuthz() = NewAcmeAuthz(
            identifier = this.identifier(),
            keyAuth = this.keyauth(),
            challenge = AcmeChallenge(
                delegate = this.challenge().delegate().toByteArray(),
                url = this.challenge().url(),
                target = this.challenge().target()
            )
        )
    }
}
