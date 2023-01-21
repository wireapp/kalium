package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isInvalidCode

/**
 * Use case to validate an activation code for a given email address.
 */
class VerifyActivationCodeUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository
) {
    /**
     * @param email [String] the registered email address that the activation code was sent to
     * @param code [String] the activation code to validate
     * @return [VerifyActivationCodeResult.Success] or [VerifyActivationCodeResult.Failure] with the specific error.
     */
    suspend operator fun invoke(email: String, code: String): VerifyActivationCodeResult =
        registerAccountRepository.verifyActivationCode(email, code)
            .fold({
                if (
                    it is NetworkFailure.ServerMiscommunication &&
                    it.kaliumException is KaliumException.InvalidRequestError &&
                    it.kaliumException.isInvalidCode()
                ) {
                    VerifyActivationCodeResult.Failure.InvalidCode
                } else {
                    VerifyActivationCodeResult.Failure.Generic(it)
                }
            }, {
                VerifyActivationCodeResult.Success
            })

}

sealed class VerifyActivationCodeResult {
    object Success : VerifyActivationCodeResult()
    sealed class Failure : VerifyActivationCodeResult() {
        object InvalidCode : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
