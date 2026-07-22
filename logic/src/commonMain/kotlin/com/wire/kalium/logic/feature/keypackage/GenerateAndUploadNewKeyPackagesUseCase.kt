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
package com.wire.kalium.logic.feature.keypackage

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier

/**
 * Generates [count] new MLS key packages and uploads them to the backend.
 *
 * Unlike [RefillKeyPackagesUseCase], the amount is caller-supplied rather than threshold-driven.
 */
internal interface GenerateAndUploadNewKeyPackagesUseCase {
    suspend operator fun invoke(mlsContext: MlsCoreCryptoContext, count: Int): Either<CoreFailure, Unit>
}

internal class GenerateAndUploadNewKeyPackagesUseCaseImpl(
    private val keyPackageRepository: KeyPackageRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val cryptoStateChangeHookNotifier: CryptoStateChangeHookNotifier,
) : GenerateAndUploadNewKeyPackagesUseCase {

    override suspend operator fun invoke(mlsContext: MlsCoreCryptoContext, count: Int): Either<CoreFailure, Unit> {
        if (count <= 0) return Either.Right(Unit)

        return currentClientIdProvider().flatMap { selfClientId ->
            keyPackageRepository.uploadNewKeyPackages(mlsContext, selfClientId, count)
        }.onSuccess {
            cryptoStateChangeHookNotifier.onCryptoStateChanged(selfUserId)
        }
    }
}
