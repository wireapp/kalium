package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.team.Result
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isFederationError

interface SyncContactsUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

class SyncContactsUseCaseImpl(private val userDataSource: UserRepository) : SyncContactsUseCase {

    override suspend operator fun invoke(): Either<CoreFailure, Unit> {
        return userDataSource.fetchKnownUsers().fold({ coreFailure ->
            val a = when {
                coreFailure is NetworkFailure.ServerMiscommunication && coreFailure.kaliumException is KaliumException.ServerError
                        && coreFailure.kaliumException.isFederationError() -> kaliumLogger.e("aaaa!")
                // todo: change this to proper failure mapping
                // todo: simplify this check or extract
                else -> Result.Failure.GenericFailure(coreFailure)
            }
            Either.Left(coreFailure)
        }, {
            Either.Right(Unit)
        })
    }

}
