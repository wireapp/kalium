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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.e2ei.E2EIRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.delay


interface EnrolE2EIUseCase {
    suspend operator fun invoke(idToken: String): Either<CoreFailure, E2EIEnrolmentResult>
}

class EnrolE2EIUseCaseImpl internal constructor(
    private val e2EIRepository: E2EIRepository,
) : EnrolE2EIUseCase {
    override suspend fun invoke(idToken: String): Either<CoreFailure, E2EIEnrolmentResult> {
        var step: E2EIEnrolmentResult = E2EIEnrolmentResult.NotStarted
        kaliumLogger.e("ACME Enrolment State:>\n $step")

        var prevNonce = ""

        val acmeDirectories = e2EIRepository.loadACMEDirectories().fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeDirectories, it).toCoreFailure()
            )
        }, { directories ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.AcmeDirectories, directories.toString())
            kaliumLogger.e("Directories:>\n $directories")
            directories
        })

        prevNonce = e2EIRepository.getACMENonce(acmeDirectories.newNonce).fold({
            return Either.Left(E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNonce, it).toCoreFailure())
        }, { acmeNonce ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.AcmeNonce, acmeNonce.toString())
            kaliumLogger.e("ACMENonce:>\n $acmeNonce")
            acmeNonce
        })

        prevNonce = e2EIRepository.createNewAccount(prevNonce, acmeDirectories.newAccount).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewAccount, it).toCoreFailure()
            )
        }, { createNewAccountNonce ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.AcmeNewAccount, createNewAccountNonce)
            kaliumLogger.e("ACMENewAccount Nonce:>\n $createNewAccountNonce")
            createNewAccountNonce
        })

        val newOrderResponse = e2EIRepository.createNewOrder(prevNonce, acmeDirectories.newOrder).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewOrder, it).toCoreFailure()
            )
        }, { newOrderResponse ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.AcmeNewOrder, newOrderResponse.toString())
            kaliumLogger.e("NewOrderResponse:>\n $newOrderResponse")
            newOrderResponse
        })

        prevNonce = newOrderResponse.second

        val authzResponse = e2EIRepository.createAuthz(prevNonce, newOrderResponse.first.authorizations[0]).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewAuthz, it).toCoreFailure()
            )
        }, { authzResponse ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.AcmeNewAuthz, authzResponse.toString())
            kaliumLogger.e("NewAuthz:>\n $authzResponse")
            authzResponse
        })

        prevNonce = authzResponse.second

        val wireNonce = e2EIRepository.getWireNonce().fold({
            return Either.Left(E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.WireNonce, it).toCoreFailure())
        }, { wireNonce ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.WireNonce, wireNonce)
            kaliumLogger.e("WireNonce:>\n $wireNonce")
            wireNonce
        })

        //todo: Mojtaba: remove later after the backend fixed it
        delay(3000)
        val dpopToken = e2EIRepository.getDPoPToken(wireNonce).fold({
            return Either.Left(E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.DPoPToken, it).toCoreFailure())
        }, { dpopToken ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.DPoPToken, dpopToken)
            kaliumLogger.e("DPoPToken:>\n $dpopToken")
            dpopToken
        })

        val wireAccessToken = e2EIRepository.getWireAccessToken(dpopToken).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.WireAccessToken, it).toCoreFailure()
            )
        }, { accessToken ->
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.WireAccessToken, accessToken.toString())
            kaliumLogger.e("AccessToken:>\n $accessToken")
            accessToken
        })

        val dpopChallengeResponse = e2EIRepository.validateDPoPChallenge(
            wireAccessToken.token,
            prevNonce,
            authzResponse.first.wireDpopChallenge!!
        ).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.DPoPChallenge, it).toCoreFailure()
            )
        }, {
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.DPoPChallenge, it.toString())
            kaliumLogger.e("DPoPChallenge:> Passed")
            it
        })
        prevNonce = dpopChallengeResponse.nonce

        val oidcChallengeResponse = e2EIRepository.validateOIDCChallenge(
            idToken,
            prevNonce,
            authzResponse.first.wireOidcChallenge!!
        ).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.OIDCChallenge, it).toCoreFailure()
            )
        }, {
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.OIDCChallenge, it.toString())
            kaliumLogger.e("OIDCChallenge:> Passed")
            it
        })
        prevNonce = oidcChallengeResponse.nonce

        val orderResponse = e2EIRepository.checkOrderRequest(newOrderResponse.third, prevNonce).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.CheckOrderRequest, it).toCoreFailure()
            )
        }, {
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.CheckOrderRequest, it.toString())
            kaliumLogger.e("CheckOrderRequest:> $it")
            it
        })

        prevNonce = orderResponse.first.nonce

        // todo: replace with orderResponse.third
        val finalizeResponse = e2EIRepository.finalize(orderResponse.second, prevNonce).fold({
            return Either.Left(
                E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.FinalizeRequest, it).toCoreFailure()
            )
        }, {
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.FinalizeRequest, it.toString())
            kaliumLogger.e("FinalizeRequest:> ${it.first.response}")
            it
        })

        prevNonce = finalizeResponse.first.nonce

        val certificateRequest = e2EIRepository.certificateRequest(finalizeResponse.second, prevNonce).fold({
            return Either.Left(E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.Certificate, it).toCoreFailure())
        }, {
            step = E2EIEnrolmentResult.Success(E2EIEnrolmentResult.E2EIStep.Certificate, it.response.decodeToString())
            kaliumLogger.e("Certificate:> ${it.response.decodeToString()}")
            it
        })

        e2EIRepository.initMLSClientWithCertificate(certificateRequest.response.decodeToString())

        return Either.Right(step)
    }

}

sealed interface E2EIEnrolmentResult {
    enum class E2EIStep {
        AcmeNonce,
        AcmeDirectories,
        AcmeNewAccount,
        AcmeNewOrder,
        AcmeNewAuthz,
        WireNonce,
        DPoPToken,
        WireAccessToken,
        DPoPChallenge,
        OIDCChallenge,
        CheckOrderRequest,
        FinalizeRequest,
        Certificate
    }

    object NotStarted : E2EIEnrolmentResult {
        override fun toString(): String {
            return "E2EI enrolment not started"
        }
    }

    class Success(val step: E2EIStep, val stepDetails: String) : E2EIEnrolmentResult {
        override fun toString(): String {
            return "E2EI enrolment passed to the $step:$stepDetails"
        }
    }

    data class Failed(val step: E2EIStep, val failure: CoreFailure) : E2EIEnrolmentResult {
        override fun toString(): String {
            return "E2EI enrolment passed to the $step:${failure}"
        }

        fun toCoreFailure() = E2EIFailure(Exception(this.toString()))
    }

}
