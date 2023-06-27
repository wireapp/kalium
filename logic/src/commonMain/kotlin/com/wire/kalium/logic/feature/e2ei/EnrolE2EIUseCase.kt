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

interface EnrolE2EIUseCase {
    suspend operator fun invoke(idToken: String): Either<CoreFailure, E2EIEnrolmentResult>
}

@Suppress("ReturnCount")
class EnrolE2EIUseCaseImpl internal constructor(
    private val e2EIRepository: E2EIRepository,
) : EnrolE2EIUseCase {
    override suspend fun invoke(idToken: String): Either<CoreFailure, E2EIEnrolmentResult> {

        val acmeDirectories = e2EIRepository.loadACMEDirectories().fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeDirectories, it).toEitherLeft()
        }, { it })

        var prevNonce = e2EIRepository.getACMENonce(acmeDirectories.newNonce).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNonce, it).toEitherLeft()
        }, { it })

        prevNonce = e2EIRepository.createNewAccount(prevNonce, acmeDirectories.newAccount).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewAccount, it).toEitherLeft()
        }, { it })

        val newOrderResponse = e2EIRepository.createNewOrder(prevNonce, acmeDirectories.newOrder).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewOrder, it).toEitherLeft()
        }, { it })

        prevNonce = newOrderResponse.second

        val authzResponse = e2EIRepository.createAuthz(prevNonce, newOrderResponse.first.authorizations[0]).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.AcmeNewAuthz, it).toEitherLeft()
        }, { it })

        prevNonce = authzResponse.second

        val wireNonce = e2EIRepository.getWireNonce().fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.WireNonce, it).toEitherLeft()
        }, { it })

        val dpopToken = e2EIRepository.getDPoPToken(wireNonce).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.DPoPToken, it).toEitherLeft()
        }, { it })

        val wireAccessToken = e2EIRepository.getWireAccessToken(dpopToken).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.WireAccessToken, it).toEitherLeft()
        }, { it })

        val dpopChallengeResponse = e2EIRepository.validateDPoPChallenge(
            wireAccessToken.token, prevNonce, authzResponse.first.wireDpopChallenge!!
        ).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.DPoPChallenge, it).toEitherLeft()
        }, { it })

        prevNonce = dpopChallengeResponse.nonce

        val oidcChallengeResponse = e2EIRepository.validateOIDCChallenge(
            idToken, prevNonce, authzResponse.first.wireOidcChallenge!!
        ).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.OIDCChallenge, it).toEitherLeft()
        }, { it })

        prevNonce = oidcChallengeResponse.nonce

        val orderResponse = e2EIRepository.checkOrderRequest(newOrderResponse.third, prevNonce).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.CheckOrderRequest, it).toEitherLeft()
        }, { it })

        prevNonce = orderResponse.first.nonce

        // todo: replace with orderResponse.third
        val finalizeResponse = e2EIRepository.finalize(orderResponse.second, prevNonce).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.FinalizeRequest, it).toEitherLeft()
        }, { it })

        prevNonce = finalizeResponse.first.nonce

        val certificateRequest = e2EIRepository.certificateRequest(finalizeResponse.second, prevNonce).fold({
            return E2EIEnrolmentResult.Failed(E2EIEnrolmentResult.E2EIStep.Certificate, it).toEitherLeft()
        }, { it })

        //todo: init after fixing the MLS client initialization mechanism
        //e2EIRepository.initMLSClientWithCertificate(certificateRequest.response.decodeToString())
        return Either.Right(E2EIEnrolmentResult.Success(certificateRequest.response.decodeToString()))
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

    class Success(val certificate: String) : E2EIEnrolmentResult

    data class Failed(val step: E2EIStep, val failure: CoreFailure) : E2EIEnrolmentResult {
        override fun toString(): String {
            return "E2EI enrolment failed at $step: with $failure"
        }

        fun toEitherLeft() = Either.Left(E2EIFailure(Exception(this.toString())))
    }
}
