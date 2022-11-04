package com.wire.kalium.logic.network

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.session.UpgradeCurrentSessionUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map

class ApiMigrationV3(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val upgradeCurrentSession: UpgradeCurrentSessionUseCase,
) : ApiMigration {
    override suspend operator fun invoke(): Either<CoreFailure, Unit> =
        currentClientIdProvider().flatMap {
            upgradeCurrentSession(it)
        }.flatMapLeft {
            if (it is StorageFailure.DataNotFound) {
                Either.Right(Unit)
            } else {
                Either.Left(it)
            }
        }
}
