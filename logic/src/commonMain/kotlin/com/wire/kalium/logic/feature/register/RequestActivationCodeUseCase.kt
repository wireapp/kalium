package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isBlackListedEmail
import com.wire.kalium.network.exceptions.isDomainBlockedForRegistration
import com.wire.kalium.network.exceptions.isInvalidEmail
import com.wire.kalium.network.exceptions.isKeyExists

class RequestActivationCodeUseCase internal constructor(
    private val registerAccountRepository: RegisterAccountRepository
) {
    suspend operator fun invoke(email: String): RequestActivationCodeResult {

        return registerAccountRepository.requestEmailActivationCode(email)
            .fold({
                if(it is NetworkFailure.ServerMiscommunication && it.kaliumException is KaliumException.InvalidRequestError)
                    when {
                        it.kaliumException.isInvalidEmail() -> RequestActivationCodeResult.Failure.InvalidEmail
                        it.kaliumException.isBlackListedEmail() -> RequestActivationCodeResult.Failure.BlacklistedEmail
                        it.kaliumException.isKeyExists() -> RequestActivationCodeResult.Failure.AlreadyInUse
                        it.kaliumException.isDomainBlockedForRegistration() ->
                            RequestActivationCodeResult.Failure.DomainBlocked
                        else -> RequestActivationCodeResult.Failure.Generic(it)
                    }
                else RequestActivationCodeResult.Failure.Generic(it)
            }, {
                RequestActivationCodeResult.Success
            })
    }
}

sealed class RequestActivationCodeResult {
    object Success : RequestActivationCodeResult()
    sealed class Failure : RequestActivationCodeResult() {
        object InvalidEmail : Failure()
        object BlacklistedEmail : Failure()
        object AlreadyInUse : Failure()
        object DomainBlocked : Failure()
        class Generic(val failure: NetworkFailure) : Failure()
    }
}
